#include "bus/message_bus.hpp"

#include <unordered_map>
#include <shared_mutex>
#include <mutex>

namespace argentum::bus {

class InprocMessageBus final : public MessageBus {
public:
    void connect(const std::string& endpoint, bool is_publisher) override {
        endpoint_ = endpoint;
        is_publisher_ = is_publisher;
    }

    void publish(const std::string& topic, const void* data, size_t size) override {
        std::vector<std::function<void(const void*, size_t)>> callbacks;
        {
            std::shared_lock lock(mutex_);
            auto it = subscribers_.find(topic);
            if (it != subscribers_.end()) {
                callbacks = it->second;
            }
        }
        for (auto& cb : callbacks) {
            cb(data, size);
        }
    }

    void subscribe(const std::string& topic, std::function<void(const void* data, size_t size)> callback) override {
        std::unique_lock lock(mutex_);
        subscribers_[topic].push_back(std::move(callback));
    }

private:
    std::string endpoint_;
    bool is_publisher_ = false;
    std::unordered_map<std::string, std::vector<std::function<void(const void*, size_t)>>> subscribers_;
    std::shared_mutex mutex_;
};

std::unique_ptr<MessageBus> create_inproc_bus() {
    return std::make_unique<InprocMessageBus>();
}

} // namespace argentum::bus
