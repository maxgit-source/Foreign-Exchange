#pragma once

#include "core/types.h"
#include "core/fixed_point.hpp"
#include <vector>
#include <map>
#include <list>
#include <optional>
#include <string>
#include <memory>
#include <unordered_map>

namespace argentum::engine {

/**
 * @class OrderBook
 * @brief High-performance Limit Order Book implementation.
 * Uses RB-Tree (std::map/std::set) for price levels.
 * Future Optimization: Replace std::map with flat_map or custom pool allocator.
 */
class OrderBook {
public:
    explicit OrderBook(const std::string& symbol);
    ~OrderBook() = default;

    // Delete copy/move to prevent accidental overhead
    OrderBook(const OrderBook&) = delete;
    OrderBook& operator=(const OrderBook&) = delete;

    /**
     * @brief Adds a new order to the book.
     * @param order The order struct.
     * @return true if added, false if rejected.
     */
    bool add_order(const Order& order);

    /**
     * @brief Cancels an existing order.
     */
    bool cancel_order(uint64_t order_id);
    bool cancel_order_partial(uint64_t order_id, int64_t reduce_lots, Order* out_updated = nullptr);
    bool modify_order(uint64_t order_id, const Order& replacement);
    bool get_order(uint64_t order_id, Order* out_order) const;

    /**
     * @brief Matches incoming market orders against the book.
     * @return Vector of matched trades.
     */
    std::vector<Trade> match_order(const Order& incoming);

    /**
     * @brief Get the best Bid price.
     */
    [[nodiscard]] std::optional<double> get_best_bid() const;

    /**
     * @brief Get the best Ask price.
     */
    [[nodiscard]] std::optional<double> get_best_ask() const;

    /**
     * @brief Get the spread (best ask - best bid).
     */
    [[nodiscard]] std::optional<double> get_spread() const;

    /**
     * @brief Calculate VWAP for a given quantity on a side.
     */
    [[nodiscard]] std::optional<double> vwap(Side side, double quantity) const;

private:
    std::string symbol_;

    // Price Levels: Price -> List of Orders (Time priority)
    // Using std::greater for Bids (Highest first)
    using OrderList = std::list<Order>;
    std::map<int64_t, OrderList, std::greater<int64_t>> bids_;
    
    // Using std::less for Asks (Lowest first)
    std::map<int64_t, OrderList, std::less<int64_t>> asks_;

    struct OrderLocator {
        Side side;
        int64_t price_ticks;
        OrderList::iterator it;
    };

    std::unordered_map<uint64_t, OrderLocator> order_lookup_;
    uint64_t next_trade_id_ = 1;
};

} // namespace argentum::engine
