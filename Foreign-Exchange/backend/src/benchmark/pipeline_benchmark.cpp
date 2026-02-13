#include "bus/message_bus.hpp"
#include "codec/market_tick_codec.hpp"
#include "persist/data_writer.hpp"
#include "core/time_utils.hpp"

#include <atomic>
#include <iostream>
#include <thread>
#include <vector>
#include <cstring>
#include <chrono>
#include <deque>
#include <mutex>
#include <algorithm>

int main() {
    argentum::bus::InprocBusConfig config;
    config.queue_capacity = 8192;
    config.policy = argentum::bus::BackpressurePolicy::DropNewest;
    config.consumer_threads = 1;

    auto bus = argentum::bus::create_inproc_bus(config);

    argentum::persist::DataWriterService writer;
    writer.set_flush_interval_ms(20);
    writer.set_queue_capacity(16384);
    writer.set_overflow_policy(argentum::persist::DataWriterService::OverflowPolicy::DropNewest);
    writer.set_csv_path("data/market_ticks_bench.csv");
    writer.start();

    const size_t total = 100000;
    std::atomic<size_t> consumed{0};
    std::atomic<size_t> published{0};
    std::atomic<size_t> dropped{0};
    std::deque<uint64_t> send_times;
    std::vector<uint64_t> latencies;
    latencies.reserve(total);
    std::mutex times_mtx;
    std::mutex lat_mtx;

    bus->subscribe("market.ticks", [&](const void* data, size_t size) {
        MarketTick tick{};
        if (argentum::codec::decode_market_tick(data, size, &tick) == ARGENTUM_OK) {
            writer.enqueue(tick);
            uint64_t recv_ns = argentum::core::now_ns();
            uint64_t send_ns = 0;
            {
                std::lock_guard<std::mutex> lock(times_mtx);
                if (!send_times.empty()) {
                    send_ns = send_times.front();
                    send_times.pop_front();
                }
            }
            if (send_ns > 0) {
                std::lock_guard<std::mutex> lock(lat_mtx);
                latencies.push_back(recv_ns - send_ns);
            }
            consumed.fetch_add(1, std::memory_order_relaxed);
        }
    });

    std::vector<uint8_t> payload;
    auto start_ns = argentum::core::now_ns();

    for (size_t i = 0; i < total; ++i) {
        MarketTick tick{};
        tick.timestamp_ns = argentum::core::unix_now_ns();
        tick.price = 100.0 + static_cast<double>(i % 1000) * 0.01;
        tick.quantity = 1.0;
        std::strncpy(tick.symbol, "BTC/USDT", sizeof(tick.symbol) - 1);
        std::strncpy(tick.source, "SIM", sizeof(tick.source) - 1);
        tick.side = SIDE_BUY;

#ifdef ARGENTUM_USE_FLATBUFFERS
        if (argentum::codec::encode_market_tick_flatbuffers(tick, &payload, false) == ARGENTUM_OK) {
            uint64_t send_ns = argentum::core::now_ns();
            {
                std::lock_guard<std::mutex> lock(times_mtx);
                send_times.push_back(send_ns);
            }
            if (bus->publish("market.ticks", payload.data(), payload.size()) == ARGENTUM_OK) {
                published.fetch_add(1, std::memory_order_relaxed);
            } else {
                dropped.fetch_add(1, std::memory_order_relaxed);
                std::lock_guard<std::mutex> lock(times_mtx);
                if (!send_times.empty()) send_times.pop_back();
            }
        }
#else
        if (argentum::codec::encode_market_tick_legacy(tick, &payload) == ARGENTUM_OK) {
            uint64_t send_ns = argentum::core::now_ns();
            {
                std::lock_guard<std::mutex> lock(times_mtx);
                send_times.push_back(send_ns);
            }
            if (bus->publish("market.ticks", payload.data(), payload.size()) == ARGENTUM_OK) {
                published.fetch_add(1, std::memory_order_relaxed);
            } else {
                dropped.fetch_add(1, std::memory_order_relaxed);
                std::lock_guard<std::mutex> lock(times_mtx);
                if (!send_times.empty()) send_times.pop_back();
            }
        }
#endif
    }

    auto publish_end_ns = argentum::core::now_ns();

    while (consumed.load(std::memory_order_relaxed) < published.load(std::memory_order_relaxed)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    writer.stop();
    auto end_ns = argentum::core::now_ns();

    double publish_ms = (publish_end_ns - start_ns) / 1e6;
    double end_to_end_ms = (end_ns - start_ns) / 1e6;
    double throughput = (published.load(std::memory_order_relaxed) / (publish_ms / 1000.0));

    double p50_us = 0.0;
    double p95_us = 0.0;
    double p99_us = 0.0;
    double p999_us = 0.0;
    {
        std::lock_guard<std::mutex> lock(lat_mtx);
        if (!latencies.empty()) {
            std::sort(latencies.begin(), latencies.end());
            auto idx = [&](double q) {
                size_t i = static_cast<size_t>(latencies.size() * q);
                if (i >= latencies.size()) i = latencies.size() - 1;
                return i;
            };
            p50_us = latencies[idx(0.50)] / 1000.0;
            p95_us = latencies[idx(0.95)] / 1000.0;
            p99_us = latencies[idx(0.99)] / 1000.0;
            p999_us = latencies[idx(0.999)] / 1000.0;
        }
    }

    argentum::bus::TopicMetrics metrics{};
    bus->get_metrics("market.ticks", &metrics);

    std::cout << "[Benchmark] Total ticks: " << total << "\n";
    std::cout << "[Benchmark] Publish time: " << publish_ms << " ms\n";
    std::cout << "[Benchmark] End-to-end time: " << end_to_end_ms << " ms\n";
    std::cout << "[Benchmark] Publish throughput: " << throughput << " ticks/sec\n";
    std::cout << "[Benchmark] Published: " << published.load() << "\n";
    std::cout << "[Benchmark] Dropped (publish): " << dropped.load() << "\n";
    std::cout << "[Benchmark] Bus drops: " << metrics.drops << "\n";
    std::cout << "[Benchmark] Latency p50: " << p50_us << " us\n";
    std::cout << "[Benchmark] Latency p95: " << p95_us << " us\n";
    std::cout << "[Benchmark] Latency p99: " << p99_us << " us\n";
    std::cout << "[Benchmark] Latency p99.9: " << p999_us << " us\n";

    return 0;
}
