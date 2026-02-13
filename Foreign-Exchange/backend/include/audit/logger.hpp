#pragma once

#include "core/time_utils.hpp"

#include <string>
#include <iostream>
#include <fstream>
#include <mutex>
#include <deque>
#include <condition_variable>
#include <thread>
#include <atomic>

namespace argentum::audit {

enum class LogLevel { INFO, WARN, ERR, AUDIT };

/**
 * @class Logger
 * @brief Thread-safe audit logger.
 * In prod: Use ring-buffer and async writing thread to avoid disk I/O blocking trading path.
 */
class Logger {
public:
    static Logger& instance() {
        static Logger instance;
        return instance;
    }

    void log(LogLevel level, const std::string& message) {
        LogEntry entry;
        entry.level = level;
        entry.timestamp_ns = argentum::core::unix_now_ns();
        entry.message = message;

        {
            std::lock_guard<std::mutex> lock(mtx_);
            if (queue_.size() >= queue_capacity_) {
                dropped_.fetch_add(1, std::memory_order_relaxed);
                return;
            }
            queue_.push_back(std::move(entry));
        }
        cv_.notify_one();
    }

    void set_queue_capacity(size_t capacity) {
        std::lock_guard<std::mutex> lock(mtx_);
        queue_capacity_ = capacity;
    }

    uint64_t dropped_count() const {
        return dropped_.load(std::memory_order_relaxed);
    }

private:
    struct LogEntry {
        LogLevel level;
        uint64_t timestamp_ns;
        std::string message;
    };

    Logger() {
        file_.open("audit.log", std::ios::app);
        worker_ = std::thread(&Logger::worker_loop, this);
    }

    ~Logger() {
        running_.store(false, std::memory_order_relaxed);
        cv_.notify_all();
        if (worker_.joinable()) {
            worker_.join();
        }
        if (file_.is_open()) file_.close();
    }

    void worker_loop() {
        for (;;) {
            LogEntry entry;
            {
                std::unique_lock<std::mutex> lock(mtx_);
                cv_.wait(lock, [&] {
                    return !running_.load(std::memory_order_relaxed) || !queue_.empty();
                });
                if (!running_.load(std::memory_order_relaxed) && queue_.empty()) {
                    break;
                }
                entry = std::move(queue_.front());
                queue_.pop_front();
            }

            char ts_buf[64];
            argentum::core::format_utc(entry.timestamp_ns, ts_buf, sizeof(ts_buf));
            std::string prefix = (entry.level == LogLevel::AUDIT) ? "[AUDIT] " : "[LOG] ";
            std::string line = std::string(ts_buf) + " " + prefix + entry.message;

            std::cout << line << std::endl;
            if (file_.is_open()) {
                file_ << line << "\n";
            }
        }
    }

    std::ofstream file_;
    mutable std::mutex mtx_;
    std::condition_variable cv_;
    std::deque<LogEntry> queue_;
    size_t queue_capacity_ = 4096;
    std::atomic<uint64_t> dropped_{0};
    std::atomic<bool> running_{true};
    std::thread worker_;
};

} // namespace argentum::audit
