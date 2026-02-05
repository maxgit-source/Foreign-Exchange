#include "risk/risk_manager.hpp"
#include <iostream>

namespace argentum::risk {

RiskManager::RiskManager(RiskLimits limits) : limits_(limits) {}

bool RiskManager::check_order(const Order& order) {
    // 1. Check Max Order Value
    double order_value = order.price * order.quantity;
    if (order_value > limits_.max_order_value) {
        std::cerr << "[Risk] REJECT: Order value " << order_value 
                  << " exceeds limit " << limits_.max_order_value << std::endl;
        return false;
    }

    // 2. Check Max Exposure (Simplified for sync check)
    if ((current_exposure_ + order_value) > limits_.max_position_exposure) {
        std::cerr << "[Risk] REJECT: Exposure limit exceeded." << std::endl;
        return false;
    }

    return true; // Approved
}

void RiskManager::on_fill(const Order& order) {
    double value = order.price * order.quantity;
    if (order.side == SIDE_BUY) {
        current_exposure_ = current_exposure_ + value;
    } else {
        current_exposure_ = current_exposure_ - value; // Simplified logic
    }
}

} // namespace argentum::risk
