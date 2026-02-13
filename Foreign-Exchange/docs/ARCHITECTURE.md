# Architecture

## High-level modules
- datafeed (C): low-latency market data capture and normalization
- analysis (C++): indicators, signals, and strategy logic
- risk (C++): exposure, limits, and real-time VaR
- order (C++): order routing and execution control
- backtest (C++): historical simulation and parameter sweeps
- api (C++): REST/WebSocket gateway for UI and external clients
- common: shared types, time utils, logging, and config

## Data flow
market feeds -> datafeed -> analysis -> risk -> order -> venues
                        \-> api -> frontend

## Message protocol
- Versioned header (V1 legacy, V2 with flags + CRC).
- Payloads encoded with FlatBuffers when enabled.
- Legacy raw-struct payloads are supported via adapter.

## Persistence and infra (future)
- time-series DB: TimescaleDB or ClickHouse
- cache/pubsub: Redis
- messaging: ZeroMQ or Kafka
- metrics/logs: Prometheus + Grafana

## In-proc bus (current)
- Bounded queues per topic with configurable backpressure.
- Dedicated consumer threads per topic (configurable).
- Metrics: queue depth, drops, publish latency.

## Persistence (current)
- Asynchronous writer with bounded queue.
- TimescaleDB connection reuse when available.
- CSV fallback with rotation and optional fsync.

## Logging (current)
- Asynchronous logger with bounded queue and drop counter.
