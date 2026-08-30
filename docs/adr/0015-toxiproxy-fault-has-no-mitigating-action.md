---
status: accepted
---

# The Toxiproxy-injected network fault has no mitigating action in this pilot

PLAN.md step 23 added a per-session Toxiproxy proxy in front of the real-infra coupon pilot's Postgres connection, injecting a real, constant ~300ms latency toxic on every query. The coupon domain's three existing actions (rate limit, cache TTL, DB pool size) are all application-layer levers; none of them can make a network round-trip faster. We deliberately did not add a fourth action (e.g. "enable retry/circuit breaker") to let the user "fix" this fault in this step.

This was a real choice, not an oversight: adding a mitigating action would have kept every prior domain's pattern of "three actions, apply them all, fully recover" — but it would have hidden the actual lesson. Verified empirically (PLAN.md step 23 notes): with the toxic active, the three existing actions still measurably improve p95 latency (baseline ~1.3s → recovered ~0.7s in this project's dev environment) by reducing how often a request has to make that slow round-trip at all — but they never approach the pre-toxic floor, because the fault itself is untouched. That gap is the point: some real infrastructure problems need a different category of fix (timeout tuning, retries, circuit breakers, or fixing the network itself) than capacity/caching/throttling levers address, and a wargame that always fully recovers with the tools already on screen would teach the wrong lesson. A future step may add a fault-specific action once this signal is validated — but that is a deliberate later decision, not an accidental gap in this one.
