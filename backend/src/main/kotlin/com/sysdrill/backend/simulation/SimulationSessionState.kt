package com.sysdrill.backend.simulation

import java.util.UUID

/**
 * The mutable inputs for one session's simulation, stored in Redis
 * (docs/ARCHITECTURE.md §9) and encoded/decoded by [SimulationSessionStateCodec].
 * [SystemState] itself is always recomputed from this, never stored.
 *
 * [sessionId] is NOT part of the encoded string — it's already the Redis key,
 * so [SimulationSessionStateCodec.decode] takes it as a separate parameter and
 * [SimulationStateStore] fills it in on both save and find. It exists on this
 * data class (rather than being threaded as a separate argument everywhere)
 * because PLAN.md step 21's real-infra engine needs to know which session's
 * schema/pool/cache to touch, while [RuleBasedSimulationEngine] ignores it.
 */
data class SimulationSessionState(
    val sessionId: UUID,
    val domain: String,
    val incidentActive: Boolean,
    val traits: DesignTraits,
    val engineMode: EngineMode = EngineMode.RULE_BASED,
)

/** PLAN.md step 21 — which [SimulationEngine] implementation serves this session. */
enum class EngineMode {
    RULE_BASED,
    REAL_INFRA,
}

/** "domain|incidentActive|rateLimitEnabled|cacheTtlSeconds|dbPoolSize|consumerCount|circuitBreakerEnabled|retryBackoffMultiplier|cachePolicySplit|singleFlightEnabled|readReplicaCount|dispatcherWorkers|idempotentPgRetryEnabled|paymentPoolIsolated|fineGrainedLockingEnabled|holdTimeoutSeconds|atomicInventoryCheckEnabled|checkpointingEnabled|chunkSize|idempotentReconciliationEnabled|podReplicas|resourceLimitsTuned|rolloutSafeguardEnabled|engineMode" — see EvaluationQueue for the same low-tech-on-purpose approach. sessionId is deliberately excluded (see [SimulationSessionState] doc) and supplied to [decode] separately. */
object SimulationSessionStateCodec {

    fun encode(state: SimulationSessionState): String =
        listOf(
            state.domain,
            state.incidentActive,
            state.traits.rateLimitEnabled,
            state.traits.cacheTtlSeconds,
            state.traits.dbPoolSize,
            state.traits.consumerCount,
            state.traits.circuitBreakerEnabled,
            state.traits.retryBackoffMultiplier,
            state.traits.cachePolicySplit,
            state.traits.singleFlightEnabled,
            state.traits.readReplicaCount,
            state.traits.dispatcherWorkers,
            state.traits.idempotentPgRetryEnabled,
            state.traits.paymentPoolIsolated,
            state.traits.fineGrainedLockingEnabled,
            state.traits.holdTimeoutSeconds,
            state.traits.atomicInventoryCheckEnabled,
            state.traits.checkpointingEnabled,
            state.traits.chunkSize,
            state.traits.idempotentReconciliationEnabled,
            state.traits.podReplicas,
            state.traits.resourceLimitsTuned,
            state.traits.rolloutSafeguardEnabled,
            state.engineMode.name,
        ).joinToString("|")

    fun decode(sessionId: UUID, raw: String): SimulationSessionState {
        val parts = raw.split("|")
        return SimulationSessionState(
            sessionId = sessionId,
            domain = parts[0],
            incidentActive = parts[1].toBoolean(),
            traits = DesignTraits(
                rateLimitEnabled = parts[2].toBoolean(),
                cacheTtlSeconds = parts[3].toInt(),
                dbPoolSize = parts[4].toInt(),
                consumerCount = parts[5].toInt(),
                circuitBreakerEnabled = parts[6].toBoolean(),
                retryBackoffMultiplier = parts[7].toInt(),
                cachePolicySplit = parts[8].toBoolean(),
                singleFlightEnabled = parts[9].toBoolean(),
                readReplicaCount = parts[10].toInt(),
                dispatcherWorkers = parts[11].toInt(),
                idempotentPgRetryEnabled = parts[12].toBoolean(),
                paymentPoolIsolated = parts[13].toBoolean(),
                fineGrainedLockingEnabled = parts[14].toBoolean(),
                holdTimeoutSeconds = parts[15].toInt(),
                atomicInventoryCheckEnabled = parts[16].toBoolean(),
                checkpointingEnabled = parts[17].toBoolean(),
                chunkSize = parts[18].toInt(),
                idempotentReconciliationEnabled = parts[19].toBoolean(),
                podReplicas = parts[20].toInt(),
                resourceLimitsTuned = parts[21].toBoolean(),
                rolloutSafeguardEnabled = parts[22].toBoolean(),
            ),
            engineMode = EngineMode.valueOf(parts[23]),
        )
    }
}
