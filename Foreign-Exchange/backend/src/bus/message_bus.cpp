#include "bus/message_bus.hpp"

#include "core/time_utils.hpp"

#include <unordered_map>
#include <shared_mutex>
#include <mutex>
#include <deque>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <chrono>
#include <cstring>

namespace argentum::bus {

class InprocMessageBus final : public MessageBus {
public:
    explicit InprocMessageBus(InprocBusConfig config)
        : config_(config) {
        if (config_.queue_capacity == 0) {
            config_.queue_capacity = 1;
        }
    }

    ~InprocMessageBus() override {
        shutdown();
    }

    void connect(const std::string& endpoint, bool is_publisher) override {
        endpoint_ = endpoint;
        is_publisher_ = is_publisher;
    }

    ArgentumStatus publish(const std::string& topic, const void* data, size_t size) override {
        if (!data || size == 0) return ARGENTUM_ERR_INVALID;

        uint64_t start_ns = argentum::core::now_ns();
        TopicState* state = get_or_create_topic(topic);
        if (!state) return ARGENTUM_ERR_NOMEM;

        std::unique_lock lock(state->mutex);
        if (!state->running) {
            return ARGENTUM_ERR_INVALID;
        }

        if (state->queue.size() >= config_.queue_capacity) {
            state->metrics.backpressure_hits.fetch_add(1, std::memory_order_relaxed);
            switch (config_.policy) {
                case BackpressurePolicy::DropNewest: {
                    state->metrics.drops.fetch_add(1, std::memory_order_relaxed);
                    update_publish_latency(state, start_ns);
                    return ARGENTUM_ERR_TIMEOUT;
                }
                case BackpressurePolicy::DropOldest: {
                    if (!state->queue.empty()) {
                        state->queue.pop_front();
                        state->metrics.drops.fetch_add(1, std::memory_order_relaxed);
                        state->metrics.queue_depth.fetch_sub(1, std::memory_order_relaxed);
                    }
                    break;
                }
                case BackpressurePolicy::Block: {
                    if (config_.consumer_threads == 0) {
                        state->metrics.drops.fetch_add(1, std::memory_order_relaxed);
                        update_publish_latency(state, start_ns);
                        return ARGENTUM_ERR_TIMEOUT;
                    }
                    if (config_.block_timeout_ms == 0) {
                        state->cv_space.wait(lock, [&] {
                            return !state->running || state->queue.size() < config_.queue_capacity;
                        });
                    } else {
                        state->cv_space.wait_for(lock,
                            std::chrono::milliseconds(config_.block_timeout_ms),
                            [&] { return !state->running || state->queue.size() < config_.queue_capacity; });
                    }
                    if (!state->running || state->queue.size() >= config_.queue_capacity) {
                        update_publish_latency(state, start_ns);
                        return ARGENTUM_ERR_TIMEOUT;
                    }
                    break;
                }
            }
        }

        Message msg;
        msg.data.resize(size);
        std::memcpy(msg.data.data(), data, size);
        state->queue.push_back(std::move(msg));
        state->metrics.queue_depth.fetch_add(1, std::memory_order_relaxed);
        state->metrics.published.fetch_add(1, std::memory_order_relaxed);
        state->cv_data.notify_one();
        update_publish_latency(state, start_ns);
        return ARGENTUM_OK;
    }

    void subscribe(const std::string& topic, std::function<void(const void* data, size_t size)> callback) override {
        TopicState* state = get_or_create_topic(topic);
        if (!state) return;
        std::unique_lock lock(state->mutex);
        state->subscribers.push_back(std::move(callback));
        if (state->running && !state->consumers_started) {
            start_consumers(state);
        }
    }

    bool get_metrics(const std::string& topic, TopicMetrics* out) const override {
        if (!out) return false;
        std::shared_lock lock(mutex_);
        auto it = topics_.find(topic);
        if (it == topics_.end()) return false;
        const TopicMetricsInternal& metrics = it->second->metrics;
        uint64_t published = metrics.published.load(std::memory_order_relaxed);
        uint64_t total_latency = metrics.publish_latency_ns_total.load(std::memory_order_relaxed);
        out->queue_depth = metrics.queue_depth.load(std::memory_order_relaxed);
        out->drops = metrics.drops.load(std::memory_order_relaxed);
        out->backpressure_hits = metrics.backpressure_hits.load(std::memory_order_relaxed);
        out->published = published;
        out->publish_latency_ns_avg = (published == 0) ? 0 : (total_latency / published);
        out->publish_latency_ns_max = metrics.publish_latency_ns_max.load(std::memory_order_relaxed);
        return true;
    }

private:
    struct Message {
        std::vector<uint8_t> data;
    };

