#pragma once

#include <string>
#include "core/types.h"

namespace argentum::gateway {

/**
 * @brief Abstract interface for connecting to real exchanges.
 */
class ExchangeGateway {
public:
    virtual ~ExchangeGateway() = default;

    virtual void connect() = 0;
    virtual void subscribe_market_data(const std::string& symbol) = 0;
    virtual void send_order(const Order& order) = 0;
};

/**
 * @brief Implementation for Binance (REST/WS).
 */
class BinanceAdapter : public ExchangeGateway {
public:
    void connect() override {
        // Implement WebSocket handshake here
        std::cout << "[Gateway] Connecting to Binance..." << std::endl;
    }

    void subscribe_market_data(const std::string& symbol) override {
        // {"method": "SUBSCRIBE", "params": ["btcusdt@trade"], "id": 1}
        std::cout << "[Gateway] Subscribed to " << symbol << " on Binance." << std::endl;
    }

    void send_order(const Order& order) override {
        // POST /api/v3/order
        std::cout << "[Gateway] Order " << order.order_id << " sent to Binance." << std::endl;
    }
};

} // namespace argentum::gateway
