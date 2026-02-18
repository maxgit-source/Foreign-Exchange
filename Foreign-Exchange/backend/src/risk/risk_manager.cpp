#include "risk/risk_manager.hpp"
#include <cmath>
#include <iostream>

namespace argentum::risk {

RiskManager::RiskManager(RiskLimits limits) : limits_(limits) {}

double RiskManager::atomic_add(std::atomic<double>& target, double delta) {
    double current = target.load(std::memory_order_relaxed);
    while (!target.compare_exchange_weak(current, current + delta, std::memory_order_relaxed)) {
    }
    return current + delta;
}

bool RiskManager::check_order(const Order& order) {
    if (!is_valid_order(order)) {
        std::cerr << "[Risk] REJECT: Invalid order fields." << std::endl;
        return false;
    }

    const double order_value_abs = std::abs(order.price * order.quantity);
    if (order_value_abs > limits_.max_order_value) {
        std::cerr << "[Risk] REJECT: Order value " << order_value_abs
                  << " exceeds limit " << limits_.max_order_value << std::endl;
        return false;
    }

    const double delta = signed_notional(order);
    double current = committed_exposure_.load(std::memory_order_relaxed);
    for (;;) {
        const double proposed = current + delta;
        if (std::abs(proposed) > limits_.max_position_exposure) {
            std::cerr << "[Risk] REJECT: Exposure limit exceeded." << std::endl;
            return false;
        }
        if (committed_exposure_.compare_exchange_weak(
                current, proposed, std::memory_order_relaxed, std::memory_order_relaxed)) {
            return true;
        }
    }
}

void RiskManager::on_fill(const Order& order) {
    if (!is_valid_order(order)) return;
    const double delta = signed_notional(order);
    atomic_add(filled_exposure_, delta);
    atomic_add(committed_exposure_, -delta);
}

void RiskManager::on_cancel(const Order& order) {
    if (!is_valid_order(order)) return;
    const double delta = signed_notional(order);
    atomic_add(committed_exposure_, -delta);
}

double RiskManager::committed_exposure() const {
    return committed_exposure_.load(std::memory_order_relaxed);
}

double RiskManager::filled_exposure() const {
    return filled_exposure_.load(std::memory_order_relaxed);
}

bool RiskManager::is_valid_order(const Order& order) {
    if (order.quantity <= 0.0) return false;
    if (order.side != SIDE_BUY && order.side != SIDE_SELL) return false;
    if (order.type != ORDER_TYPE_MARKET &&
        order.type != ORDER_TYPE_LIMIT &&
        order.type != ORDER_TYPE_STOP) {
        return false;
    }
    if (order.type == ORDER_TYPE_LIMIT && order.price <= 0.0) return false;
    if (order.type != ORDER_TYPE_LIMIT && order.price < 0.0) return false;
    return true;
}

double RiskManager::signed_notional(const Order& order) {
    const double gross = std::abs(order.price * order.quantity);
    return (order.side == SIDE_BUY) ? gross : -gross;
}

} // namespace argentum::risk
