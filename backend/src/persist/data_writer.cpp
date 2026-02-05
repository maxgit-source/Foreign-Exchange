#include "persist/data_writer.hpp"

#include <chrono>
#include <ctime>
#include <fstream>
#include <filesystem>
#include <cstdio>

#ifdef ARGENTUM_USE_LIBPQ
#include <libpq-fe.h>
#endif

namespace argentum::persist {

DataWriterService::DataWriterService(std::string connection_string)
    : connection_string_(std::move(connection_string)) {}

DataWriterService::~DataWriterService() {
    stop();
}

void DataWriterService::start() {
    if (running_.exchange(true)) return;
    worker_ = std::thread(&DataWriterService::worker_loop, this);
}

void DataWriterService::stop() {
    if (!running_.exchange(false)) return;
    cv_.notify_all();
    if (worker_.joinable()) {
        worker_.join();
    }
}

void DataWriterService::set_max_batch(size_t max_batch) {
    max_batch_ = max_batch;
}

void DataWriterService::set_flush_interval_ms(uint32_t ms) {
    flush_interval_ms_ = ms;
}

void DataWriterService::enqueue(const MarketTick& tick) {
    std::lock_guard lock(mutex_);
    queue_.push_back(tick);
    if (queue_.size() >= max_batch_) {
        cv_.notify_one();
    }
}

void DataWriterService::enqueue_batch(const MarketTick* ticks, size_t count) {
    if (!ticks || count == 0) return;
    std::lock_guard lock(mutex_);
    queue_.insert(queue_.end(), ticks, ticks + count);
    if (queue_.size() >= max_batch_) {
        cv_.notify_one();
    }
}

void DataWriterService::worker_loop() {
    using namespace std::chrono;
    std::vector<MarketTick> batch;

    while (running_) {
        {
            std::unique_lock lock(mutex_);
            cv_.wait_for(lock, milliseconds(flush_interval_ms_), [&] {
                return !queue_.empty() || !running_;
            });
            if (!running_ && queue_.empty()) break;
            batch.swap(queue_);
        }

        if (!batch.empty()) {
            flush_batch(batch);
            batch.clear();
        }
    }
}

void DataWriterService::flush_batch(std::vector<MarketTick>& batch) {
#ifdef ARGENTUM_USE_LIBPQ
    PGconn* conn = PQconnectdb(connection_string_.c_str());
    if (!conn || PQstatus(conn) != CONNECTION_OK) {
        if (conn) PQfinish(conn);
        return;
    }
    PGresult* res = PQexec(conn,
        "COPY market_ticks (time, symbol, price, volume, side, source) "
        "FROM STDIN WITH (FORMAT csv)");
    if (!res || PQresultStatus(res) != PGRES_COPY_IN) {
        if (res) PQclear(res);
        PQfinish(conn);
        return;
    }
    PQclear(res);

    auto format_timestamp = [](uint64_t ts_ns, char* out, size_t out_len) {
        time_t secs = (time_t)(ts_ns / 1000000000ULL);
        uint32_t ns = (uint32_t)(ts_ns % 1000000000ULL);
        std::tm tm{};
#ifdef _WIN32
        gmtime_s(&tm, &secs);
#else
        gmtime_r(&secs, &tm);
#endif
        char base[32];
        strftime(base, sizeof(base), "%Y-%m-%d %H:%M:%S", &tm);
        snprintf(out, out_len, "%s.%09u+00", base, ns);
    };

    char line[320];
    char ts_buf[64];
    for (const auto& tick : batch) {
        format_timestamp(tick.timestamp_ns, ts_buf, sizeof(ts_buf));
        int written = snprintf(line, sizeof(line),
            "%s,%s,%.10f,%.10f,%c,%s\n",
            ts_buf,
            tick.symbol,
            tick.price,
            tick.quantity,
            tick.side == SIDE_BUY ? 'B' : 'S',
            tick.source);
        if (written > 0) {
            PQputCopyData(conn, line, written);
        }
    }
    PQputCopyEnd(conn, NULL);
    while ((res = PQgetResult(conn)) != NULL) {
        PQclear(res);
    }
    PQfinish(conn);
#else
    std::filesystem::create_directories("data");
    std::ofstream file("data/market_ticks.csv", std::ios::app | std::ios::binary);
    if (!file.is_open()) return;

    for (const auto& tick : batch) {
        file << tick.timestamp_ns << ','
             << tick.symbol << ','
             << tick.price << ','
             << tick.quantity << ','
             << (tick.side == SIDE_BUY ? 'B' : 'S') << ','
             << tick.source << '\n';
    }
#endif
}

} // namespace argentum::persist
