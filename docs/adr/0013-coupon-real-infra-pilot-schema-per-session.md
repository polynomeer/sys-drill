---
status: accepted
---

# Coupon gets an opt-in real-infra SimulationEngine, schema-per-session instead of container-per-session

Phase 3's premise is that real infra might teach more than the rule-based math `SimulationEngine` (docs/ARCHITECTURE.md §1 원칙 7 explicitly rejected spinning up real infra per session — this is a deliberate revisit of that principle, not an oversight). We introduced `interface SimulationEngine` and a `RealInfraCouponEngine` that runs a genuine k6 load test against a real Postgres/Redis-backed coupon-claim endpoint, scoped to the coupon domain only (same reasoning as ADR-0010: a new mechanism per domain, not a generic layer) and strictly opt-in — every other domain, and non-opted-in coupon sessions, are completely unaffected.

The literal reading of "실제 컨테이너 기반 의존성 도입" would spin up a dedicated Postgres/Redis container per session. We chose a cheaper isolation unit instead: a dedicated Postgres **schema** plus a dedicated `HikariDataSource` scoped to it per session, reusing the existing shared `docker-compose.yml` containers. The three traits this pilot teaches — rate limiting, cache TTL, DB pool size — don't need physical container isolation to be real; a dedicated schema and pool already make cache staleness, write contention, and pool exhaustion genuinely observable under a real k6 load. Container-per-session would only pay for itself once a later step needs to kill or network-partition a dependency (Toxiproxy), which schema-per-session structurally cannot do — revisit then, not now.

One empirical correction this decision produced: `RealInfraCouponEngine.applyAction` provisions (drops/recreates) the schema only once, at `startIncident` — re-provisioning on every action was tried first and could hang, because `DROP SCHEMA CASCADE` has to wait for any request from the previous, possibly-still-saturated probe that's still mid-flight on the same dedicated pool.
