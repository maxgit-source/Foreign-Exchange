#pragma once

#include "core/types.h"
#include "risk/risk_manager.hpp"
#include "engine/order_book.hpp"
#include "core/fixed_point.hpp"
#include "core/time_utils.hpp"
#include <memory>
#include <mutex>
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

enum class OrderStatus {
    New = 0,
    Resting = 1,
    PartiallyFilled = 2,
    Filled = 3,
    Canceled = 4,
    Rejected = 5
};

struct OrderState {
    Order order{};
    int64_t initial_lots = 0;
    int64_t remaining_lots = 0;
    int64_t filled_lots = 0;
    OrderStatus status = OrderStatus::New;
    OrderRejectReason reject_reason = OrderRejectReason::None;
    uint64_t updated_at_ns = 0;
};

struct OrderSubmissionResult {
    bool accepted = false;
    bool resting = false;
    double filled_quantity = 0.0;
    double remaining_quantity = 0.0;
    OrderStatus status = OrderStatus::New;
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
    bool cancel_order_partial(uint64_t order_id, double quantity);
    bool modify_order(uint64_t order_id, double new_price, double new_quantity);
    bool get_order_state(uint64_t order_id, OrderState* out_state) const;
    size_t active_order_count() const;

private:
    static bool is_valid_order(const Order& order);
    void upsert_state(const OrderState& state);
    void apply_trade_to_maker(uint64_t maker_order_id, const Trade& trade);

    std::shared_ptr<risk::RiskManager> risk_manager_;
    std::shared_ptr<engine::OrderBook> order_book_;
    mutable std::mutex mutex_;
    std::unordered_map<uint64_t, OrderState> active_orders_;
    std::unordered_map<uint64_t, OrderState> order_history_;
};

} // namespace argentum::trading
