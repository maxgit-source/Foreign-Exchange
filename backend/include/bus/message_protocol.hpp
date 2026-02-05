#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace argentum::bus {

constexpr uint16_t kMessageProtocolVersion = 1;

enum class MessageType : uint16_t {
    MarketTick = 1,
    Order = 2,
    Trade = 3
};

struct MessageHeader {
    uint16_t version;
    uint16_t type;
    uint32_t size;
    uint64_t timestamp_ns;
};

std::vector<uint8_t> encode_message(MessageType type, const void* data, size_t size, uint64_t timestamp_ns);
bool decode_header(const void* data, size_t size, MessageHeader* out_header);
const uint8_t* payload_ptr(const void* data, size_t size);

} // namespace argentum::bus
