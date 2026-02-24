# Argentum-FX Execution Plan (Codebase-wide review)

## Scope and intent

This plan is based on a full review of current source modules (`backend`, `frontend`, `infra`, `docs`) and current test execution.
Goal: move from prototype maturity (`~4.5/10`) to institutional-grade platform (`9/10`) with milestone evidence usable for fundraising.

## Baseline from current code (what is real today)

1. Core pipeline exists
- In-proc bus with bounded queues and backpressure (`backend/include/bus/message_bus.hpp`, `backend/src/bus/message_bus.cpp`).
- Binary protocol with versioned headers and optional CRC (`backend/include/bus/message_protocol.hpp`, `backend/src/bus/message_protocol.cpp`).
- Market tick codec legacy + FlatBuffers optional (`backend/src/codec/market_tick_codec.cpp`).

2. Trading core exists but is still MVP
- Order book with match/cancel/modify/vwap (`backend/src/engine/order_book.cpp`).
- OMS lifecycle base (`backend/src/trading/order_manager.cpp`).
- Risk checks for order value and exposure (`backend/src/risk/risk_manager.cpp`).

3. API exists and is usable for demo
- HTTP + WebSocket server with `/api/v1/health`, `/api/v1/orders`, `/api/v1/markets/*/snapshot`, `/metrics`, `/ws` (`backend/api/src/http_ws_server.cpp`).
- Token and rate-limit in gateway (`backend/api/src/market_gateway.cpp`).

4. Persistence exists
- Async writer with CSV fallback and optional libpq COPY (`backend/src/persist/data_writer.cpp`).

5. Tests exist and pass
- `ctest --test-dir build-local -C Release --output-on-failure`: 6/6 passing.

## Hard blockers to reach 9/10 (from code review)

1. Risk accounting correctness is not institution-safe
- Market order price can be `0` in API parser (`backend/api/src/http_ws_server.cpp:722-727,746`).
- Risk reservation/release model can drift because reservation uses order price while fills release using trade price (`backend/src/trading/order_manager.cpp:61,69,86`; `backend/src/risk/risk_manager.cpp:16-62`).
- Outcome: exposure can become inconsistent under realistic execution paths.

2. Concurrency model is unsafe for high-load multi-thread operation
- `OrderBook` has no internal synchronization while OMS calls into it across multiple entry points (`backend/include/engine/order_book.hpp`, `backend/src/engine/order_book.cpp`).
- OMS mutex does not guard all book mutations through a single serialized critical path (`backend/include/trading/order_manager.hpp:79-81`, `backend/src/trading/order_manager.cpp`).

3. Market connectivity is still single-path demo
- FIX/SBE declared at enum level but not implemented (`backend/include/datafeed/market_parser.h:14-15`, `backend/src/datafeed/market_parser.c:144-147`).

4. Backtesting and ML are placeholders
- Synthetic historical data + fake result (`backend/src/backtest/backtest_engine.cpp:11,42,48`).
- Python bridge returns dummy prediction (`backend/include/ml/python_bridge.hpp:26-29`).

5. Frontend is mostly static UI prototype
- Static orderbook/depth data (`frontend/src/components/domain/OrderBook.tsx:3,9`, `frontend/src/components/domain/DepthChart.tsx:4`).
- Order form not connected to backend (`frontend/src/components/domain/OrderEntry.tsx:29,38`).
- App still includes demo placeholders (`frontend/src/App.tsx:69,93,106`).

6. Contract and typing drift exists
- Stream contract/backend use `timestamp_ns`, but frontend health type keeps `timestamp?: string` (`docs/contracts/stream-contract-v1.md`, `backend/api/src/market_gateway.cpp`, `frontend/src/types/api.ts:25`).

7. Platform and operations limits
- API and socket stack are Windows-only (`backend/src/network/socket_manager.c:10`, `backend/api/src/http_ws_server.cpp:23`).
- Multiple module folders still wired to placeholders (`backend/*/CMakeLists.txt` with `placeholder` entries).

## Delivery strategy

Execution is split into 6 phases with strict gates.
Do not overlap phases that affect accounting correctness and event model until the gate is passed.

## Phase 0 (Weeks 1-2): Stabilization and architecture freeze

Objective:
- Freeze event model and remove correctness ambiguity before adding features.

Deliverables:
1. Define canonical order state machine and event schema (`order_accepted`, `order_rejected`, `order_filled`, `order_canceled`, `order_replaced`, `risk_rejected`).
2. Enforce non-zero executable price semantics for risk accounting path.
3. Decide concurrency model:
- Option A: single-threaded matching shard per instrument.
- Option B: explicit lock domain around orderbook mutation path.
4. Add Architecture Decision Records for risk reservation model and concurrency model.

Exit gate:
- Design review signed.
- No unresolved ambiguity on reservation/release formulas.

## Phase 1 (Weeks 3-6): OMS and Risk correctness hardening

Objective:
- Make pre-trade and post-trade accounting deterministic and auditable.

