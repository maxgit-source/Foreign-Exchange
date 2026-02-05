#include "bus/message_protocol.hpp"

#include <cstring>

namespace argentum::bus {

std::vector<uint8_t> encode_message(MessageType type, const void* data, size_t size, uint64_t timestamp_ns) {
    std::vector<uint8_t> buffer(sizeof(MessageHeader) + size);
    MessageHeader header{};
    header.version = kMessageProtocolVersion;
    header.type = static_cast<uint16_t>(type);
    header.size = static_cast<uint32_t>(size);
    header.timestamp_ns = timestamp_ns;
    std::memcpy(buffer.data(), &header, sizeof(header));
    if (size > 0 && data) {
        std::memcpy(buffer.data() + sizeof(header), data, size);
    }
    return buffer;
}

bool decode_header(const void* data, size_t size, MessageHeader* out_header) {
    if (!data || !out_header || size < sizeof(MessageHeader)) return false;
    std::memcpy(out_header, data, sizeof(MessageHeader));
    return out_header->version == kMessageProtocolVersion;
}

const uint8_t* payload_ptr(const void* data, size_t size) {
    if (!data || size < sizeof(MessageHeader)) return nullptr;
    return static_cast<const uint8_t*>(data) + sizeof(MessageHeader);
}

} // namespace argentum::bus
