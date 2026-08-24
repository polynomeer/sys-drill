package com.sysdrill.backend.simulation

import kotlin.math.max

/**
 * Pure, deterministic simulation math for the "선착순 쿠폰" incident template
 * (docs/PRD.md §8.1, docs/ARCHITECTURE.md §6). No I/O, no Spring — everything
 * here is `NextState = f(...)` in the sense ARCHITECTURE.md §6 describes,
 * just specialized to this one incident for v1 rather than a general engine
 * that reads scenario-defined parameters (that generalization is future work
 * once more than one incident template exists).
 */
object SimulationEngine {

    // "as-designed" capacities and baseline traffic (docs/PRD.md §8.1: 평시 300 RPS).
    private const val BASELINE_TRAFFIC_RPS = 300.0
    private const val INCIDENT_TRAFFIC_MULTIPLIER = 20.0 // PRD.md §8.1 꼬리설계: 트래픽 20배
    private const val READ_RATIO = 0.7
    private const val WRITE_RATIO = 0.3

    private const val BASE_DB_READ_CAPACITY_RPS = 3000.0
    private const val BASE_DB_WRITE_CAPACITY_RPS = 1000.0 // at BASELINE_DB_POOL_SIZE
    private const val RATE_LIMIT_CEILING_RPS = 3000.0

    private const val BASE_CACHE_LATENCY_MS = 2.0
    private const val REDIS_DEGRADATION_FACTOR = 15.0 // PRD.md §8.1 워게임: Redis latency 증가
    private const val MAX_USEFUL_TTL_SECONDS = 600.0
    private const val BASELINE_CACHE_HIT_RATIO = 0.95

    private const val BASE_P95_LATENCY_MS = 80.0
    private const val BASELINE_ERROR_RATE = 0.001

    /**
     * utilization = incoming_load / max_capacity bands, per docs/ARCHITECTURE.md §6:
     * 0~60% 안정 / 60~80% latency 증가 / 80~95% p95·p99 급등 / 95~100% error 증가 / 100%+ timeout·drop.
     */
    fun latencyMultiplier(utilization: Double): Double = when {
        utilization < 0.6 -> 1.0
        utilization < 0.8 -> 1.5
        utilization < 0.95 -> 3.0
        utilization < 1.0 -> 5.0
        else -> 8.0
    }

    fun errorRateFor(utilization: Double): Double = when {
        utilization < 0.6 -> BASELINE_ERROR_RATE
        utilization < 0.8 -> 0.005
        utilization < 0.95 -> 0.02
        utilization < 1.0 -> 0.10
        else -> 0.30
    }

    fun computeState(session: SimulationSessionState): SystemState {
        val traits = session.traits
        val baseTraffic = if (session.incidentActive) {
            BASELINE_TRAFFIC_RPS * INCIDENT_TRAFFIC_MULTIPLIER
        } else {
            BASELINE_TRAFFIC_RPS
        }
        val admittedTraffic = if (traits.rateLimitEnabled) minOf(baseTraffic, RATE_LIMIT_CEILING_RPS) else baseTraffic

        val cacheLatencyMs = if (session.incidentActive) {
            BASE_CACHE_LATENCY_MS * REDIS_DEGRADATION_FACTOR
        } else {
            BASE_CACHE_LATENCY_MS
        }
        // Redis is only "degraded" relative to its own baseline; a longer TTL means
        // fewer lock/refresh round-trips are needed, dampening how much an elevated
        // cacheLatencyMs actually costs the effective hit ratio.
        val effectiveCacheHitRatio = if (cacheLatencyMs <= BASE_CACHE_LATENCY_MS * 2) {
            BASELINE_CACHE_HIT_RATIO
        } else {
            val ttlDampening = (traits.cacheTtlSeconds / MAX_USEFUL_TTL_SECONDS).coerceIn(0.0, 1.0)
            BASELINE_CACHE_HIT_RATIO * (0.4 + 0.6 * ttlDampening)
        }

        val readRps = admittedTraffic * READ_RATIO
        val writeRps = admittedTraffic * WRITE_RATIO

        val dbReadRps = readRps * (1 - effectiveCacheHitRatio)
        val dbWriteRps = writeRps
        val dbWriteCapacity = BASE_DB_WRITE_CAPACITY_RPS * (traits.dbPoolSize / DesignTraits.DEFAULT_DB_POOL_SIZE.toDouble())

        val readUtilization = dbReadRps / BASE_DB_READ_CAPACITY_RPS
        val writeUtilization = dbWriteRps / dbWriteCapacity
        val overallUtilization = max(readUtilization, writeUtilization)

        return SystemState(
            trafficRps = admittedTraffic,
            p95LatencyMs = BASE_P95_LATENCY_MS * latencyMultiplier(overallUtilization),
            errorRate = errorRateFor(overallUtilization),
            availability = 1.0 - errorRateFor(overallUtilization),
            dbReadLoad = readUtilization,
            dbWriteLoad = writeUtilization,
            connectionPoolUsage = writeUtilization.coerceAtMost(1.0),
            cacheHitRatio = effectiveCacheHitRatio,
            cacheLatencyMs = cacheLatencyMs,
            queueLag = 0,
            consumerThroughput = 0.0,
            externalDependencyLatencyMs = 0.0,
        )
    }

    /**
     * Applies one action's docs/ARCHITECTURE.md §6.1 "긍정 효과" to the design
     * traits. The corresponding trade-off ("가능한 부작용") isn't separately
     * numerically modeled in v1 — see Evaluation._weaknesses_-style qualitative
     * notes instead, produced by [SimulationActionType]'s effect description.
     */
    fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState {
        val traits = when (action) {
            SimulationActionType.STRENGTHEN_RATE_LIMIT -> current.traits.copy(rateLimitEnabled = true)
            SimulationActionType.INCREASE_CACHE_TTL -> current.traits.copy(
                cacheTtlSeconds = maxOf(current.traits.cacheTtlSeconds * 3, 300)
            )
            SimulationActionType.INCREASE_DB_POOL -> current.traits.copy(
                dbPoolSize = current.traits.dbPoolSize + 100
            )
        }
        return current.copy(traits = traits)
    }
}
