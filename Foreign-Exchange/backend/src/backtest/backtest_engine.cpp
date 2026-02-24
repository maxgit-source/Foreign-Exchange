#include "backtest/backtest_engine.hpp"

#include "core/fixed_point.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <numeric>
#include <sstream>
#include <vector>

namespace argentum::backtest {

namespace {
bool parse_i64_json_field(const std::string& json, const std::string& key, int64_t* out) {
    if (!out) return false;
    const std::string marker = "\"" + key + "\":";
    const size_t pos = json.find(marker);
    if (pos == std::string::npos) return false;

    const size_t begin = pos + marker.size();
    size_t end = begin;
    if (end < json.size() && json[end] == '-') ++end;
    while (end < json.size() && json[end] >= '0' && json[end] <= '9') ++end;
    if (end == begin) return false;

    *out = static_cast<int64_t>(std::strtoll(json.substr(begin, end - begin).c_str(), nullptr, 10));
    return true;
}

std::string to_compact_symbol(const std::string& raw) {
    std::string out;
    out.reserve(raw.size());
    for (char c : raw) {
        if (c == '/' || c == '-' || c == '_' || c == ' ' || c == '.') continue;
        out.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(c))));
    }
    return out;
}
} // namespace

void BacktestEngine::load_data(const std::string& symbol, const std::string& start_date, const std::string& end_date) {
    (void)start_date;
    (void)end_date;

    history_.clear();
    trades_.clear();

    std::cout << "[Backtest] Loading persisted dataset for " << symbol << "..." << std::endl;
    const bool ticks_ok = load_ticks_from_csv("data/market_ticks.csv", symbol);
    const bool trades_ok = load_trades_from_journal("data/order_events.jsonl");

    if (!ticks_ok) {
        std::cout << "[Backtest] Warning: no tick CSV found; continuing with execution events only." << std::endl;
    }
    if (!trades_ok) {
        std::cout << "[Backtest] Warning: no event journal found; running with tick-only replay." << std::endl;
    }

    std::cout << "[Backtest] Loaded ticks=" << history_.size()
              << " trades=" << trades_.size() << std::endl;
}

bool BacktestEngine::load_ticks_from_csv(const std::string& csv_path, const std::string& symbol) {
    std::ifstream in(csv_path);
    if (!in.is_open()) return false;

    const std::string symbol_filter = to_compact_symbol(symbol);
    std::string line;
    bool loaded = false;

    while (std::getline(in, line)) {
        if (line.empty()) continue;
        if (line.rfind("timestamp_ns,", 0) == 0) continue;

        std::stringstream ss(line);
        std::string ts_s;
        std::string symbol_s;
        std::string price_s;
        std::string qty_s;
        std::string side_s;
        std::string source_s;

        if (!std::getline(ss, ts_s, ',')) continue;
        if (!std::getline(ss, symbol_s, ',')) continue;
        if (!std::getline(ss, price_s, ',')) continue;
        if (!std::getline(ss, qty_s, ',')) continue;
        if (!std::getline(ss, side_s, ',')) continue;
        if (!std::getline(ss, source_s, ',')) source_s.clear();

        if (!symbol_filter.empty() && to_compact_symbol(symbol_s) != symbol_filter) {
            continue;
        }

        MarketTick tick{};
        tick.timestamp_ns = static_cast<uint64_t>(std::strtoull(ts_s.c_str(), nullptr, 10));
        tick.price = std::strtod(price_s.c_str(), nullptr);
        tick.quantity = std::strtod(qty_s.c_str(), nullptr);
        tick.side = static_cast<uint8_t>(
            (!side_s.empty() && (side_s[0] == 'B' || side_s[0] == 'b')) ? SIDE_BUY : SIDE_SELL);
        std::strncpy(tick.symbol, symbol_s.c_str(), sizeof(tick.symbol) - 1);
        std::strncpy(tick.source, source_s.c_str(), sizeof(tick.source) - 1);

        if (tick.timestamp_ns == 0 || tick.price <= 0.0 || tick.quantity <= 0.0) {
            continue;
        }

        history_.push_back(tick);
        loaded = true;
    }

    std::sort(history_.begin(), history_.end(), [](const MarketTick& a, const MarketTick& b) {
        return a.timestamp_ns < b.timestamp_ns;
    });

    return loaded;
}