    struct TopicMetricsInternal {
        std::atomic<uint64_t> queue_depth{0};
        std::atomic<uint64_t> drops{0};
        std::atomic<uint64_t> backpressure_hits{0};
        std::atomic<uint64_t> published{0};
        std::atomic<uint64_t> publish_latency_ns_total{0};
        std::atomic<uint64_t> publish_latency_ns_max{0};
    };

    struct TopicState {
        std::mutex mutex;
        std::condition_variable cv_data;
        std::condition_variable cv_space;
        std::deque<Message> queue;
        std::vector<std::function<void(const void*, size_t)>> subscribers;
        std::vector<std::thread> workers;
        TopicMetricsInternal metrics;
        bool running = false;
        bool consumers_started = false;
    };

    TopicState* get_or_create_topic(const std::string& topic) {
        {
            std::shared_lock lock(mutex_);
            auto it = topics_.find(topic);
            if (it != topics_.end()) {
                return it->second.get();
            }
        }
        std::unique_lock lock(mutex_);
        auto it = topics_.find(topic);
        if (it != topics_.end()) {
            return it->second.get();
        }
        auto state = std::make_unique<TopicState>();
        state->running = true;
        TopicState* ptr = state.get();
        topics_[topic] = std::move(state);
        return ptr;
    }

    void start_consumers(TopicState* state) {
        if (!state) return;
        if (config_.consumer_threads == 0) return;
        if (state->subscribers.empty()) return;
        if (!state->consumers_started) {
            uint32_t threads = config_.consumer_threads;
            for (uint32_t i = 0; i < threads; ++i) {
                state->workers.emplace_back([this, state] { consumer_loop(state); });
            }
            state->consumers_started = true;
        }
    }

    void consumer_loop(TopicState* state) {
        for (;;) {
            Message msg;
            std::vector<std::function<void(const void*, size_t)>> callbacks;
            {
                std::unique_lock lock(state->mutex);
                state->cv_data.wait(lock, [&] {
                    return !state->running || !state->queue.empty();
                });
                if (!state->running && state->queue.empty()) {
                    break;
                }
                msg = std::move(state->queue.front());
                state->queue.pop_front();
                state->metrics.queue_depth.fetch_sub(1, std::memory_order_relaxed);
                state->cv_space.notify_one();
                callbacks = state->subscribers;
            }

            for (auto& cb : callbacks) {
                cb(msg.data.data(), msg.data.size());
            }
        }
    }

    void update_publish_latency(TopicState* state, uint64_t start_ns) {
        if (!state) return;
        uint64_t elapsed = argentum::core::now_ns() - start_ns;
        state->metrics.publish_latency_ns_total.fetch_add(elapsed, std::memory_order_relaxed);
        uint64_t prev = state->metrics.publish_latency_ns_max.load(std::memory_order_relaxed);
        while (elapsed > prev &&
               !state->metrics.publish_latency_ns_max.compare_exchange_weak(prev, elapsed, std::memory_order_relaxed)) {
        }
    }

    void shutdown() {
        std::unique_lock lock(mutex_);
        for (auto& [_, state] : topics_) {
            {
                std::unique_lock tlock(state->mutex);
                state->running = false;
                state->cv_data.notify_all();
                state->cv_space.notify_all();
            }
            for (auto& worker : state->workers) {
                if (worker.joinable()) worker.join();
            }
            state->workers.clear();
            state->consumers_started = false;
        }
    }

private:
    std::string endpoint_;
    bool is_publisher_ = false;
    InprocBusConfig config_;
    std::unordered_map<std::string, std::unique_ptr<TopicState>> topics_;
    mutable std::shared_mutex mutex_;
};

std::shared_ptr<MessageBus> create_inproc_bus(const InprocBusConfig& config) {
    return std::make_shared<InprocMessageBus>(config);
}

std::shared_ptr<MessageBus> create_inproc_bus() {
    return std::make_shared<InprocMessageBus>(InprocBusConfig{});
}

} // namespace argentum::bus
