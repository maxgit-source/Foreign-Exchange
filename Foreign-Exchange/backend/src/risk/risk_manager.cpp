#include "risk/risk_manager.hpp"
#include <cmath>
#include <iostream>

namespace argentum::risk {

RiskManager::RiskManager(RiskLimits limits) : limits_(limits) {}

int64_t RiskManager::atomic_add(std::atomic<int64_t>& target, int64_t delta) {
    int64_t current = target.load(std::memory_order_relaxed);
    while (!target.compare_exchange_weak(current, current + delta, std::memory_order_relaxed)) {
    }
    return current + delta;
}

bool RiskManager::check_order(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);

    if (!is_valid_order(normalized)) {
        std::cerr << "[Risk] REJECT: Invalid order fields." << std::endl;
        return false;
    }

    const double order_value_abs = std::abs(normalized.price * normalized.quantity);
    if (order_value_abs > limits_.max_order_value) {
        std::cerr << "[Risk] REJECT: Order value " << order_value_abs
                  << " exceeds limit " << limits_.max_order_value << std::endl;
        return false;
    }

    const int64_t delta = signed_notional_units(normalized);
    int64_t current = committed_exposure_units_.load(std::memory_order_relaxed);
    for (;;) {
        const int64_t proposed = current + delta;
        const double proposed_abs = std::abs(static_cast<double>(proposed)) / static_cast<double>(core::kNotionalScale);
        if (proposed_abs > limits_.max_position_exposure) {
            std::cerr << "[Risk] REJECT: Exposure limit exceeded." << std::endl;
            return false;
        }
        if (committed_exposure_units_.compare_exchange_weak(
                current, proposed, std::memory_order_relaxed, std::memory_order_relaxed)) {
            return true;
        }
    }
}

void RiskManager::on_fill(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);
    if (!is_valid_order(normalized)) return;
    const int64_t delta = signed_notional_units(normalized);
    atomic_add(filled_exposure_units_, delta);
    atomic_add(committed_exposure_units_, -delta);
}

void RiskManager::on_cancel(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);
    if (!is_valid_order(normalized)) return;
    const int64_t delta = signed_notional_units(normalized);
    atomic_add(committed_exposure_units_, -delta);
}

double RiskManager::committed_exposure() const {
    return static_cast<double>(committed_exposure_units_.load(std::memory_order_relaxed)) /
           static_cast<double>(core::kNotionalScale);
}

double RiskManager::filled_exposure() const {
    return static_cast<double>(filled_exposure_units_.load(std::memory_order_relaxed)) /
           static_cast<double>(core::kNotionalScale);
}

int64_t RiskManager::committed_exposure_units() const {
    return committed_exposure_units_.load(std::memory_order_relaxed);
}

int64_t RiskManager::filled_exposure_units() const {
    return filled_exposure_units_.load(std::memory_order_relaxed);
}

bool RiskManager::is_valid_order(const Order& order) {
    if (order.quantity_lots <= 0) return false;
    if (order.side != SIDE_BUY && order.side != SIDE_SELL) return false;
    if (order.type != ORDER_TYPE_MARKET &&
        order.type != ORDER_TYPE_LIMIT &&
        order.type != ORDER_TYPE_STOP) {
        return false;
    }
    if (order.type == ORDER_TYPE_LIMIT && order.price_ticks <= 0) return false;
    if (order.type != ORDER_TYPE_LIMIT && order.price_ticks < 0) return false;
    return true;
}

int64_t RiskManager::signed_notional_units(const Order& order) {
    return core::signed_notional_units(order);
}

} // namespace argentum::risk
