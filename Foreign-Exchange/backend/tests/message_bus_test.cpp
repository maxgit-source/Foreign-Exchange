#include "bus/message_bus.hpp"

#include <cassert>
#include <cstring>

int main() {
    argentum::bus::InprocBusConfig config;
    config.queue_capacity = 2;
    config.policy = argentum::bus::BackpressurePolicy::DropNewest;
    config.consumer_threads = 0; // keep messages queued for test determinism

    auto bus = argentum::bus::create_inproc_bus(config);

    const char payload[4] = {'t', 'e', 's', 't'};
    assert(bus->publish("market.ticks", payload, sizeof(payload)) == ARGENTUM_OK);
    assert(bus->publish("market.ticks", payload, sizeof(payload)) == ARGENTUM_OK);
    assert(bus->publish("market.ticks", payload, sizeof(payload)) == ARGENTUM_ERR_TIMEOUT);

    argentum::bus::TopicMetrics metrics{};
    assert(bus->get_metrics("market.ticks", &metrics));
    assert(metrics.queue_depth == 2);
    assert(metrics.drops == 1);

    return 0;
}
