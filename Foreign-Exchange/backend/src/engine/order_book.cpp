#include "engine/order_book.hpp"

#include <algorithm>

namespace argentum::engine {

OrderBook::OrderBook(const std::string& symbol) : symbol_(symbol) {}

bool OrderBook::add_order(const Order& order) {
    if (order.quantity <= 0.0) return false;

    if (order.side == SIDE_BUY) {
        auto& level = bids_[order.price];
        level.push_back(order);
        auto it = std::prev(level.end());
        order_lookup_[order.order_id] = OrderLocator{(Side)order.side, order.price, it};
    } else {
        auto& level = asks_[order.price];
        level.push_back(order);
        auto it = std::prev(level.end());
        order_lookup_[order.order_id] = OrderLocator{(Side)order.side, order.price, it};
    }
    return true;
}

bool OrderBook::cancel_order(uint64_t order_id) {
    auto found = order_lookup_.find(order_id);
    if (found == order_lookup_.end()) return false;

    const OrderLocator& loc = found->second;
    if (loc.side == SIDE_BUY) {
        auto level_it = bids_.find(loc.price);
        if (level_it == bids_.end()) {
            order_lookup_.erase(found);
            return false;
        }
        level_it->second.erase(loc.it);
        if (level_it->second.empty()) {
            bids_.erase(level_it);
        }
    } else {
        auto level_it = asks_.find(loc.price);
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

std::vector<Trade> OrderBook::match_order(const Order& incoming) {
    std::vector<Trade> trades;
    if (incoming.quantity <= 0.0) return trades;

    double remaining = incoming.quantity;
    if (incoming.side == SIDE_BUY) {
        auto it = asks_.begin();
        while (it != asks_.end() && remaining > 0.0) {
            double level_price = it->first;

            if (incoming.type == ORDER_TYPE_LIMIT && level_price > incoming.price) break;

            auto& orders = it->second;
            for (auto order_it = orders.begin(); order_it != orders.end() && remaining > 0.0; ) {
                double fill_qty = std::min(remaining, order_it->quantity);

                Trade trade{};
                trade.trade_id = next_trade_id_++;
                trade.maker_order_id = order_it->order_id;
                trade.taker_order_id = incoming.order_id;
                trade.timestamp_ns = incoming.timestamp_ns;
                trade.price = level_price;
                trade.quantity = fill_qty;
                trade.side = incoming.side;
                trades.push_back(trade);

                order_it->quantity -= fill_qty;
                remaining -= fill_qty;

                if (order_it->quantity <= 0.0) {
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
        while (it != bids_.end() && remaining > 0.0) {
            double level_price = it->first;

            if (incoming.type == ORDER_TYPE_LIMIT && level_price < incoming.price) break;

            auto& orders = it->second;
            for (auto order_it = orders.begin(); order_it != orders.end() && remaining > 0.0; ) {
                double fill_qty = std::min(remaining, order_it->quantity);

                Trade trade{};
                trade.trade_id = next_trade_id_++;
                trade.maker_order_id = order_it->order_id;
                trade.taker_order_id = incoming.order_id;
                trade.timestamp_ns = incoming.timestamp_ns;
                trade.price = level_price;
                trade.quantity = fill_qty;
                trade.side = incoming.side;
                trades.push_back(trade);

                order_it->quantity -= fill_qty;
                remaining -= fill_qty;

                if (order_it->quantity <= 0.0) {
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

    if (remaining > 0.0 && incoming.type == ORDER_TYPE_LIMIT) {
        Order residual = incoming;
        residual.quantity = remaining;
        add_order(residual);
    }

    return trades;
}

std::optional<double> OrderBook::get_best_bid() const {
    if (bids_.empty()) return std::nullopt;
    return bids_.begin()->first;
}

std::optional<double> OrderBook::get_best_ask() const {
    if (asks_.empty()) return std::nullopt;
    return asks_.begin()->first;
}

std::optional<double> OrderBook::get_spread() const {
    auto bid = get_best_bid();
    auto ask = get_best_ask();
    if (!bid || !ask) return std::nullopt;
    return *ask - *bid;
}

std::optional<double> OrderBook::vwap(Side side, double quantity) const {
    if (quantity <= 0.0) return std::nullopt;

    double remaining = quantity;
    double notional = 0.0;

    if (side == SIDE_BUY) {
        for (const auto& [price, orders] : asks_) {
            double level_qty = 0.0;
            for (const auto& order : orders) {
                level_qty += order.quantity;
            }

            double take = std::min(remaining, level_qty);
            notional += take * price;
            remaining -= take;

            if (remaining <= 0.0) break;
        }
    } else {
        for (const auto& [price, orders] : bids_) {
            double level_qty = 0.0;
            for (const auto& order : orders) {
                level_qty += order.quantity;
            }

            double take = std::min(remaining, level_qty);
            notional += take * price;
            remaining -= take;

            if (remaining <= 0.0) break;
        }
    }

    if (remaining > 0.0) return std::nullopt;
    return notional / quantity;
}

} // namespace argentum::engine
