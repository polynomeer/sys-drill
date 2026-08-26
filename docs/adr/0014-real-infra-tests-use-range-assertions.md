---
status: accepted
---

# Real-infra simulation tests assert ranges and relative comparisons, not exact hand-computed values

Every prior `SimulationEngine` domain (coupon through batch-settlement) is pure math — `SimulationEngineTest` hand-derives the exact expected double for every scenario and asserts `isCloseTo` against it, because the formula is deterministic and machine-independent. `RealInfraCouponEngine`'s numbers come from an actual k6 run against a real, dedicated Postgres pool: the same test on the same code produces different p95/error-rate/throughput numbers depending on the machine's CPU, Docker resource limits, and whatever else is competing for the shared Postgres container at that moment (verified during PLAN.md step 21: baseline calibration on this project's dev machine needed incident RPS around 3000 to show real saturation — a value that will differ on other hardware).

`RealInfraCouponEngineTest` therefore asserts plausibility ranges (`errorRate` in `[0,1]`, `p95LatencyMs >= 0`) and relative comparisons under identical load (rate-limited p95 ≤ unlimited p95) rather than exact values. This is a deliberate, narrowly-scoped exception for real-infra domains specifically — it does not loosen the exact-value norm for `RuleBasedSimulationEngine`'s tests, which stay exact because their inputs are genuinely deterministic.
