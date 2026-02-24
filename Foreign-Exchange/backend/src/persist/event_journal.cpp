#include "persist/event_journal.hpp"

#include "core/fixed_point.hpp"
#include "core/time_utils.hpp"

#include <algorithm>
#include <cstdio>
#include <filesystem>
#include <sstream>

namespace argentum::persist {

namespace {
const char* kUnknownType = "unknown";

bool parse_u64_field(const std::string& json, const std::string& key, uint64_t* out) {
    if (!out) return false;
    const std::string marker = "\"" + key + "\":";
    const size_t pos = json.find(marker);
    if (pos == std::string::npos) return false;

    size_t begin = pos + marker.size();
    while (begin < json.size() && (json[begin] == ' ' || json[begin] == '\t')) ++begin;
    size_t end = begin;
    while (end < json.size() && json[end] >= '0' && json[end] <= '9') ++end;
    if (end == begin) return false;

    *out = static_cast<uint64_t>(std::strtoull(json.substr(begin, end - begin).c_str(), nullptr, 10));
    return true;
}

bool parse_i64_field(const std::string& json, const std::string& key, int64_t* out) {
    if (!out) return false;
    const std::string marker = "\"" + key + "\":";
    const size_t pos = json.find(marker);
    if (pos == std::string::npos) return false;

    size_t begin = pos + marker.size();
    while (begin < json.size() && (json[begin] == ' ' || json[begin] == '\t')) ++begin;
    size_t end = begin;
    if (end < json.size() && json[end] == '-') ++end;
    const size_t digits_begin = end;
    while (end < json.size() && json[end] >= '0' && json[end] <= '9') ++end;
    if (digits_begin == end) return false;

    *out = static_cast<int64_t>(std::strtoll(json.substr(begin, end - begin).c_str(), nullptr, 10));
    return true;
}

bool parse_i32_field(const std::string& json, const std::string& key, int32_t* out) {
    if (!out) return false;
    int64_t tmp = 0;
    if (!parse_i64_field(json, key, &tmp)) return false;
    *out = static_cast<int32_t>(tmp);
    return true;
}

bool parse_bool_field(const std::string& json, const std::string& key, bool* out) {
    if (!out) return false;
    const std::string marker = "\"" + key + "\":";
    const size_t pos = json.find(marker);
    if (pos == std::string::npos) return false;

    size_t begin = pos + marker.size();
    while (begin < json.size() && (json[begin] == ' ' || json[begin] == '\t')) ++begin;

    if (json.compare(begin, 4, "true") == 0) {
        *out = true;
        return true;
    }
    if (json.compare(begin, 5, "false") == 0) {
        *out = false;
        return true;
    }
    return false;
}

bool parse_string_field(const std::string& json, const std::string& key, std::string* out) {
    if (!out) return false;
    const std::string marker = "\"" + key + "\":\"";
    const size_t pos = json.find(marker);
    if (pos == std::string::npos) return false;

    const size_t begin = pos + marker.size();
    const size_t end = json.find('"', begin);
    if (end == std::string::npos) return false;

    *out = json.substr(begin, end - begin);
    return true;
}

uint8_t status_for_replay_resting() {
    return 1; // trading::OrderStatus::Resting
}

uint8_t status_for_replay_partially_filled() {
    return 2; // trading::OrderStatus::PartiallyFilled
}

uint8_t status_for_replay_filled() {
    return 3; // trading::OrderStatus::Filled
}

uint8_t status_for_replay_canceled() {
    return 4; // trading::OrderStatus::Canceled
}

uint8_t status_for_replay_rejected() {
    return 5; // trading::OrderStatus::Rejected
}

int64_t signed_notional_units(int64_t price_ticks, int64_t quantity_lots, uint8_t side) {
    if (price_ticks <= 0 || quantity_lots <= 0) return 0;
    int64_t units = core::to_notional_units(price_ticks, quantity_lots);
    if (side == SIDE_SELL) {
        units = -units;
    }
    return units;
}

void apply_fill_to_state(ReplayOrderState* state, int64_t fill_lots) {
    if (!state || fill_lots <= 0) return;

    const int64_t applied = std::min(fill_lots, std::max<int64_t>(0, state->remaining_lots));
    if (applied <= 0) return;

    state->filled_lots += applied;
    state->remaining_lots = std::max<int64_t>(0, state->remaining_lots - applied);
    if (state->remaining_lots == 0) {
        state->status = status_for_replay_filled();
    } else {
        state->status = status_for_replay_partially_filled();
    }
}

bool load_tail_stats(const std::string& path, uint64_t* out_last_seq, uint64_t* out_last_ts) {
    if (!out_last_seq || !out_last_ts) return false;
    *out_last_seq = 0;
    *out_last_ts = 0;

    std::ifstream in(path);
    if (!in.is_open()) return true;

    std::string line;
    uint64_t max_seq = 0;
    uint64_t max_ts = 0;
    while (std::getline(in, line)) {
        if (line.empty()) continue;
        uint64_t seq = 0;
        uint64_t ts = 0;
        if (parse_u64_field(line, "seq", &seq)) {
            max_seq = std::max(max_seq, seq);
        }
        if (parse_u64_field(line, "timestamp_ns", &ts)) {
            max_ts = std::max(max_ts, ts);
        }
    }
    *out_last_seq = max_seq;
    *out_last_ts = max_ts;
    return true;
}

} // namespace

EventJournal::EventJournal(std::string path) : path_(std::move(path)) {
    std::error_code ec;
    const std::filesystem::path fs_path(path_);
    if (!fs_path.parent_path().empty()) {
        std::filesystem::create_directories(fs_path.parent_path(), ec);
    }

    uint64_t last_seq = 0;
    uint64_t last_ts = 0;
    if (load_tail_stats(path_, &last_seq, &last_ts)) {
        next_seq_ = last_seq + 1;
        last_timestamp_ns_ = last_ts;
    }
}

EventJournal::~EventJournal() {
    flush();
}

bool EventJournal::append(const JournalEvent& event) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!file_.is_open()) {
        file_.open(path_, std::ios::out | std::ios::app);
        if (!file_.is_open()) return false;
    }

    JournalEvent to_write = event;
    if (to_write.seq == 0) {
        to_write.seq = next_seq_++;
    } else {
        next_seq_ = std::max(next_seq_, to_write.seq + 1);
    }
    if (to_write.timestamp_ns == 0) {
        to_write.timestamp_ns = core::unix_now_ns();
    }
    if (last_timestamp_ns_ != 0 && to_write.timestamp_ns <= last_timestamp_ns_) {
        to_write.timestamp_ns = last_timestamp_ns_ + 1;
    }
    last_timestamp_ns_ = to_write.timestamp_ns;

    file_ << "{\"seq\":" << to_write.seq
          << ",\"timestamp_ns\":" << to_write.timestamp_ns
          << ",\"type\":\"" << journal_event_type_to_string(to_write.type) << "\""
          << ",\"order_id\":" << to_write.order_id
          << ",\"related_order_id\":" << to_write.related_order_id
          << ",\"price_ticks\":" << to_write.price_ticks
          << ",\"quantity_lots\":" << to_write.quantity_lots
          << ",\"remaining_lots\":" << to_write.remaining_lots
          << ",\"reason_code\":" << to_write.reason_code
          << ",\"side\":" << static_cast<uint32_t>(to_write.side)
          << ",\"order_type\":" << static_cast<uint32_t>(to_write.order_type)
          << ",\"tif\":" << static_cast<uint32_t>(to_write.tif)
          << ",\"resting\":" << (to_write.resting ? "true" : "false")
          << "}\n";
    return file_.good();
}

