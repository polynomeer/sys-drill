---
status: accepted
---

# SimulationEngine is three domain-specific pure functions, not a generic scenario-driven engine

The coupon incident (Redis latency spike → DB write hotspot) was originally built as hardcoded constants in one object, with generalizing to a data-driven engine explicitly deferred until more incident templates existed. That point arrived when the notification (provider timeout → consumer lag) and product-browsing (cache stampede → DB read overload) incidents were added. We still didn't build a generic engine: each domain got its own object (`SimulationEngine.Coupon`/`Notification`/`ProductBrowsing`) with its own constants and `computeState`/`applyAction` functions. The only code shared between them is the domain-agnostic utilization-band math (`latencyMultiplier`, `errorRateFor`).

The three incidents model genuinely different bottlenecked resources (DB read/write capacity, consumer throughput, cache hit ratio) with different formulas — a data-driven engine would need a small formula language to express that. Every incident's output numbers are also hand-verified against the constants in unit tests (an established practice since the first incident), and three concrete functions are what that hand-verification needs; a generic engine would make the numbers harder to trace back to a formula, for comparatively little code saved at three domains. Revisit if a fourth incident's mechanics turn out to be a near-duplicate of an existing one rather than a genuinely new shape.
