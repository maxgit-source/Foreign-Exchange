#include "engine/order_book.hpp"

#include <algorithm>
#include <cmath>

namespace argentum::engine {

OrderBook::OrderBook(const std::string& symbol) : symbol_(symbol) {}

bool OrderBook::add_order(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);

    if (normalized.quantity_lots <= 0) return false;
    if (order.order_id == 0) return false;
    if (normalized.side != SIDE_BUY && normalized.side != SIDE_SELL) return false;
    if (normalized.type == ORDER_TYPE_LIMIT && normalized.price_ticks <= 0) return false;
    if (normalized.type != ORDER_TYPE_LIMIT && normalized.price_ticks < 0) return false;
    if (order_lookup_.find(order.order_id) != order_lookup_.end()) return false;

    if (normalized.side == SIDE_BUY) {
        auto& level = bids_[normalized.price_ticks];
        level.push_back(normalized);
        auto it = std::prev(level.end());
        order_lookup_[normalized.order_id] = OrderLocator{SIDE_BUY, normalized.price_ticks, it};
    } else {
        auto& level = asks_[normalized.price_ticks];
        level.push_back(normalized);
        auto it = std::prev(level.end());
        order_lookup_[normalized.order_id] = OrderLocator{SIDE_SELL, normalized.price_ticks, it};
    }
    return true;
}

bool OrderBook::cancel_order(uint64_t order_id) {
    auto found = order_lookup_.find(order_id);
    if (found == order_lookup_.end()) return false;

    const OrderLocator& loc = found->second;
    if (loc.side == SIDE_BUY) {
        auto level_it = bids_.find(loc.price_ticks);
        if (level_it == bids_.end()) {
            order_lookup_.erase(found);
            return false;
        }
        level_it->second.erase(loc.it);
        if (level_it->second.empty()) {
            bids_.erase(level_it);
        }
    } else {
        auto level_it = asks_.find(loc.price_ticks);
        if (level_it == asks_.end()) {
            order_lookup_.erase(found);
            return false;
        }
        level_it->second.erase(loc.it);
        if (level_it->second.empty()) {
            asks_.erase(level_it);
        }
    }

    order_lookup_.erase(found);
    return true;
}

bool OrderBook::cancel_order_partial(uint64_t order_id, int64_t reduce_lots, Order* out_updated) {
    if (reduce_lots <= 0) return false;
    auto found = order_lookup_.find(order_id);
    if (found == order_lookup_.end()) return false;

    const OrderLocator& loc = found->second;
    auto handle_level = [&](auto& levels) -> bool {
        auto level_it = levels.find(loc.price_ticks);
        if (level_it == levels.end()) return false;
        if (loc.it == level_it->second.end()) return false;

        Order& order = *loc.it;
        if (reduce_lots >= order.quantity_lots) {
            if (out_updated) {
                *out_updated = order;
                out_updated->quantity_lots = 0;
                out_updated->quantity = 0.0;
            }
            level_it->second.erase(loc.it);
            if (level_it->second.empty()) {
                levels.erase(level_it);
            }
            order_lookup_.erase(found);
            return true;
        }

        order.quantity_lots -= reduce_lots;
        order.quantity = core::from_quantity_lots(order.quantity_lots);
        if (out_updated) {
            *out_updated = order;
        }
        return true;
    };

    if (loc.side == SIDE_BUY) {
        return handle_level(bids_);
    }
    return handle_level(asks_);
}

bool OrderBook::modify_order(uint64_t order_id, const Order& replacement) {
    Order current{};
    if (!get_order(order_id, &current)) return false;
    if (!cancel_order(order_id)) return false;

    Order normalized = replacement;
    normalized.order_id = order_id;
    core::normalize_order_scalars(&normalized);
    if (!add_order(normalized)) {
        (void)add_order(current);
        return false;
    }
    return true;
}

bool OrderBook::get_order(uint64_t order_id, Order* out_order) const {
    if (!out_order) return false;
    auto found = order_lookup_.find(order_id);
    if (found == order_lookup_.end()) return false;
    const OrderLocator& loc = found->second;

    auto get_from_level = [&](const auto& levels) -> bool {
        auto level_it = levels.find(loc.price_ticks);
        if (level_it == levels.end()) return false;
        if (loc.it == level_it->second.end()) return false;
        *out_order = *loc.it;
        return true;
    };

    if (loc.side == SIDE_BUY) {
        return get_from_level(bids_);
    }
    return get_from_level(asks_);
}

