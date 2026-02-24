#include "risk/risk_manager.hpp"

#include <algorithm>
#include <cmath>
#include <iostream>

namespace argentum::risk {

RiskManager::RiskManager(RiskLimits limits) : limits_(limits) {}

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
    std::lock_guard<std::mutex> lock(mutex_);

    if (reservations_.find(normalized.order_id) != reservations_.end()) {
        std::cerr << "[Risk] REJECT: Duplicate reservation for order_id " << normalized.order_id << std::endl;
        return false;
    }

    const int64_t proposed = committed_exposure_units_ + delta;
    const double proposed_abs = std::abs(static_cast<double>(proposed)) /
                                static_cast<double>(core::kNotionalScale);
    if (proposed_abs > limits_.max_position_exposure) {
        std::cerr << "[Risk] REJECT: Exposure limit exceeded." << std::endl;
        return false;
    }

    committed_exposure_units_ = proposed;
    reservations_[normalized.order_id] = Reservation{
        static_cast<Side>(normalized.side),
        normalized.price_ticks,
        normalized.quantity_lots
    };
    return true;
}

void RiskManager::on_fill(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);
    if (!is_valid_order(normalized)) return;

    std::lock_guard<std::mutex> lock(mutex_);
    auto it = reservations_.find(normalized.order_id);
    if (it != reservations_.end()) {
        const int64_t release_lots = std::min(normalized.quantity_lots, it->second.remaining_lots);
        if (release_lots > 0) {
            int64_t release_units = core::to_notional_units(it->second.reserved_price_ticks, release_lots);
            if (it->second.side == SIDE_SELL) {
                release_units = -release_units;
            }
            committed_exposure_units_ -= release_units;
            it->second.remaining_lots -= release_lots;
        }
        if (it->second.remaining_lots <= 0) {
            reservations_.erase(it);
        }
    }

    const int64_t fill_units = signed_notional_units(normalized);
    filled_exposure_units_ += fill_units;
}

void RiskManager::on_cancel(const Order& order) {
    Order normalized = order;
    core::normalize_order_scalars(&normalized);
    if (!is_valid_order(normalized)) return;

    std::lock_guard<std::mutex> lock(mutex_);
    auto it = reservations_.find(normalized.order_id);
    if (it == reservations_.end()) return;

    const int64_t release_lots = std::min(normalized.quantity_lots, it->second.remaining_lots);
    if (release_lots <= 0) return;

    int64_t release_units = core::to_notional_units(it->second.reserved_price_ticks, release_lots);
    if (it->second.side == SIDE_SELL) {
        release_units = -release_units;
    }
    committed_exposure_units_ -= release_units;
    it->second.remaining_lots -= release_lots;
    if (it->second.remaining_lots <= 0) {
        reservations_.erase(it);
    }
}

double RiskManager::committed_exposure() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return static_cast<double>(committed_exposure_units_) /
           static_cast<double>(core::kNotionalScale);
}

double RiskManager::filled_exposure() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return static_cast<double>(filled_exposure_units_) /
           static_cast<double>(core::kNotionalScale);
}

int64_t RiskManager::committed_exposure_units() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return committed_exposure_units_;
}

int64_t RiskManager::filled_exposure_units() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return filled_exposure_units_;
}

bool RiskManager::is_valid_order(const Order& order) {
    if (order.order_id == 0) return false;
    if (order.quantity_lots <= 0) return false;
    if (order.side != SIDE_BUY && order.side != SIDE_SELL) return false;
    if (order.type != ORDER_TYPE_MARKET &&
        order.type != ORDER_TYPE_LIMIT &&
        order.type != ORDER_TYPE_STOP) {
        return false;
    }
    if (order.tif != TIF_GTC &&
        order.tif != TIF_IOC &&
        order.tif != TIF_FOK) {
        return false;
    }
    if (order.price_ticks <= 0) return false;
    return true;
}

int64_t RiskManager::signed_notional_units(const Order& order) {
    return core::signed_notional_units(order);
}

} // namespace argentum::risk
