#include "persist/event_journal.hpp"

#include <iostream>
#include <string>

int main(int argc, char** argv) {
    std::string path = "data/order_events.jsonl";
    if (argc > 1 && argv[1] != nullptr) {
        path = argv[1];
    }

    argentum::persist::ReplaySummary summary{};
    if (!argentum::persist::EventReplayer::replay_file(path, &summary)) {
        std::cerr << "[Replay] Failed to replay file: " << path << std::endl;
        return 1;
    }

    std::cout << "[Replay] file=" << path << "\n";
    std::cout << "[Replay] total_events=" << summary.total_events << "\n";
    std::cout << "[Replay] accepted=" << summary.accepted
              << " rejected=" << summary.rejected
              << " gateway_rejected=" << summary.gateway_rejected
              << " trades=" << summary.trades
              << " canceled=" << summary.canceled
              << " replaced=" << summary.replaced << "\n";
    std::cout << "[Replay] active_orders=" << summary.active_orders.size()
              << " order_history=" << summary.order_history.size() << "\n";
    std::cout << "[Replay] monotonic_seq=" << (summary.monotonic_seq ? "true" : "false")
              << " monotonic_time=" << (summary.monotonic_time ? "true" : "false") << "\n";
    std::cout << "[Replay] committed_exposure_units=" << summary.committed_exposure_units
              << " filled_exposure_units=" << summary.filled_exposure_units
              << " net_position_lots=" << summary.net_position_lots << "\n";
    return 0;
}
