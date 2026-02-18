#include <iostream>
#include <memory>
#include <vector>
#include <thread>
#include <chrono>
#include <cstring>

#include "bus/message_bus.hpp"
#include "bus/message_protocol.hpp"
#include "datafeed/feed_player.hpp"
#include "persist/data_writer.hpp"
#include "trading/order_manager.hpp"
#include "risk/risk_manager.hpp"
#include "benchmark/latency_tester.hpp"
#include "gateway/exchange_gateway.hpp"
#include "codec/market_tick_codec.hpp"
#include "alerts/alert_system.hpp"
#include "system/cpu_utils.hpp"
#include "audit/logger.hpp"
#include "api/market_gateway.hpp"

int main() {
    // 1. System Init
    argentum::system::pin_thread_to_core(0);
    argentum::audit::Logger::instance().log(argentum::audit::LogLevel::INFO, "System Booting...");

    // 2. Alert System
    argentum::alerts::AlertSystem alerts;
    alerts.register_handler(argentum::alerts::AlertSystem::console_handler);
    alerts.dispatch(argentum::alerts::AlertSeverity::INFO, "Alert System Online.");

    // 3. Exchange Gateway
    argentum::gateway::BinanceAdapter binance;
    binance.connect();
    binance.subscribe_market_data("BTCUSDT");

    // 4. Message Bus + Persistence
    auto bus = argentum::bus::create_inproc_bus();
    bus->connect("inproc://market", true);

    argentum::persist::DataWriterService writer;
    writer.set_max_batch(256);
    writer.set_flush_interval_ms(50);
    writer.start();

    bus->subscribe("market.ticks", [&](const void* data, size_t size) {
        MarketTick tick{};
        if (argentum::codec::decode_market_tick(data, size, &tick) != ARGENTUM_OK) return;
        writer.enqueue(tick);
    });
    argentum::api::MarketGatewayService gateway(bus);
    gateway.start();

    argentum::datafeed::FeedPlayer player(bus, "market.ticks");
    size_t published = player.play_file("data/sample_ticks.jsonl", FEED_FORMAT_JSON, 0);
    if (published == 0) {
        MarketTick tick{};
        tick.timestamp_ns = 1700000000000000000ULL;
        tick.price = 50000.0;
        tick.quantity = 0.1;
        std::strncpy(tick.symbol, "BTC/USDT", sizeof(tick.symbol) - 1);
        std::strncpy(tick.source, "BINANCE", sizeof(tick.source) - 1);
        tick.side = SIDE_BUY;
        std::vector<uint8_t> payload;
        bool published_ok = false;
#ifdef ARGENTUM_USE_FLATBUFFERS
        if (argentum::codec::encode_market_tick_flatbuffers(tick, &payload, false) == ARGENTUM_OK) {
            if (bus->publish("market.ticks", payload.data(), payload.size()) == ARGENTUM_OK) {
                published_ok = true;
            }
        }
#else
        if (argentum::codec::encode_market_tick_legacy(tick, &payload) == ARGENTUM_OK) {
            if (bus->publish("market.ticks", payload.data(), payload.size()) == ARGENTUM_OK) {
                published_ok = true;
            }
        }
#endif
        if (published_ok) {
            published = 1;
        }
    }
    std::cout << "[Datafeed] Published " << published << " ticks." << std::endl;
    for (int i = 0; i < 20; ++i) {
        MarketTick latest{};
        if (gateway.get_latest_tick("BTC/USDT", &latest)) {
            break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    std::cout << "[API] Snapshot BTC/USDT: " << gateway.latest_tick_json("BTC/USDT") << std::endl;
    std::cout << "[API] Health: " << gateway.health_json() << std::endl;

    // 4. Latency Benchmark (Phase 16)
    argentum::benchmark::LatencyTester tester;
    tester.start(1000000); // 1 Million Ops
    tester.report();

    std::cout << "[Argentum-FX] Main Loop Entering Wait State..." << std::endl;
    // In real app: while(running) { poll(); }

    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    writer.stop();
    gateway.stop();

    return 0;
}
