#include "api/market_gateway.hpp"

#include "codec/market_tick_codec.hpp"
#include "core/time_utils.hpp"

#include <cctype>
#include <sstream>

namespace argentum::api {

namespace {
std::string json_escape(const char* raw) {
    if (!raw) return "";
    std::string out;
    for (const char* p = raw; *p != '\0'; ++p) {
        const char c = *p;
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out.push_back(c); break;
        }
    }
    return out;
}
} // namespace

MarketGatewayService::MarketGatewayService(
    std::shared_ptr<bus::MessageBus> bus,
    std::string market_topic,
    GatewaySecurityConfig security)
    : bus_(std::move(bus)),
      market_topic_(std::move(market_topic)),
      security_(std::move(security)) {
    if (!security_.api_token.empty()) {
        const uint64_t expiry_ns = security_.default_token_ttl_ms == 0
            ? 0
            : (core::unix_now_ns() + security_.default_token_ttl_ms * 1'000'000ULL);
        token_expiry_ns_[security_.api_token] = expiry_ns;
    }
}

void MarketGatewayService::start() {
    bool expected = false;
    if (!started_.compare_exchange_strong(expected, true, std::memory_order_relaxed)) {
        return;
    }
    if (!bus_) {
        started_.store(false, std::memory_order_relaxed);
        return;
    }

    bus_->subscribe(market_topic_, [this](const void* data, size_t size) { on_market_message(data, size); });
}

void MarketGatewayService::stop() {
    started_.store(false, std::memory_order_relaxed);
}

