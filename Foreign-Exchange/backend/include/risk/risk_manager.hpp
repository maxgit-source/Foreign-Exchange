#pragma once

#include "core/types.h"
#include <atomic>
#include <mutex>

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

private:
    RiskLimits limits_;
    std::atomic<double> current_exposure_{0.0};
    std::atomic<double> daily_pl_{0.0};
    
    // Mutex not needed for atomics, but needed if limits change dynamically
    mutable std::mutex mtx_; 

    static double atomic_add(std::atomic<double>& target, double delta);
};

} // namespace argentum::risk
