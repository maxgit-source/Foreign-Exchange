#pragma once

#include <string>
#include <iostream>
#include <fstream>
#include <mutex>

namespace argentum::audit {

enum class LogLevel { INFO, WARNING, ERROR, AUDIT };

/**
 * @class Logger
 * @brief Thread-safe audit logger.
 * In prod: Use ring-buffer and async writing thread to avoid disk I/O blocking trading path.
 */
class Logger {
public:
    static Logger& instance() {
        static Logger instance;
        return instance;
    }

    void log(LogLevel level, const std::string& message) {
        std::lock_guard<std::mutex> lock(mtx_);
        // In HFT, format time carefully or use RDTSC
        std::string prefix = (level == LogLevel::AUDIT) ? "[AUDIT] " : "[LOG] ";
        
        std::cout << prefix << message << std::endl;
        
        if (file_.is_open()) {
            file_ << prefix << message << "\n"; // Flush handled by OS or explicitly
        }
    }

private:
    Logger() {
        file_.open("audit.log", std::ios::app);
    }
    ~Logger() {
        if (file_.is_open()) file_.close();
    }

    std::ofstream file_;
    std::mutex mtx_;
};

} // namespace argentum::audit

