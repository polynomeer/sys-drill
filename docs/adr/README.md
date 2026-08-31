# Architecture Decision Records

Short records of decisions that are hard to reverse, would surprise a future reader, and were a real trade-off between genuine alternatives — not every decision, just the ones worth explaining "why" for. See [CLAUDE.md](../../CLAUDE.md) for when and how these get written.

| # | Decision |
|---|---|
| [0001](0001-plain-uuid-scalar-references-across-aggregates.md) | Plain UUID scalar fields instead of JPA relationships across aggregate boundaries |
| [0002](0002-content-via-migrations-not-admin-crud.md) | Scenario and Build challenge content ships as Flyway seed migrations, not admin CRUD |
| [0003](0003-no-real-authentication-in-mvp.md) | MVP ships with a nickname-only guest profile, not real authentication |
| [0004](0004-async-jobs-enqueued-only-after-commit.md) | Redis job queues are only ever pushed to after the enqueuing transaction commits |
| [0005](0005-llm-credential-env-var-namespace.md) | LLM credentials use LLM_ANTHROPIC_* env vars, not ANTHROPIC_* |
| [0006](0006-config-as-data.md) | Grading scripts and prompt templates live in the database, not the filesystem |
| [0007](0007-docker-sandboxed-build-execution.md) | Build Mode grades submissions inside network-isolated, resource-capped Docker containers |
| [0008](0008-python-for-build-challenges.md) | Build Mode challenges run in Python, independent of the backend's Kotlin/JVM stack |
| [0009](0009-bridge-mode-as-a-single-fk-column.md) | Bridge Mode is a nullable FK column on Session, not a separate domain |
| [0010](0010-simulation-engine-per-domain-functions.md) | SimulationEngine is three domain-specific pure functions, not a generic scenario-driven engine |
| [0011](0011-derived-values-are-never-persisted.md) | Interpreted/derived values are computed at read time, never stored |
| [0012](0012-new-incident-domains-get-distinct-mechanisms.md) | A new SimulationEngine domain gets a genuinely distinct mechanism, not a relabeled copy of an existing one |
| [0013](0013-coupon-real-infra-pilot-schema-per-session.md) | Coupon gets an opt-in real-infra SimulationEngine, schema-per-session instead of container-per-session |
| [0014](0014-real-infra-tests-use-range-assertions.md) | Real-infra simulation tests assert ranges and relative comparisons, not exact hand-computed values |
| [0015](0015-toxiproxy-fault-has-no-mitigating-action.md) | The Toxiproxy-injected network fault has no mitigating action in this pilot |
| [0016](0016-incident-replay-snapshots-only-for-real-infra.md) | Incident replay reconstructs rule-based timelines by recomputation, but persists real-infra snapshots |
| [0017](0017-notification-real-infra-pilot-in-process-clients.md) | Notification's real-infra pilot drives Kafka with in-process clients, not an external load-gen container |
| [0018](0018-real-infra-engine-selection-by-domain-map.md) | Real-infra engine selection generalizes from a single coupon field to a domain→engine map |