void MarketGatewayService::on_market_message(const void* data, size_t size) {
    if (!started_.load(std::memory_order_relaxed)) return;
    ticks_received_.fetch_add(1, std::memory_order_relaxed);

    MarketTick tick{};
    if (codec::decode_market_tick(data, size, &tick) != ARGENTUM_OK) {
        decode_errors_.fetch_add(1, std::memory_order_relaxed);
        return;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    latest_ticks_[normalize_key(tick.symbol)] = tick;
    ticks_decoded_.fetch_add(1, std::memory_order_relaxed);
}

bool MarketGatewayService::get_latest_tick(const std::string& symbol, MarketTick* out) const {
    if (!out) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = latest_ticks_.find(normalize_key(symbol.c_str()));
    if (it == latest_ticks_.end()) return false;
    *out = it->second;
    return true;
}

std::string MarketGatewayService::latest_tick_json(const std::string& symbol) const {
    MarketTick tick{};
    if (!get_latest_tick(symbol, &tick)) {
        return "{}";
    }
    return to_json(tick);
}

std::string MarketGatewayService::health_json() const {
    return to_json(metrics(), started_.load(std::memory_order_relaxed));
}

bool MarketGatewayService::consume_rate_limit(const std::string& key) {
    const auto now = std::chrono::steady_clock::now();
    const auto window = std::chrono::milliseconds(security_.rate_limit.window_ms == 0
        ? 1
        : security_.rate_limit.window_ms);

    auto& state = rate_windows_[key];
    if (state.window_start.time_since_epoch().count() == 0) {
        state.window_start = now;
    }
    if (now - state.window_start >= window) {
        state.window_start = now;
        state.requests = 0;
    }

    if (security_.rate_limit.max_requests == 0) {
        return false;
    }
    if (state.requests >= security_.rate_limit.max_requests) {
        return false;
    }
    ++state.requests;
    return true;
}

bool MarketGatewayService::token_allowed_unlocked(const std::string& token, uint64_t now_ns) {
    if (token_expiry_ns_.empty()) {
        return true;
    }

    auto it = token_expiry_ns_.find(token);
    if (it == token_expiry_ns_.end()) {
        return false;
    }

    const uint64_t expiry_ns = it->second;
    if (expiry_ns != 0 && now_ns > expiry_ns) {
        token_expiry_ns_.erase(it);
        return false;
    }
    return true;
}

bool MarketGatewayService::authorize_request(
    const std::string& provided_token,
    GatewayRejectReason* reason,
    bool count_as_order_request) {
    if (count_as_order_request) {
        order_requests_.fetch_add(1, std::memory_order_relaxed);
    }

    {
        std::lock_guard<std::mutex> lock(mutex_);
        const uint64_t now_ns = core::unix_now_ns();
        if (!token_allowed_unlocked(provided_token, now_ns)) {
            auth_failures_.fetch_add(1, std::memory_order_relaxed);
            if (count_as_order_request) {
                order_rejected_.fetch_add(1, std::memory_order_relaxed);
            }
            if (reason) *reason = GatewayRejectReason::Unauthorized;
            return false;
        }

        const std::string key = provided_token.empty() ? "anonymous" : provided_token;
        if (!consume_rate_limit(key)) {
            rate_limited_.fetch_add(1, std::memory_order_relaxed);
            if (count_as_order_request) {
                order_rejected_.fetch_add(1, std::memory_order_relaxed);
            }
            if (reason) *reason = GatewayRejectReason::RateLimited;
            return false;
        }
    }

    if (reason) *reason = GatewayRejectReason::None;
    return true;
}

bool MarketGatewayService::add_token(const std::string& token, uint64_t ttl_ms) {
    if (token.empty()) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    const uint64_t expiry_ns = ttl_ms == 0
        ? 0
        : (core::unix_now_ns() + ttl_ms * 1'000'000ULL);
    token_expiry_ns_[token] = expiry_ns;
    return true;
}

bool MarketGatewayService::revoke_token(const std::string& token) {
    if (token.empty()) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    return token_expiry_ns_.erase(token) > 0;
}

bool MarketGatewayService::rotate_token(const std::string& old_token, const std::string& new_token, uint64_t ttl_ms) {
    if (old_token.empty() || new_token.empty()) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = token_expiry_ns_.find(old_token);
    if (it == token_expiry_ns_.end()) return false;
    token_expiry_ns_.erase(it);
    const uint64_t expiry_ns = ttl_ms == 0
        ? 0
        : (core::unix_now_ns() + ttl_ms * 1'000'000ULL);
    token_expiry_ns_[new_token] = expiry_ns;
    return true;
}

void MarketGatewayService::record_order_result(bool accepted) {
    if (accepted) {
        order_accepted_.fetch_add(1, std::memory_order_relaxed);
    } else {
        order_rejected_.fetch_add(1, std::memory_order_relaxed);
    }
}

GatewayMetrics MarketGatewayService::metrics() const {
    GatewayMetrics out{};
    out.ticks_received = ticks_received_.load(std::memory_order_relaxed);
    out.ticks_decoded = ticks_decoded_.load(std::memory_order_relaxed);
    out.decode_errors = decode_errors_.load(std::memory_order_relaxed);
    out.order_requests = order_requests_.load(std::memory_order_relaxed);
    out.order_accepted = order_accepted_.load(std::memory_order_relaxed);
    out.order_rejected = order_rejected_.load(std::memory_order_relaxed);
    out.auth_failures = auth_failures_.load(std::memory_order_relaxed);
    out.rate_limited = rate_limited_.load(std::memory_order_relaxed);

    std::lock_guard<std::mutex> lock(mutex_);
    out.tracked_symbols = latest_ticks_.size();
    return out;
}

void MarketGatewayService::reset_metrics() {
    ticks_received_.store(0, std::memory_order_relaxed);
    ticks_decoded_.store(0, std::memory_order_relaxed);
    decode_errors_.store(0, std::memory_order_relaxed);
    order_requests_.store(0, std::memory_order_relaxed);
    order_accepted_.store(0, std::memory_order_relaxed);
    order_rejected_.store(0, std::memory_order_relaxed);
    auth_failures_.store(0, std::memory_order_relaxed);
    rate_limited_.store(0, std::memory_order_relaxed);
}

std::string MarketGatewayService::normalize_key(const char* symbol) {
    std::string key;
    if (!symbol) return key;
    for (const char* p = symbol; *p != '\0'; ++p) {
        if (*p == '/' || *p == '-' || *p == '_' || *p == ' ') continue;
        key.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(*p))));
    }
    return key;
}

