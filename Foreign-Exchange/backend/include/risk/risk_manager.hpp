#pragma once

#include "core/types.h"
#include "core/fixed_point.hpp"

#include <cstdint>
#include <mutex>
#include <unordered_map>

namespace argentum::risk {

/**
 * @struct RiskLimits
 * @brief Configuration for risk checks.
 */
struct RiskLimits {
    double max_order_value;
    double max_position_exposure;
    double max_daily_loss;
};

/**
 * @class RiskManager
 * @brief Thread-safe pre-trade risk checker.
 */
class RiskManager {
public:
    explicit RiskManager(RiskLimits limits);

    /**
     * @brief Checks if an order can be placed.
     * @return true if approved, false if rejected.
     */
    bool check_order(const Order& order);

    /**
     * @brief Updates internal state after an execution.
     */
    void on_fill(const Order& order);

    /**
     * @brief Releases reserved exposure for canceled/unfilled quantity.
     */
    void on_cancel(const Order& order);

    /**
     * @brief Current reserved+active exposure tracked by risk checks.
     */
    double committed_exposure() const;

    /**
     * @brief Net executed exposure.
     */
    double filled_exposure() const;
    int64_t committed_exposure_units() const;
    int64_t filled_exposure_units() const;

private:
    struct Reservation {
        Side side = SIDE_BUY;
        int64_t reserved_price_ticks = 0;
        int64_t remaining_lots = 0;
    };

    RiskLimits limits_;
    mutable std::mutex mutex_;
    int64_t committed_exposure_units_ = 0;
    int64_t filled_exposure_units_ = 0;
    double daily_pl_ = 0.0;
    std::unordered_map<uint64_t, Reservation> reservations_;

    static bool is_valid_order(const Order& order);
    static int64_t signed_notional_units(const Order& order);
};

} // namespace argentum::risk
