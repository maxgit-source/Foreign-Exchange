#include "trading/order_manager.hpp"

#include <algorithm>
#include <iostream>

namespace argentum::trading {

OrderManager::OrderManager(std::shared_ptr<risk::RiskManager> risk, 
                           std::shared_ptr<engine::OrderBook> book)
    : risk_manager_(std::move(risk)), order_book_(std::move(book)) {}

OrderSubmissionResult OrderManager::submit_order(const Order& order) {
    OrderSubmissionResult result{};
    result.remaining_quantity = order.quantity;

    if (!risk_manager_ || !order_book_) {
        result.reject_reason = OrderRejectReason::InternalError;
        return result;
    }

    if (!is_valid_order(order)) {
        result.reject_reason = OrderRejectReason::InvalidOrder;
        return result;
    }

    if (active_orders_.find(order.order_id) != active_orders_.end()) {
        result.reject_reason = OrderRejectReason::DuplicateOrderId;
        return result;
    }

    if (!risk_manager_->check_order(order)) {
        result.reject_reason = OrderRejectReason::RiskRejected;
        std::cout << "[OMS] Order " << order.order_id << " rejected by Risk Manager." << std::endl;
        return result;
    }

    result.trades = order_book_->match_order(order);
    for (const Trade& trade : result.trades) {
        Order taker_fill = order;
        taker_fill.price = trade.price;
        taker_fill.quantity = trade.quantity;
        risk_manager_->on_fill(taker_fill);
        result.filled_quantity += trade.quantity;

        auto maker_it = active_orders_.find(trade.maker_order_id);
        if (maker_it != active_orders_.end()) {
            Order maker_fill = maker_it->second;
            maker_fill.price = trade.price;
            maker_fill.quantity = trade.quantity;
            risk_manager_->on_fill(maker_fill);

            maker_it->second.quantity = std::max(0.0, maker_it->second.quantity - trade.quantity);
            if (maker_it->second.quantity <= 0.0) {
                active_orders_.erase(maker_it);
            }
        }
    }

    result.remaining_quantity = std::max(0.0, order.quantity - result.filled_quantity);
    result.resting = (order.type == ORDER_TYPE_LIMIT && result.remaining_quantity > 0.0);

    if (result.resting) {
        Order residual = order;
        residual.quantity = result.remaining_quantity;
        active_orders_[residual.order_id] = residual;
        std::cout << "[OMS] Order " << order.order_id << " accepted and placed with remaining "
                  << result.remaining_quantity << "." << std::endl;
    } else {
        if (result.remaining_quantity > 0.0) {
            Order canceled = order;
            canceled.quantity = result.remaining_quantity;
            risk_manager_->on_cancel(canceled);
        }
        active_orders_.erase(order.order_id);
        std::cout << "[OMS] Order " << order.order_id << " fully processed." << std::endl;
    }

    result.accepted = true;
    return result;
}

bool OrderManager::cancel_order(uint64_t order_id) {
    auto it = active_orders_.find(order_id);
    if (it == active_orders_.end()) return false;

    if (!order_book_->cancel_order(order_id)) {
        return false;
    }

    risk_manager_->on_cancel(it->second);
    active_orders_.erase(it);
    std::cout << "[OMS] Order " << order_id << " canceled." << std::endl;
    return true;
}

size_t OrderManager::active_order_count() const {
    return active_orders_.size();
}

bool OrderManager::is_valid_order(const Order& order) {
    if (order.order_id == 0) return false;
    if (order.quantity <= 0.0) return false;
    if (order.side != SIDE_BUY && order.side != SIDE_SELL) return false;
    if (order.type == ORDER_TYPE_LIMIT && order.price <= 0.0) return false;
    if (order.type != ORDER_TYPE_LIMIT && order.price < 0.0) return false;
    return true;
}

} // namespace argentum::trading