OrderAck submit_order(trading::OrderManager& manager, const Order& order) {
    trading::OrderSubmissionResult result = manager.submit_order(order);
    return {
        order.order_id,
        result.accepted,
        result.resting,
        result.filled_quantity,
        result.remaining_quantity,
        result.reject_reason,
        GatewayRejectReason::None
    };
}

OrderAck submit_order(
    MarketGatewayService& gateway,
    trading::OrderManager& manager,
    const Order& order,
    const std::string& api_token) {
    GatewayRejectReason gateway_reason = GatewayRejectReason::None;
    if (!gateway.authorize_request(api_token, &gateway_reason)) {
        return {
            order.order_id,
            false,
            false,
            0.0,
            order.quantity,
            trading::OrderRejectReason::None,
            gateway_reason
        };
    }

    OrderAck ack = submit_order(manager, order);
    gateway.record_order_result(ack.accepted);
    return ack;
}

const char* reject_reason_to_string(trading::OrderRejectReason reason) {
    switch (reason) {
        case trading::OrderRejectReason::None: return "none";
        case trading::OrderRejectReason::InvalidOrder: return "invalid_order";
        case trading::OrderRejectReason::DuplicateOrderId: return "duplicate_order_id";
        case trading::OrderRejectReason::RiskRejected: return "risk_rejected";
        case trading::OrderRejectReason::InternalError: return "internal_error";
        default: return "unknown";
    }
}

const char* gateway_reject_reason_to_string(GatewayRejectReason reason) {
    switch (reason) {
        case GatewayRejectReason::None: return "none";
        case GatewayRejectReason::Unauthorized: return "unauthorized";
        case GatewayRejectReason::RateLimited: return "rate_limited";
        default: return "unknown";
    }
}

std::string to_json(const MarketTick& tick) {
    std::ostringstream os;
    os.setf(std::ios::fixed);
    os.precision(10);
    os << "{\"event\":\"tick\""
       << ",\"symbol\":\"" << json_escape(tick.symbol) << "\""
       << ",\"timestamp_ns\":" << tick.timestamp_ns
       << ",\"price\":" << tick.price
       << ",\"quantity\":" << tick.quantity
       << ",\"side\":\"" << (tick.side == SIDE_BUY ? "buy" : "sell") << "\""
       << ",\"source\":\"" << json_escape(tick.source) << "\"}";
    return os.str();
}

std::string to_json(const OrderAck& ack) {
    std::ostringstream os;
    os.setf(std::ios::fixed);
    os.precision(10);
    os << "{\"event\":\"order_ack\""
       << ",\"order_id\":" << ack.order_id
       << ",\"accepted\":" << (ack.accepted ? "true" : "false")
       << ",\"resting\":" << (ack.resting ? "true" : "false")
       << ",\"filled_quantity\":" << ack.filled_quantity
       << ",\"remaining_quantity\":" << ack.remaining_quantity
       << ",\"reject_reason\":\"" << reject_reason_to_string(ack.reject_reason) << "\""
       << ",\"gateway_reject_reason\":\"" << gateway_reject_reason_to_string(ack.gateway_reject_reason) << "\"}";
    return os.str();
}

std::string to_json(const GatewayMetrics& metrics, bool running) {
    const char* status = "down";
    if (running) {
        status = (metrics.decode_errors > 0) ? "degraded" : "ok";
    }
    std::ostringstream os;
    os << "{\"status\":\"" << status << "\""
       << ",\"timestamp_ns\":" << argentum::core::unix_now_ns()
       << ",\"ticks_received\":" << metrics.ticks_received
       << ",\"ticks_decoded\":" << metrics.ticks_decoded
       << ",\"decode_errors\":" << metrics.decode_errors
       << ",\"order_requests\":" << metrics.order_requests
       << ",\"order_accepted\":" << metrics.order_accepted
       << ",\"order_rejected\":" << metrics.order_rejected
       << ",\"auth_failures\":" << metrics.auth_failures
       << ",\"rate_limited\":" << metrics.rate_limited
       << ",\"tracked_symbols\":" << metrics.tracked_symbols
       << "}";
    return os.str();
}

} // namespace argentum::api