void EventJournal::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (file_.is_open()) {
        file_.flush();
    }
}

const std::string& EventJournal::path() const {
    return path_;
}

const char* journal_event_type_to_string(JournalEventType type) {
    switch (type) {
        case JournalEventType::OrderAccepted: return "order_accepted";
        case JournalEventType::OrderRejected: return "order_rejected";
        case JournalEventType::TradeExecuted: return "trade_executed";
        case JournalEventType::OrderCanceled: return "order_canceled";
        case JournalEventType::OrderReplaced: return "order_replaced";
        case JournalEventType::GatewayRejected: return "gateway_rejected";
        default: return kUnknownType;
    }
}

bool journal_event_type_from_string(const std::string& raw, JournalEventType* out) {
    if (!out) return false;
    if (raw == "order_accepted") {
        *out = JournalEventType::OrderAccepted;
        return true;
    }
    if (raw == "order_rejected") {
        *out = JournalEventType::OrderRejected;
        return true;
    }
    if (raw == "trade_executed") {
        *out = JournalEventType::TradeExecuted;
        return true;
    }
    if (raw == "order_canceled") {
        *out = JournalEventType::OrderCanceled;
        return true;
    }
    if (raw == "order_replaced") {
        *out = JournalEventType::OrderReplaced;
        return true;
    }
    if (raw == "gateway_rejected") {
        *out = JournalEventType::GatewayRejected;
        return true;
    }
    return false;
}

