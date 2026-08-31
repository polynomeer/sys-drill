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

/**
 * PLAN.md step 27 — the opt-in real-infra alternative to
 * [com.sysdrill.backend.simulation.RuleBasedSimulationEngine], scoped to the
 * "notification" domain only, mirroring [RealInfraCouponEngine]'s shape
 * (ADR-0013) for a second real-infra domain: a real Kafka topic/consumer
 * group instead of a real Postgres schema/pool. Same `computeState` = cache
 * read / `applyAction` = real probe split, for the same reason (WargameLive.tsx
 * polls every 3s; re-running a multi-second Kafka probe on every poll would
 * be wasteful and would visibly restart traffic in the UI).
 */
@Component
class RealInfraNotificationEngine(
    private val topicProvisioner: NotificationTopicProvisioner,
    private val loadRunner: NotificationLoadRunner,
    private val measurementStore: RealInfraMeasurementStore,
    private val stateStore: SimulationStateStore,
    private val sessionTracker: RealInfraSessionTracker,
    @Value("\${sysdrill.simulation.realinfra.kafka.partitions-per-session}") private val maxConsumerCount: Int,
) : SimulationEngine {

    /** Per-session in-process lock, same reasoning as [RealInfraCouponEngine.sessionLocks]. */
    private val sessionLocks = ConcurrentHashMap<UUID, Any>()

    override fun computeState(session: SimulationSessionState): SystemState =
        measurementStore.find(session.sessionId)
            ?: probeAndCache(session.sessionId, session.traits, session.incidentActive, provisionTopic = true)

    override fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState {
        val updatedTraits = mutate(current.traits, action)
        val updated = current.copy(traits = updatedTraits)
        // Persist BEFORE probing — same ordering lesson as RealInfraCouponEngine
        // (PLAN.md step 21): nothing here reads live traits mid-probe the way the
        // coupon controller does, but keeping the same order avoids reintroducing
        // that class of bug if a future step adds one.
        stateStore.save(current.sessionId, updated)
        // provisionTopic = false — the topic itself is reused (no recreation
        // overhead, ADR-0013's "provisionSchema=false" reasoning), but each
        // probe still measures against a brand-new consumer group (see
        // NotificationLoadRunner's class doc for why: an earlier version tried
        // carrying the consumer group forward too, and found that once a
        // severe incident backlogs the topic, every later probe's consumers
        // spend the whole window still draining that old backlog instead of
        // measuring the just-applied action).
        probeAndCache(current.sessionId, updatedTraits, current.incidentActive, provisionTopic = false)
        return updated
    }

    /**
     * Same [SimulationActionType]s as the rule-based notification engine, but
     * capped differently: real consumer parallelism can't exceed this
     * session's real partition count, so ADD_CONSUMERS steps by 1 (not the
     * rule-based engine's +8) and stops mattering past [maxConsumerCount].
     */
    private fun mutate(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
        SimulationActionType.ADD_CONSUMERS ->
            traits.copy(consumerCount = (traits.consumerCount + CONSUMER_STEP).coerceAtMost(maxConsumerCount))
        SimulationActionType.ENABLE_CIRCUIT_BREAKER -> traits.copy(circuitBreakerEnabled = true)
        SimulationActionType.ADJUST_RETRY_BACKOFF ->
            traits.copy(retryBackoffMultiplier = traits.retryBackoffMultiplier + RETRY_BACKOFF_STEP)
        else -> error("$action does not apply to the real-infra notification incident")
    }

    private fun probeAndCache(sessionId: UUID, traits: DesignTraits, incidentActive: Boolean, provisionTopic: Boolean): SystemState {
        val lock = sessionLocks.computeIfAbsent(sessionId) { Any() }
        synchronized(lock) {
            sessionTracker.touch(sessionId)
            if (provisionTopic) topicProvisioner.provision(sessionId)

            val cappedTraits = traits.copy(consumerCount = traits.consumerCount.coerceIn(MIN_CONSUMER_COUNT, maxConsumerCount))
            val summary = loadRunner.run(sessionId, cappedTraits, incidentActive)

            val state = SystemState(
                trafficRps = summary.achievedProducerRate,
                p95LatencyMs = summary.p95LatencyMs,
                errorRate = summary.errorRate,
                availability = 1.0 - summary.errorRate,
                dbReadLoad = 0.0,
                dbWriteLoad = 0.0,
                connectionPoolUsage = 0.0,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = summary.queueLagAtEnd,
                consumerThroughput = summary.consumerThroughput,
                externalDependencyLatencyMs = summary.processingDelayMs.toDouble(),
            )
            measurementStore.save(sessionId, state)
            return state
        }
    }

    companion object {
        /** Deliberately smaller than [DesignTraits.DEFAULT_CONSUMER_COUNT] (4) — a real-infra session should start visibly undersized so ADD_CONSUMERS has room to show an effect (same reasoning as [RealInfraCouponEngine.INITIAL_DB_POOL_SIZE]). */
        const val INITIAL_CONSUMER_COUNT = 1

        private const val MIN_CONSUMER_COUNT = 1
        private const val CONSUMER_STEP = 1
        private const val RETRY_BACKOFF_STEP = 4
    }
}
