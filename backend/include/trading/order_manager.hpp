#pragma once

#include "core/types.h"
#include "risk/risk_manager.hpp"
#include "engine/order_book.hpp"
#include <memory>
#include <unordered_map>

namespace argentum::trading {

/**
 * @class OrderManager
 * @brief Orchestrates the lifecycle of orders.
 */
class OrderManager {
public:
    OrderManager(std::shared_ptr<risk::RiskManager> risk, 
                 std::shared_ptr<engine::OrderBook> book);

    /**
     * @brief Entry point for new orders from API/Strategy.
     */
    bool submit_order(Order& order);

    // TODO: cancel_order, modify_order

private:
    std::shared_ptr<risk::RiskManager> risk_manager_;
    std::shared_ptr<engine::OrderBook> order_book_;
    
    // Map OrderID -> Order
    std::unordered_map<uint64_t, Order> active_orders_;
};

} // namespace argentum::trading
