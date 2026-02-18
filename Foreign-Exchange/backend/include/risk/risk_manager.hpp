#pragma once

#include "core/types.h"
#include <atomic>

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

private:
    RiskLimits limits_;
    std::atomic<double> committed_exposure_{0.0};
    std::atomic<double> filled_exposure_{0.0};
    std::atomic<double> daily_pl_{0.0};
    static bool is_valid_order(const Order& order);
    static double signed_notional(const Order& order);
    static double atomic_add(std::atomic<double>& target, double delta);
};

} // namespace argentum::risk
