#include "trading/order_manager.hpp"
#include <iostream>

namespace argentum::trading {

OrderManager::OrderManager(std::shared_ptr<risk::RiskManager> risk, 
                           std::shared_ptr<engine::OrderBook> book)
    : risk_manager_(std::move(risk)), order_book_(std::move(book)) {}

bool OrderManager::submit_order(Order& order) {
    // 1. Risk Check
    if (!risk_manager_->check_order(order)) {
        std::cout << "[OMS] Order " << order.order_id << " rejected by Risk Manager." << std::endl;
        return false; // Rejected
    }

    // 2. Send to Matching Engine
    if (order_book_->add_order(order)) {
        active_orders_[order.order_id] = order;
        std::cout << "[OMS] Order " << order.order_id << " accepted and placed." << std::endl;
        return true;
    }

    return false;
}

} // namespace argentum::trading