bool EventReplayer::replay_file(const std::string& path, ReplaySummary* out_summary) {
    if (!out_summary) return false;

    std::ifstream in(path);
    if (!in.is_open()) return false;

    ReplaySummary summary{};
    uint64_t last_seq = 0;
    uint64_t last_ts = 0;
    std::unordered_map<uint64_t, int64_t> seen_fill_lots;
    std::string line;

    auto apply_fill = [&](uint64_t order_id, int64_t filled_lots) {
        if (order_id == 0 || filled_lots <= 0) return;

        seen_fill_lots[order_id] += filled_lots;
        auto it = summary.active_orders.find(order_id);
        if (it == summary.active_orders.end()) return;

        apply_fill_to_state(&it->second, filled_lots);
        summary.order_history[order_id] = it->second;
        if (it->second.remaining_lots <= 0) {
            summary.active_orders.erase(it);
        }
    };

    while (std::getline(in, line)) {
        if (line.empty()) continue;
        ++summary.total_events;

        JournalEvent event{};
        uint64_t seq = 0;
        uint64_t ts = 0;
        std::string type_raw;
        if (!parse_u64_field(line, "seq", &seq)) return false;
        if (!parse_u64_field(line, "timestamp_ns", &ts)) return false;
        if (!parse_string_field(line, "type", &type_raw)) return false;
        if (!journal_event_type_from_string(type_raw, &event.type)) return false;

        (void)parse_u64_field(line, "order_id", &event.order_id);
        (void)parse_u64_field(line, "related_order_id", &event.related_order_id);
        (void)parse_i64_field(line, "price_ticks", &event.price_ticks);
        (void)parse_i64_field(line, "quantity_lots", &event.quantity_lots);
        (void)parse_i64_field(line, "remaining_lots", &event.remaining_lots);
        (void)parse_i32_field(line, "reason_code", &event.reason_code);

        uint64_t tmp = 0;
        (void)parse_u64_field(line, "side", &tmp);
        event.side = static_cast<uint8_t>(tmp);
        tmp = 0;
        (void)parse_u64_field(line, "order_type", &tmp);
        event.order_type = static_cast<uint8_t>(tmp);
        tmp = 0;
        (void)parse_u64_field(line, "tif", &tmp);
        event.tif = static_cast<uint8_t>(tmp);
        (void)parse_bool_field(line, "resting", &event.resting);

        if (last_seq != 0 && seq <= last_seq) summary.monotonic_seq = false;
        if (last_ts != 0 && ts < last_ts) summary.monotonic_time = false;
        last_seq = seq;
        last_ts = ts;

        switch (event.type) {
            case JournalEventType::OrderAccepted: {
                ++summary.accepted;
                ReplayOrderState st{};
                st.price_ticks = event.price_ticks;
                st.initial_lots = std::max<int64_t>(0, event.quantity_lots);
                st.remaining_lots = std::max<int64_t>(0, event.remaining_lots);
                st.side = event.side;

                const int64_t seen_fills = std::max<int64_t>(0, seen_fill_lots[event.order_id]);
                st.filled_lots = std::min(st.initial_lots, seen_fills);

                if (event.resting && st.remaining_lots > 0) {
                    st.status = (st.filled_lots > 0)
                        ? status_for_replay_partially_filled()
                        : status_for_replay_resting();
                    summary.active_orders[event.order_id] = st;
                } else {
                    st.remaining_lots = 0;
                    if (st.filled_lots >= st.initial_lots && st.initial_lots > 0) {
                        st.status = status_for_replay_filled();
                    } else if (st.filled_lots > 0) {
                        st.status = status_for_replay_partially_filled();
                    } else {
                        st.status = status_for_replay_canceled();
                    }
                    summary.active_orders.erase(event.order_id);
                }

                summary.order_history[event.order_id] = st;
                break;
            }
            case JournalEventType::OrderRejected: {
                ++summary.rejected;
                ReplayOrderState st{};
                st.price_ticks = event.price_ticks;
                st.initial_lots = std::max<int64_t>(0, event.quantity_lots);
                st.remaining_lots = std::max<int64_t>(0, event.remaining_lots);
                st.filled_lots = 0;
                st.side = event.side;
                st.status = status_for_replay_rejected();
                summary.order_history[event.order_id] = st;
                summary.active_orders.erase(event.order_id);
                break;
            }
            case JournalEventType::GatewayRejected: {
                ++summary.gateway_rejected;
                break;
            }
            case JournalEventType::TradeExecuted: {
                ++summary.trades;
                apply_fill(event.order_id, event.quantity_lots);
                if (event.related_order_id != 0) {
                    apply_fill(event.related_order_id, event.quantity_lots);
                }

                summary.filled_exposure_units += signed_notional_units(
                    event.price_ticks,
                    event.quantity_lots,
                    event.side);
                summary.net_position_lots += (event.side == SIDE_BUY)
                    ? event.quantity_lots
                    : -event.quantity_lots;

                if (event.related_order_id != 0) {
                    const uint8_t maker_side = static_cast<uint8_t>(
                        (event.side == SIDE_BUY) ? SIDE_SELL : SIDE_BUY);
                    summary.filled_exposure_units += signed_notional_units(
                        event.price_ticks,
                        event.quantity_lots,
                        maker_side);
                    summary.net_position_lots += (maker_side == SIDE_BUY)
                        ? event.quantity_lots
                        : -event.quantity_lots;
                }
                break;
            }
            case JournalEventType::OrderCanceled: {
                ++summary.canceled;
                auto it = summary.order_history.find(event.order_id);
                if (it != summary.order_history.end()) {
                    it->second.remaining_lots = 0;
                    it->second.status = status_for_replay_canceled();
                    summary.order_history[event.order_id] = it->second;
                } else {
                    ReplayOrderState st{};
                    st.price_ticks = event.price_ticks;
                    st.side = event.side;
                    st.status = status_for_replay_canceled();
                    summary.order_history[event.order_id] = st;
                }
                summary.active_orders.erase(event.order_id);
                break;
            }
            case JournalEventType::OrderReplaced: {
                ++summary.replaced;
                ReplayOrderState st{};
                auto it = summary.order_history.find(event.order_id);
                if (it != summary.order_history.end()) {
                    st = it->second;
                }

                st.price_ticks = event.price_ticks;
                if (event.side == SIDE_BUY || event.side == SIDE_SELL) {
                    st.side = event.side;
                }
                st.remaining_lots = std::max<int64_t>(0, event.remaining_lots);
                if (event.quantity_lots > 0) {
                    st.initial_lots = std::max<int64_t>(event.quantity_lots, st.filled_lots + st.remaining_lots);
                } else {
                    st.initial_lots = std::max<int64_t>(st.initial_lots, st.filled_lots + st.remaining_lots);
                }

                if (st.remaining_lots > 0) {
                    st.status = (st.filled_lots > 0)
                        ? status_for_replay_partially_filled()
                        : status_for_replay_resting();
                    summary.active_orders[event.order_id] = st;
                } else {
                    st.status = (st.filled_lots > 0)
                        ? status_for_replay_filled()
                        : status_for_replay_canceled();
                    summary.active_orders.erase(event.order_id);
                }
                summary.order_history[event.order_id] = st;
                break;
            }
            default:
                break;
        }
    }

    int64_t committed_units = 0;
    for (const auto& [_, st] : summary.active_orders) {
        committed_units += signed_notional_units(st.price_ticks, st.remaining_lots, st.side);
    }
    summary.committed_exposure_units = committed_units;

    *out_summary = std::move(summary);
    return true;
}

} // namespace argentum::persist
