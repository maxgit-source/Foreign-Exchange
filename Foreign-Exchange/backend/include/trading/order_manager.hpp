#pragma once

#include "core/types.h"
#include "risk/risk_manager.hpp"
#include "engine/order_book.hpp"
#include <memory>
#include <unordered_map>
#include <vector>

namespace argentum::trading {

enum class OrderRejectReason {
    None = 0,
    InvalidOrder = 1,
    DuplicateOrderId = 2,
    RiskRejected = 3,
    InternalError = 4
};

struct OrderSubmissionResult {
    bool accepted = false;
    bool resting = false;
    double filled_quantity = 0.0;
    double remaining_quantity = 0.0;
    OrderRejectReason reject_reason = OrderRejectReason::None;
    std::vector<Trade> trades;
};

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
    OrderSubmissionResult submit_order(const Order& order);

    bool cancel_order(uint64_t order_id);
    size_t active_order_count() const;

private:
    static bool is_valid_order(const Order& order);

    std::shared_ptr<risk::RiskManager> risk_manager_;
    std::shared_ptr<engine::OrderBook> order_book_;
    
    // Map OrderID -> Order
    std::unordered_map<uint64_t, Order> active_orders_;
};

} // namespace argentum::trading
