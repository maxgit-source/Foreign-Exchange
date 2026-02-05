#pragma once

#include <string>
#include <iostream>
#include <vector>
#include <functional>

namespace argentum::alerts {

enum class AlertSeverity { INFO, WARNING, CRITICAL };

struct Alert {
    AlertSeverity severity;
    std::string message;
    long long timestamp;
};

class AlertSystem {
public:
    using AlertHandler = std::function<void(const Alert&)>;

    void register_handler(AlertHandler handler) {
        handlers_.push_back(handler);
    }

    void dispatch(AlertSeverity severity, const std::string& message) {
        Alert alert{severity, message, 0}; // TODO: Add real timestamp
        for (const auto& handler : handlers_) {
            handler(alert);
        }
    }
    
    // Default handler implementation
    static void console_handler(const Alert& alert) {
        std::string prefix = (alert.severity == AlertSeverity::CRITICAL) ? "[CRITICAL] " : "[ALERT] ";
        std::cerr << prefix << alert.message << std::endl;
    }

private:
    std::vector<AlertHandler> handlers_;
};

} // namespace argentum::alerts
