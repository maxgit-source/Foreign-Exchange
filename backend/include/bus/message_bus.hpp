#pragma once

#include <string>
#include <functional>
#include <vector>
#include <span>
#include <memory>

namespace argentum::bus {

/**
 * @class MessageBus
 * @brief Abstract interface for the low-latency messaging system (ZeroMQ/IPC).
 */
class MessageBus {
public:
    virtual ~MessageBus() = default;

    /**
     * @brief Initialize the bus connection.
     * @param endpoint The connection string (e.g., "tcp://localhost:5555" or "ipc://feeds")
     * @param is_publisher True if this node will write data.
     */
    virtual void connect(const std::string& endpoint, bool is_publisher) = 0;

    /**
     * @brief Publish a binary message to a topic.
     * @param topic The topic string (e.g., "market.btc_usdt").
     * @param data Pointer to data.
     * @param size Size of data.
     */
    virtual void publish(const std::string& topic, const void* data, size_t size) = 0;

    /**
     * @brief Subscribe to a topic.
     * @param topic Topic to subscribe to.
     * @param callback Function to handle incoming data.
     */
    virtual void subscribe(const std::string& topic, std::function<void(const void* data, size_t size)> callback) = 0;
};

/**
 * @brief Create an in-process MessageBus (thread-safe, single-process).
 * This is the default fallback when ZeroMQ is not linked.
 */
std::unique_ptr<MessageBus> create_inproc_bus();

} // namespace argentum::bus