bool BacktestEngine::load_trades_from_journal(const std::string& journal_path) {
    std::ifstream in(journal_path);
    if (!in.is_open()) return false;

    std::string line;
    bool loaded = false;
    while (std::getline(in, line)) {
        if (line.empty()) continue;
        if (line.find("\"type\":\"trade_executed\"") == std::string::npos) continue;

        int64_t price_ticks = 0;
        int64_t quantity_lots = 0;
        int64_t side = 0;
        if (!parse_i64_json_field(line, "price_ticks", &price_ticks)) continue;
        if (!parse_i64_json_field(line, "quantity_lots", &quantity_lots)) continue;
        if (!parse_i64_json_field(line, "side", &side)) continue;
        if (price_ticks <= 0 || quantity_lots <= 0) continue;

        trades_.push_back(HistoricalTrade{
            price_ticks,
            quantity_lots,
            static_cast<uint8_t>(side)
        });
        loaded = true;
    }

    return loaded;
}

BacktestResult BacktestEngine::run(std::shared_ptr<analysis::Strategy> strategy) {
    if (!strategy) {
        return BacktestResult{0.0, 0, 0.0, 0.0};
    }

    std::cout << "[Backtest] Running persisted replay for strategy: " << strategy->get_name() << std::endl;

    for (const auto& tick : history_) {
        strategy->on_tick(tick);
    }

    if (history_.empty() && trades_.empty()) {
        return BacktestResult{0.0, 0, 0.0, 0.0};
    }

    double cash = initial_capital_;
    int64_t position_lots = 0;
    double mark_price = history_.empty() ? 0.0 : history_.back().price;
    double max_equity = initial_capital_;
    double max_drawdown = 0.0;
    std::vector<double> equity_path;
    equity_path.reserve(trades_.size());

    for (const auto& trade : trades_) {
        const double px = core::from_price_ticks(trade.price_ticks);
        const double qty = core::from_quantity_lots(trade.quantity_lots);

        if (trade.side == SIDE_BUY) {
            cash -= (px * qty);
            position_lots += trade.quantity_lots;
        } else if (trade.side == SIDE_SELL) {
            cash += (px * qty);
            position_lots -= trade.quantity_lots;
        }

        mark_price = px;
        const double equity = cash + (core::from_quantity_lots(position_lots) * mark_price);
        max_equity = std::max(max_equity, equity);

        if (max_equity > 0.0) {
            const double drawdown = (max_equity - equity) / max_equity;
            max_drawdown = std::max(max_drawdown, drawdown);
        }
        equity_path.push_back(equity);
    }

    const double final_equity = cash + (core::from_quantity_lots(position_lots) * mark_price);
    const double total_pnl = final_equity - initial_capital_;

    double sharpe = 0.0;
    if (equity_path.size() > 1) {
        std::vector<double> returns;
        returns.reserve(equity_path.size() - 1);
        for (size_t i = 1; i < equity_path.size(); ++i) {
            returns.push_back(equity_path[i] - equity_path[i - 1]);
        }

        const double mean = std::accumulate(returns.begin(), returns.end(), 0.0) /
            static_cast<double>(returns.size());
        double variance = 0.0;
        for (double r : returns) {
            const double d = r - mean;
            variance += d * d;
        }
        variance /= static_cast<double>(returns.size());
        const double stddev = std::sqrt(variance);
        if (stddev > 1e-12) {
            sharpe = (mean / stddev) * std::sqrt(252.0);
        }
    }

    return BacktestResult{
        total_pnl,
        trades_.size(),
        max_drawdown,
        sharpe
    };
}

} // namespace argentum::backtest