Deliverables:
1. Rework risk ledger:
- Track reservations by `order_id` and release exact reserved quantities.
- Separate `reserved_notional`, `filled_notional`, and `realized/unrealized pnl`.
2. Complete order semantics:
- Add `IOC`, `FOK`, `GTC`; deterministic cancel/replace transitions.
3. Deterministic reconciliation:
- End-of-cycle consistency check between order state, fills, and risk ledger.
4. Tests:
- Add race and property tests for reservation invariants.
- Add negative tests for malformed price/quantity and duplicate order races.

Exit gate:
- Invariant tests pass under stress.
- Zero unresolved drift between fills and exposure accounting.

## Phase 2 (Weeks 7-12): Event sourcing, replay, and data integrity

Objective:
- Move from transient runtime to replayable/auditable trading core.

Deliverables:
1. Append-only event journal (orders, fills, risk decisions, gateway rejections).
2. Replay engine that reconstructs deterministic book + risk + positions.
3. Unified persistence policy:
- Raw ticks + execution events + snapshots.
4. Backtest v1 replacement:
- Historical loader from persisted events/ticks.
- Real PnL, drawdown, and trade attribution.

Exit gate:
- Replaying same event stream reproduces same final state and key metrics.
- Drill: reconstruct any order lifecycle by id.

## Phase 3 (Weeks 13-18): Multi-venue and execution quality

Objective:
- Evolve from single demo feed to venue-aware execution infrastructure.

Deliverables:
1. Implement FIX adapter v1 and normalized venue interface.
2. Implement market data adapters with normalized symbol/instrument metadata.
3. Smart routing v1 (best quote + fee/latency scoring).
4. Add realistic simulator (slippage, queue position, latency profile).

Exit gate:
- Orders can route through at least 2 venues in paper mode.
- Execution quality report (fill ratio, slippage, latency percentiles) generated per venue.

## Phase 4 (Weeks 19-24): API/Frontend operator product

Objective:
- Deliver expert-facing control plane and complete API lifecycle.

Deliverables:
1. API v1 completion:
- Create/cancel/replace/list orders.
- Positions, exposures, pnl, limits, risk alerts.
- Token lifecycle endpoints (create/revoke/rotate) with audit.
2. Frontend migration from mock to live:
- Replace static orderbook/depth/positions with backend data.
- Add risk dashboard and latency panels.
3. Contract cleanup:
- Align frontend types with backend schema (`timestamp_ns`, gateway reject reasons, metrics fields).

Exit gate:
- UI can run full paper-trading workflow against backend only (no mock blocks).
- API contract tests and frontend integration tests pass in CI.

## Phase 5 (Weeks 25-32): Compliance, security, and reliability

Objective:
- Reach regulated pilot readiness and enterprise-grade operations.

Deliverables:
1. Security baseline:
- TLS termination, RBAC, MFA for admin actions, secret rotation policy.
2. Compliance baseline:
- Immutable audit retention, surveillance primitives, export-ready reports.
3. SRE baseline:
- SLOs, alerting, on-call runbooks, failover drills.
4. Deployment:
- Containerized services, blue/green or rolling strategy, rollback automation.

Exit gate:
- Successful tabletop incident simulation + failover test.
- Audit package can reconstruct full order timeline and auth trail.

## Phase 6 (Weeks 33-40): Commercialization and funding readiness

Objective:
- Convert technical readiness into paying pilots and finance-ready metrics.

Deliverables:
1. B2B packaging:
- License tiers, SLAs, support model, tenancy boundaries.
2. Pilot toolkit:
- Customer onboarding runbook, environment templates, observability dashboard pack.
3. Funding artifacts:
- Technical due diligence dossier (architecture, risk controls, replay evidence, incident metrics).

Exit gate:
- 1-3 active design partners.
- Signed KPI evidence for reliability, execution quality, and auditability.

## KPI framework (must be tracked from Phase 1 onward)

1. Correctness KPIs
- Risk reservation drift incidents.
- Replay determinism rate.
- Order state transition violations.

2. Performance KPIs
- Order ack latency p50/p95/p99.
- Tick-to-book and tick-to-client latency.
- Drop rates in bus and writer queues.

3. Reliability KPIs
- Uptime and error budget burn.
- MTTR for Sev-1/Sev-2 incidents.
- Failed flush count and recovery time.

4. Product KPIs
- Paper-trading completion success rate.
- API reliability under load.
- Pilot customer issue closure time.

## Immediate 30-day implementation backlog

1. Fix risk reservation model and market-order price semantics.
2. Serialize or shard orderbook mutation path safely.
3. Introduce event journal and minimal replay command.
4. Replace frontend mock order actions with real order submit + ack stream.
5. Align API/Frontend schema types and remove contract drift.
6. Add CI jobs for replay invariants and integration API flows.

This is the minimum high-leverage path to move from "strong prototype" to "fundable institutional pilot candidate".
