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
)
