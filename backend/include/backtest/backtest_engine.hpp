#pragma once

#include "core/types.h"
#include "analysis/strategy.hpp"
#include <vector>
#include <string>
#include <functional>

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

    /**
     * @brief Runs the simulation with a specific strategy.
     */
    BacktestResult run(std::shared_ptr<analysis::Strategy> strategy);

private:
    std::vector<MarketTick> history_;
    double initial_capital_ = 100000.0;
};

} // namespace argentum::backtest
