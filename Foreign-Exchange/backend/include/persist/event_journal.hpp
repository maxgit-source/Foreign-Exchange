#pragma once

#include "core/types.h"

#include <cstdint>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace argentum::persist {

enum class JournalEventType : uint8_t {
    OrderAccepted = 1,
    OrderRejected = 2,
    TradeExecuted = 3,
    OrderCanceled = 4,
    OrderReplaced = 5,
    GatewayRejected = 6
};

struct JournalEvent {
    uint64_t seq = 0;
    uint64_t timestamp_ns = 0;
    JournalEventType type = JournalEventType::OrderAccepted;
    uint64_t order_id = 0;
    uint64_t related_order_id = 0;
    int64_t price_ticks = 0;
    int64_t quantity_lots = 0;
    int64_t remaining_lots = 0;
    int32_t reason_code = 0;
    uint8_t side = 0;
    uint8_t order_type = 0;
    uint8_t tif = 0;
    bool resting = false;
};

class EventJournal {
public:
    explicit EventJournal(std::string path = "data/order_events.jsonl");
    ~EventJournal();

    bool append(const JournalEvent& event);
    void flush();
    const std::string& path() const;

private:
    std::string path_;
    uint64_t next_seq_ = 1;
    uint64_t last_timestamp_ns_ = 0;
    std::ofstream file_;
    mutable std::mutex mutex_;
};

struct ReplayOrderState {
    int64_t price_ticks = 0;
    int64_t initial_lots = 0;
    int64_t remaining_lots = 0;
    int64_t filled_lots = 0;
    uint8_t side = 0;
    uint8_t status = 0;
};

struct ReplaySummary {
    uint64_t total_events = 0;
    uint64_t accepted = 0;
    uint64_t rejected = 0;
    uint64_t gateway_rejected = 0;
    uint64_t trades = 0;
    uint64_t canceled = 0;
    uint64_t replaced = 0;
    bool monotonic_seq = true;
    bool monotonic_time = true;
    int64_t committed_exposure_units = 0;
    int64_t filled_exposure_units = 0;
    int64_t net_position_lots = 0;
    std::unordered_map<uint64_t, ReplayOrderState> active_orders;
    std::unordered_map<uint64_t, ReplayOrderState> order_history;
};

const char* journal_event_type_to_string(JournalEventType type);
bool journal_event_type_from_string(const std::string& raw, JournalEventType* out);

class EventReplayer {
public:
    static bool replay_file(const std::string& path, ReplaySummary* out_summary);
};

} // namespace argentum::persist
