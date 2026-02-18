#include "trading/order_manager.hpp"

#include <algorithm>
#include <iostream>

namespace argentum::trading {

OrderManager::OrderManager(std::shared_ptr<risk::RiskManager> risk, 
                           std::shared_ptr<engine::OrderBook> book)
    : risk_manager_(std::move(risk)), order_book_(std::move(book)) {}

OrderSubmissionResult OrderManager::submit_order(const Order& order) {
    OrderSubmissionResult result{};
    Order normalized = order;
    core::normalize_order_scalars(&normalized);
    result.remaining_quantity = normalized.quantity;

    if (!risk_manager_ || !order_book_) {
        result.reject_reason = OrderRejectReason::InternalError;
        result.status = OrderStatus::Rejected;
        return result;
    }

    if (!is_valid_order(normalized)) {
        result.reject_reason = OrderRejectReason::InvalidOrder;
        result.status = OrderStatus::Rejected;
        return result;
    }

    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (active_orders_.find(normalized.order_id) != active_orders_.end() ||
            order_history_.find(normalized.order_id) != order_history_.end()) {
            result.reject_reason = OrderRejectReason::DuplicateOrderId;
            result.status = OrderStatus::Rejected;
            return result;
        }
    }

    if (!risk_manager_->check_order(normalized)) {
        result.reject_reason = OrderRejectReason::RiskRejected;
        result.status = OrderStatus::Rejected;
        std::cout << "[OMS] Order " << normalized.order_id << " rejected by Risk Manager." << std::endl;
        return result;
    }

    OrderState taker_state{};
    taker_state.order = normalized;
    taker_state.initial_lots = normalized.quantity_lots;
    taker_state.remaining_lots = normalized.quantity_lots;
    taker_state.updated_at_ns = core::unix_now_ns();
    taker_state.status = OrderStatus::New;

    result.trades = order_book_->match_order(normalized);
    for (const Trade& trade : result.trades) {
        Order taker_fill = normalized;
        taker_fill.price = trade.price;
        taker_fill.quantity = trade.quantity;
        taker_fill.price_ticks = trade.price_ticks;
        taker_fill.quantity_lots = trade.quantity_lots;
        risk_manager_->on_fill(taker_fill);
        result.filled_quantity += trade.quantity;
        taker_state.filled_lots += trade.quantity_lots;
        taker_state.remaining_lots = std::max<int64_t>(0, taker_state.remaining_lots - trade.quantity_lots);
        apply_trade_to_maker(trade.maker_order_id, trade);
    }

    result.remaining_quantity = core::from_quantity_lots(taker_state.remaining_lots);
    result.resting = (normalized.type == ORDER_TYPE_LIMIT && taker_state.remaining_lots > 0);

    if (result.resting) {
        Order residual = normalized;
        residual.quantity_lots = taker_state.remaining_lots;
        residual.quantity = core::from_quantity_lots(taker_state.remaining_lots);
        taker_state.order = residual;
        taker_state.status = (taker_state.filled_lots > 0) ? OrderStatus::PartiallyFilled : OrderStatus::Resting;
        taker_state.updated_at_ns = core::unix_now_ns();
        {
            std::lock_guard<std::mutex> lock(mutex_);
            active_orders_[residual.order_id] = taker_state;
        }
        upsert_state(taker_state);
        std::cout << "[OMS] Order " << normalized.order_id << " accepted and placed with remaining "
                  << result.remaining_quantity << "." << std::endl;
    } else {
        if (taker_state.remaining_lots > 0) {
            Order canceled = normalized;
            canceled.quantity_lots = taker_state.remaining_lots;
            canceled.quantity = result.remaining_quantity;
            risk_manager_->on_cancel(canceled);
        }
        taker_state.status = (taker_state.filled_lots > 0) ? OrderStatus::Filled : OrderStatus::Canceled;
        taker_state.remaining_lots = 0;
        taker_state.order.quantity_lots = 0;
        taker_state.order.quantity = 0.0;
        taker_state.updated_at_ns = core::unix_now_ns();
        upsert_state(taker_state);
        std::cout << "[OMS] Order " << normalized.order_id << " fully processed." << std::endl;
    }

    result.accepted = true;
    result.status = result.resting
        ? (taker_state.filled_lots > 0 ? OrderStatus::PartiallyFilled : OrderStatus::Resting)
        : (taker_state.filled_lots > 0 ? OrderStatus::Filled : OrderStatus::Canceled);
    return result;
}

bool OrderManager::cancel_order(uint64_t order_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = active_orders_.find(order_id);
    if (it == active_orders_.end()) return false;

    if (!order_book_->cancel_order(order_id)) {
        return false;
    }

    OrderState state = it->second;
    risk_manager_->on_cancel(state.order);
    state.status = OrderStatus::Canceled;
    state.order.quantity_lots = 0;
    state.order.quantity = 0.0;
    state.remaining_lots = 0;
    state.updated_at_ns = core::unix_now_ns();
    active_orders_.erase(it);
    order_history_[order_id] = state;
    std::cout << "[OMS] Order " << order_id << " canceled." << std::endl;
    return true;
}

