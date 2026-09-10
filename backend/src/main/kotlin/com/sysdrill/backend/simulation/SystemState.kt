package com.sysdrill.backend.simulation

/**
 * Runtime snapshot of a simulated system, per docs/ARCHITECTURE.md §6. This
 * is always a *derived* value — see [SimulationEngine.computeState] — never
 * persisted directly; what's persisted (in Redis) is the smaller set of
 * inputs needed to recompute it: see [SimulationSessionState].
 */
data class SystemState(
    val trafficRps: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
    val availability: Double,
    val dbReadLoad: Double,
    val dbWriteLoad: Double,
    val connectionPoolUsage: Double,
    val cacheHitRatio: Double,
    val cacheLatencyMs: Double,
    val queueLag: Long,
    val consumerThroughput: Double,
    val externalDependencyLatencyMs: Double,
) {
    /**
     * SysDrill_UIUX_Design_Plan.docx §5.5 — the Incident screen's CPU/Memory
     * gauges. A computed property, not a constructor field: every domain's
     * `computeState` (7 rule-based + 2 real-infra) already produces a
     * load-ish signal in at least one of [dbReadLoad]/[dbWriteLoad]/
     * [connectionPoolUsage]/[queueLag]/[errorRate], so deriving CPU from
     * whichever of those is worst covers all of them uniformly — without
     * touching any of the 9 existing `SystemState(...)` call sites, without
     * a new domain-specific formula per engine, and without this
     * participating in equals/hashCode/copy (ADR-0011: derived values are
     * computed at read time, never stored). API responses go through
     * [SystemStateResponse], a separate explicit-field DTO — that mapping,
     * not this class, is what actually has to list `cpuUtilization` for it
     * to reach the frontend; the property itself works the same either way.
     */
    val cpuUtilization: Double
        get() {
            val queueSignal = (queueLag / 100.0).coerceAtMost(1.0)
            val errorSignal = (errorRate * 3).coerceAtMost(1.0)
            return maxOf(dbReadLoad, dbWriteLoad, connectionPoolUsage, queueSignal, errorSignal).coerceIn(0.05, 0.98)
        }

    /** Latency-driven backpressure (more in-flight work held in memory) blended with [cpuUtilization], so it tracks but doesn't just mirror CPU. */
    val memoryUtilization: Double
        get() {
            val latencyPressure = p95LatencyMs / (p95LatencyMs + 200.0)
            return (cpuUtilization * 0.6 + latencyPressure * 0.4).coerceIn(0.05, 0.98)
        }

    /**
     * Phase 3-A (docs/DRILLS_SIMULATION_VISION.md §6) — the log-severity classification
     * `WargameLive.tsx`'s `deriveLevel()` used to compute client-side from
     * [cpuUtilization], moved here so the frontend displays a backend-computed value
     * instead of re-deriving it. Mirrors `frontend/src/lib/metrics.ts`'s
     * `utilizationStatus()` bands exactly (0.6/0.95) — there's no shared source between
     * the two runtimes, so a change to one needs the matching change to the other.
     */
    val level: String
        get() = when {
            cpuUtilization < 0.6 -> "INFO"
            cpuUtilization < 0.95 -> "WARN"
            else -> "ERROR"
        }
}
