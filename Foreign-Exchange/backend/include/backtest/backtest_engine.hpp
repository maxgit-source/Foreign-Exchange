#pragma once

#include "core/types.h"
#include "analysis/strategy.hpp"
#include <vector>
#include <string>
#include <functional>
#include <memory>

namespace argentum::backtest {

struct BacktestResult {
    double total_pnl;
    size_t trades_count;
    double max_drawdown;
    double sharpe_ratio;
};

/**
 * @class BacktestEngine
 * @brief Replays historical data to evaluate strategies.
 */
class BacktestEngine {
public:
    BacktestEngine() = default;

    /**
     * @brief Loads historical data (Mock implementation).
     * In prod, this would read from TimescaleDB or binary files.
     */
    void load_data(const std::string& symbol, const std::string& start_date, const std::string& end_date);
    bool load_ticks_from_csv(const std::string& csv_path, const std::string& symbol = "");
    bool load_trades_from_journal(const std::string& journal_path);

    /**
     * @brief Runs the simulation with a specific strategy.
     */
    BacktestResult run(std::shared_ptr<analysis::Strategy> strategy);

private:
    struct HistoricalTrade {
        int64_t price_ticks = 0;
        int64_t quantity_lots = 0;
        uint8_t side = 0;
    };

    std::vector<MarketTick> history_;
    std::vector<HistoricalTrade> trades_;
    double initial_capital_ = 100000.0;
};

} // namespace argentum::backtest