bool OrderManager::cancel_order_partial(uint64_t order_id, double quantity) {
    const int64_t reduce_lots = core::to_quantity_lots(quantity);
    if (reduce_lots <= 0) return false;

    std::lock_guard<std::mutex> lock(mutex_);
    auto it = active_orders_.find(order_id);
    if (it == active_orders_.end()) return false;

    Order updated{};
    if (!order_book_->cancel_order_partial(order_id, reduce_lots, &updated)) {
        return false;
    }

    if (updated.quantity_lots <= 0) {
        risk_manager_->on_cancel(it->second.order);
        OrderState state = it->second;
        state.status = OrderStatus::Canceled;
        state.remaining_lots = 0;
        state.order.quantity_lots = 0;
        state.order.quantity = 0.0;
        state.updated_at_ns = core::unix_now_ns();
        active_orders_.erase(it);
        order_history_[order_id] = state;
        return true;
    }

    OrderState& state = it->second;
    const int64_t old_remaining = state.remaining_lots;
    state.order = updated;
    state.remaining_lots = updated.quantity_lots;
    state.filled_lots = state.initial_lots - state.remaining_lots;
    state.status = (state.filled_lots > 0) ? OrderStatus::PartiallyFilled : OrderStatus::Resting;
    state.updated_at_ns = core::unix_now_ns();

    const int64_t released = std::max<int64_t>(0, old_remaining - state.remaining_lots);
    if (released > 0) {
        Order canceled = updated;
        canceled.quantity_lots = released;
        canceled.quantity = core::from_quantity_lots(released);
        risk_manager_->on_cancel(canceled);
    }
    order_history_[order_id] = state;
    return true;
}

bool OrderManager::modify_order(uint64_t order_id, double new_price, double new_quantity) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = active_orders_.find(order_id);
    if (it == active_orders_.end()) return false;

    Order replacement = it->second.order;
    replacement.price = new_price;
    replacement.quantity = new_quantity;
    replacement.price_ticks = core::to_price_ticks(new_price);
    replacement.quantity_lots = core::to_quantity_lots(new_quantity);
    core::normalize_order_scalars(&replacement);

    if (!is_valid_order(replacement)) return false;
    if (!order_book_->modify_order(order_id, replacement)) return false;

    // Rebuild risk reservation using delta between old and new remaining.
    risk_manager_->on_cancel(it->second.order);
    if (!risk_manager_->check_order(replacement)) {
        (void)order_book_->modify_order(order_id, it->second.order);
        (void)risk_manager_->check_order(it->second.order);
        return false;
    }

    OrderState& state = it->second;
    state.order = replacement;
    state.initial_lots = replacement.quantity_lots;
    state.remaining_lots = replacement.quantity_lots;
    state.filled_lots = 0;
    state.status = OrderStatus::Resting;
    state.updated_at_ns = core::unix_now_ns();
    order_history_[order_id] = state;
    return true;
}

bool OrderManager::get_order_state(uint64_t order_id, OrderState* out_state) const {
    if (!out_state) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    auto active_it = active_orders_.find(order_id);
    if (active_it != active_orders_.end()) {
        *out_state = active_it->second;
        return true;
    }
    auto hist_it = order_history_.find(order_id);
    if (hist_it == order_history_.end()) return false;
    *out_state = hist_it->second;
    return true;
}

size_t OrderManager::active_order_count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return active_orders_.size();
}

bool OrderManager::is_valid_order(const Order& order) {
    if (order.order_id == 0) return false;
    if (order.quantity_lots <= 0) return false;
    if (order.side != SIDE_BUY && order.side != SIDE_SELL) return false;
    if (order.type == ORDER_TYPE_LIMIT && order.price_ticks <= 0) return false;
    if (order.type != ORDER_TYPE_LIMIT && order.price_ticks < 0) return false;
    return true;
}

void OrderManager::upsert_state(const OrderState& state) {
    std::lock_guard<std::mutex> lock(mutex_);
    order_history_[state.order.order_id] = state;
}

void OrderManager::apply_trade_to_maker(uint64_t maker_order_id, const Trade& trade) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto maker_it = active_orders_.find(maker_order_id);
    if (maker_it == active_orders_.end()) {
        return;
    }

    OrderState& maker = maker_it->second;
    Order maker_fill = maker.order;
    maker_fill.price_ticks = trade.price_ticks;
    maker_fill.quantity_lots = trade.quantity_lots;
    maker_fill.price = trade.price;
    maker_fill.quantity = trade.quantity;
    risk_manager_->on_fill(maker_fill);

    maker.filled_lots += trade.quantity_lots;
    maker.remaining_lots = std::max<int64_t>(0, maker.remaining_lots - trade.quantity_lots);
    maker.order.quantity_lots = maker.remaining_lots;
    maker.order.quantity = core::from_quantity_lots(maker.remaining_lots);
    maker.status = (maker.remaining_lots == 0) ? OrderStatus::Filled : OrderStatus::PartiallyFilled;
    maker.updated_at_ns = core::unix_now_ns();

    if (maker.remaining_lots == 0) {
        order_history_[maker.order.order_id] = maker;
        active_orders_.erase(maker_it);
        return;
    }
    order_history_[maker.order.order_id] = maker;
}

} // namespace argentum::trading
