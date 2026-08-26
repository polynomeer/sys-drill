package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SimulationEngine
import com.sysdrill.backend.simulation.SimulationSessionState
import com.sysdrill.backend.simulation.SimulationStateStore
import com.sysdrill.backend.simulation.SystemState
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PLAN.md step 21 — the opt-in real-infra alternative to
 * [com.sysdrill.backend.simulation.RuleBasedSimulationEngine], scoped to the
 * "coupon" domain only (ADR-0013). `computeState` is a cache read
 * ([RealInfraMeasurementStore]) — WargameLive.tsx polls it every 3s, and
 * re-running k6 on every poll would be expensive and would visibly restart
 * traffic in the UI. Only `applyAction` (and the very first `computeState`
 * after an incident starts, on a cache miss) triggers a real probe: a fresh
 * k6 run against this session's dedicated Postgres schema/pool/Redis cache.
 */
@Component
class RealInfraCouponEngine(
    private val schemaProvisioner: CouponSchemaProvisioner,
    private val dataSourceRegistry: SessionDataSourceRegistry,
    private val measurementStore: RealInfraMeasurementStore,
    private val loadRunner: CouponLoadRunner,
    private val stats: RealInfraCouponStats,
    private val stateStore: SimulationStateStore,
    @Value("\${sysdrill.simulation.realinfra.max-db-pool-size}") private val maxPoolSize: Int,
    @Value("\${sysdrill.simulation.realinfra.baseline-rps}") private val baselineRps: Int,
    @Value("\${sysdrill.simulation.realinfra.incident-rps}") private val incidentRps: Int,
    @Value("\${sysdrill.simulation.realinfra.probe-duration-seconds}") private val probeDurationSeconds: Int,
) : SimulationEngine {

    /** Per-session in-process lock — prevents a racing double `computeState` cache-miss from double-provisioning or double-probing (single-JVM-instance assumption, as elsewhere in this pilot). */
    private val sessionLocks = ConcurrentHashMap<UUID, Any>()

    override fun computeState(session: SimulationSessionState): SystemState =
        measurementStore.find(session.sessionId)
            ?: probeAndCache(session.sessionId, session.traits, session.incidentActive, provisionSchema = true)

    override fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState {
        val updatedTraits = mutate(current.traits, action)
        val updated = current.copy(traits = updatedTraits)
        // Persist BEFORE probing, not after: RealInfraCouponController reads
        // current traits from this same store (to pick pool size/cache TTL/rate
        // limit) on every request k6 sends during the probe. SimulationService
        // only calls stateStore.save AFTER this whole method returns — without
        // this early save, the controller would serve the entire probe against
        // the pre-action pool size, and (observed empirically) the controller and
        // this engine would fight to rebuild the pool at two different sizes
        // mid-probe, since SessionDataSourceRegistry.poolFor rebuilds on any size
        // mismatch.
        stateStore.save(current.sessionId, updated)
        // provisionSchema = false: DROP SCHEMA CASCADE here would need to wait on
        // any request from the previous (possibly still-saturated) probe that's
        // still mid-flight on this session's dedicated pool — under real load that
        // backlog can take much longer to drain than this probe's own timeout,
        // so re-provisioning on every action risked the NEXT probe hanging on a
        // DDL lock (observed empirically during PLAN.md step 21 verification).
        // The schema/table already exists from startIncident; reuse it as-is —
        // carrying forward whatever inventory state real claims already left,
        // which is more honest than silently resetting to 1000 every click.
        probeAndCache(current.sessionId, updatedTraits, current.incidentActive, provisionSchema = false)
        return updated
    }

    /**
     * Same [SimulationActionType]s as the rule-based coupon engine (so the
     * frontend's existing coupon action buttons work unchanged), but capped
     * differently: the shared Postgres container's `max_connections` is a
     * real, global ceiling shared by every concurrent real-infra session, so
     * pool growth can't be unbounded like the rule-based `+100`/click.
     */
    private fun mutate(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
        SimulationActionType.STRENGTHEN_RATE_LIMIT -> traits.copy(rateLimitEnabled = true)
        SimulationActionType.INCREASE_CACHE_TTL -> traits.copy(cacheTtlSeconds = (traits.cacheTtlSeconds * 3).coerceAtLeast(MIN_CACHE_TTL_SECONDS))
        SimulationActionType.INCREASE_DB_POOL -> traits.copy(dbPoolSize = (traits.dbPoolSize + DB_POOL_STEP).coerceAtMost(maxPoolSize))
        else -> error("$action does not apply to the real-infra coupon incident")
    }

    private fun probeAndCache(sessionId: UUID, traits: DesignTraits, incidentActive: Boolean, provisionSchema: Boolean): SystemState {
        val lock = sessionLocks.computeIfAbsent(sessionId) { Any() }
        synchronized(lock) {
            val schema = if (provisionSchema) schemaProvisioner.provision(sessionId) else schemaProvisioner.schemaName(sessionId)
            val poolSize = traits.dbPoolSize.coerceIn(MIN_DB_POOL_SIZE, maxPoolSize)
            val dataSource = dataSourceRegistry.poolFor(sessionId, schema, poolSize)

            val rateLimitCeiling = if (traits.rateLimitEnabled) baselineRps else Int.MAX_VALUE
            stats.resetLimiter(sessionId, rateLimitCeiling, RATE_LIMIT_WINDOW_MILLIS)
            stats.resetCacheCounters(sessionId)

            val targetRps = if (incidentActive) incidentRps else baselineRps

            // Sample real peak concurrent connections while k6 runs — connections are
            // short-lived per-request, so reading this only after the run would miss
            // the actual contention a real pool sees during load.
            val peakActiveConnections = AtomicInteger(0)
            val stopSampling = AtomicBoolean(false)
            val sampler = Thread {
                while (!stopSampling.get()) {
                    peakActiveConnections.updateAndGet { maxOf(it, dataSource.hikariPoolMXBean?.activeConnections ?: 0) }
                    Thread.sleep(SAMPLE_INTERVAL_MILLIS)
                }
            }
            sampler.isDaemon = true
            sampler.start()
            val summary = try {
                loadRunner.run(sessionId, targetRps, probeDurationSeconds)
            } finally {
                stopSampling.set(true)
                sampler.join(1000)
            }

            val state = SystemState(
                trafficRps = summary.achievedRps,
                p95LatencyMs = summary.p95Ms,
                errorRate = summary.errorRate,
                availability = 1.0 - summary.errorRate,
                dbReadLoad = 0.0,
                dbWriteLoad = 0.0,
                connectionPoolUsage = (peakActiveConnections.get().toDouble() / poolSize).coerceIn(0.0, 1.0),
                cacheHitRatio = stats.cacheHitRatio(sessionId),
                cacheLatencyMs = 0.0,
                queueLag = 0,
                consumerThroughput = summary.achievedRps,
                externalDependencyLatencyMs = 0.0,
            )
            measurementStore.save(sessionId, state)
            return state
        }
    }

    companion object {
        /** Deliberately much smaller than [DesignTraits.DEFAULT_DB_POOL_SIZE] (50) — that default is a rule-based formula input, not a literal real connection count; a real-infra session should start visibly undersized so INCREASE_DB_POOL has room to show an effect. */
        const val INITIAL_DB_POOL_SIZE = 4

        private const val MIN_DB_POOL_SIZE = 2
        private const val DB_POOL_STEP = 4
        private const val MIN_CACHE_TTL_SECONDS = 30
        private const val RATE_LIMIT_WINDOW_MILLIS = 1000L
        private const val SAMPLE_INTERVAL_MILLIS = 50L
    }
}