std::vector<Trade> OrderBook::match_order(const Order& incoming) {
    std::vector<Trade> trades;
    Order normalized = incoming;
    core::normalize_order_scalars(&normalized);

    if (normalized.quantity_lots <= 0) return trades;
    if (normalized.side != SIDE_BUY && normalized.side != SIDE_SELL) return trades;
    if (normalized.type == ORDER_TYPE_LIMIT && normalized.price_ticks <= 0) return trades;
    if (normalized.type != ORDER_TYPE_LIMIT && normalized.price_ticks < 0) return trades;

    int64_t remaining_lots = normalized.quantity_lots;
    if (normalized.side == SIDE_BUY) {
        auto it = asks_.begin();
        while (it != asks_.end() && remaining_lots > 0) {
            int64_t level_price_ticks = it->first;

            if (normalized.type == ORDER_TYPE_LIMIT && level_price_ticks > normalized.price_ticks) break;

            auto& orders = it->second;
            for (auto order_it = orders.begin(); order_it != orders.end() && remaining_lots > 0; ) {
                const int64_t fill_lots = std::min(remaining_lots, order_it->quantity_lots);

                Trade trade{};
                trade.trade_id = next_trade_id_++;
                trade.maker_order_id = order_it->order_id;
                trade.taker_order_id = normalized.order_id;
                trade.timestamp_ns = normalized.timestamp_ns;
                trade.price_ticks = level_price_ticks;
                trade.quantity_lots = fill_lots;
                trade.price = core::from_price_ticks(level_price_ticks);
                trade.quantity = core::from_quantity_lots(fill_lots);
                trade.side = normalized.side;
                trades.push_back(trade);

                order_it->quantity_lots -= fill_lots;
                order_it->quantity = core::from_quantity_lots(order_it->quantity_lots);
                remaining_lots -= fill_lots;

                if (order_it->quantity_lots <= 0) {
                    order_lookup_.erase(order_it->order_id);
                    order_it = orders.erase(order_it);
                } else {
                    ++order_it;
                }
            }

            if (orders.empty()) {
                it = asks_.erase(it);
            } else {
                ++it;
            }
        }
    } else {
        auto it = bids_.begin();
        while (it != bids_.end() && remaining_lots > 0) {
            int64_t level_price_ticks = it->first;

            if (normalized.type == ORDER_TYPE_LIMIT && level_price_ticks < normalized.price_ticks) break;

            auto& orders = it->second;
            for (auto order_it = orders.begin(); order_it != orders.end() && remaining_lots > 0; ) {
                const int64_t fill_lots = std::min(remaining_lots, order_it->quantity_lots);

                Trade trade{};
                trade.trade_id = next_trade_id_++;
                trade.maker_order_id = order_it->order_id;
                trade.taker_order_id = normalized.order_id;
                trade.timestamp_ns = normalized.timestamp_ns;
                trade.price_ticks = level_price_ticks;
                trade.quantity_lots = fill_lots;
                trade.price = core::from_price_ticks(level_price_ticks);
                trade.quantity = core::from_quantity_lots(fill_lots);
                trade.side = normalized.side;
                trades.push_back(trade);

                order_it->quantity_lots -= fill_lots;
                order_it->quantity = core::from_quantity_lots(order_it->quantity_lots);
                remaining_lots -= fill_lots;

                if (order_it->quantity_lots <= 0) {
                    order_lookup_.erase(order_it->order_id);
                    order_it = orders.erase(order_it);
                } else {
                    ++order_it;
                }
            }

            if (orders.empty()) {
                it = bids_.erase(it);
            } else {
                ++it;
            }
        }
    }

    if (remaining_lots > 0 && normalized.type == ORDER_TYPE_LIMIT) {
        Order residual = normalized;
        residual.quantity_lots = remaining_lots;
        residual.quantity = core::from_quantity_lots(remaining_lots);
        (void)add_order(residual);
    }

    return trades;
}

std::optional<double> OrderBook::get_best_bid() const {
    if (bids_.empty()) return std::nullopt;
    return core::from_price_ticks(bids_.begin()->first);
}

std::optional<double> OrderBook::get_best_ask() const {
    if (asks_.empty()) return std::nullopt;
    return core::from_price_ticks(asks_.begin()->first);
}

std::optional<double> OrderBook::get_spread() const {
    auto bid = get_best_bid();
    auto ask = get_best_ask();
    if (!bid || !ask) return std::nullopt;
    return *ask - *bid;
}

std::optional<double> OrderBook::vwap(Side side, double quantity) const {
    const int64_t target_lots = core::to_quantity_lots(quantity);
    if (target_lots <= 0) return std::nullopt;

    int64_t remaining_lots = target_lots;
    long double notional_units = 0.0;

    if (side == SIDE_BUY) {
        for (const auto& [price_ticks, orders] : asks_) {
            int64_t level_lots = 0;
            for (const auto& order : orders) {
                level_lots += order.quantity_lots;
            }

            int64_t take_lots = std::min(remaining_lots, level_lots);
            notional_units += static_cast<long double>(core::to_notional_units(price_ticks, take_lots));
            remaining_lots -= take_lots;

            if (remaining_lots <= 0) break;
        }
    } else {
        for (const auto& [price_ticks, orders] : bids_) {
            int64_t level_lots = 0;
            for (const auto& order : orders) {
                level_lots += order.quantity_lots;
            }

            int64_t take_lots = std::min(remaining_lots, level_lots);
            notional_units += static_cast<long double>(core::to_notional_units(price_ticks, take_lots));
            remaining_lots -= take_lots;

            if (remaining_lots <= 0) break;
        }
    }

    if (remaining_lots > 0) return std::nullopt;
    const long double avg_ticks = notional_units / static_cast<long double>(target_lots);
    return core::from_price_ticks(static_cast<int64_t>(std::llround(avg_ticks)));
}

} // namespace argentum::engine
