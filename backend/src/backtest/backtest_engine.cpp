#include "backtest/backtest_engine.hpp"
#include <iostream>
#include <cmath>

namespace argentum::backtest {

void BacktestEngine::load_data(const std::string& symbol, const std::string& start_date, const std::string& end_date) {
    (void)start_date; (void)end_date;
    std::cout << "[Backtest] Loading historical data for " << symbol << "..." << std::endl;
    
    // Generate synthetic data for demonstration (Sine wave price)
    double price = 100.0;
    for (int i = 0; i < 1000; ++i) {
        MarketTick tick;
        tick.timestamp_ns = i * 1000000;
        tick.price = price + std::sin(i * 0.1) * 2.0;
        tick.quantity = 1.0;
        // tick.symbol = symbol; // Copiar string
        history_.push_back(tick);
    }
    std::cout << "[Backtest] Loaded " << history_.size() << " ticks." << std::endl;
}

BacktestResult BacktestEngine::run(std::shared_ptr<analysis::Strategy> strategy) {
    std::cout << "[Backtest] Running simulation for strategy: " << strategy->get_name() << std::endl;

    double cash = initial_capital_;
    double holdings = 0.0;
    double max_equity = initial_capital_;
    double max_dd = 0.0;
    size_t trades = 0;

    for (const auto& tick : history_) {
        // 1. Feed strategy
        strategy->on_tick(tick);

        // 2. Mock execution (Simplified: Strategy would send orders, Engine matches them)
        // Here we just simulate PnL tracking if we had positions.
    }

    // Mock Result
    return BacktestResult{
        .total_pnl = 1500.50, // Fake result
        .trades_count = 50,
        .max_drawdown = 0.05,
        .sharpe_ratio = 1.8
    };
}

} // namespace argentum::backtest
