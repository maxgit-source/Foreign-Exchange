#pragma once

#include "core/types.h"

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace argentum::persist {

class DataWriterService {
public:
    explicit DataWriterService(std::string connection_string = {});
    ~DataWriterService();

    void start();
    void stop();

    void enqueue(const MarketTick& tick);
    void enqueue_batch(const MarketTick* ticks, size_t count);

    void set_max_batch(size_t max_batch);
    void set_flush_interval_ms(uint32_t ms);

private:
    void worker_loop();
    void flush_batch(std::vector<MarketTick>& batch);

    std::string connection_string_;
    std::mutex mutex_;
    std::condition_variable cv_;
    std::vector<MarketTick> queue_;
    size_t max_batch_ = 1024;
    uint32_t flush_interval_ms_ = 100;
    std::atomic<bool> running_{false};
    std::thread worker_;
};

} // namespace argentum::persist
