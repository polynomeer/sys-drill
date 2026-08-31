---
status: accepted
---

# Incident replay reconstructs rule-based timelines by recomputation, but persists real-infra snapshots — a scoped exception to ADR-0011

PLAN.md step 25 added `GET /sessions/{sessionId}/simulation/timeline`, letting a user scrub through how a session's metrics evolved as they applied actions during an incident. It reuses `AppliedAction` rows (already written on every `applyAction` call) rather than introducing a second, separately-tracked history — and for rule-based domains, it replays `RuleBasedSimulationEngine` over the stored action sequence to recompute each step's `SystemState`, storing nothing new. That's a direct application of ADR-0011: a rule-based state is a pure function of `(domain, incidentActive, traits-after-N-actions)`, so it's always reproducible from the action list alone.

Real-infra sessions can't follow that path. Their `SystemState` came from an actual k6 run against actual Postgres/Toxiproxy at a specific moment — running the "same" probe again later would need the same infra to still exist (it doesn't; ADR-0013's session sweep tears it down) and would produce different numbers even if it did (real timing, not a formula). So `AppliedActionSnapshot` — a small JSON payload written into `AppliedAction.parameters` (an existing, previously-unused JSONB column) — captures each real-infra step's actual `SystemState` at the moment it happened. Every row also records `engineMode`, so a replay request can tell which reconstruction path applies without depending on `SimulationStateStore`'s 6-hour Redis TTL, which may well have expired by the time someone views a session's replay.

This is a deliberate, narrowly-scoped exception to ADR-0011's "never persist derived values" — not a reversal of it. It applies only where the underlying measurement is fundamentally non-reproducible (real-infra probes), the same category of exception ADR-0014 already carved out for real-infra test assertions.
