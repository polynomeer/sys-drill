package com.sysdrill.backend.simulation

/**
 * The mutable inputs for one session's simulation, stored in Redis
 * (docs/ARCHITECTURE.md §9) and encoded/decoded by [SimulationSessionStateCodec].
 * [SystemState] itself is always recomputed from this, never stored.
 */
data class SimulationSessionState(
    val incidentActive: Boolean,
    val traits: DesignTraits,
)

/** "incidentActive|rateLimitEnabled|cacheTtlSeconds|dbPoolSize" — see EvaluationQueue for the same low-tech-on-purpose approach. */
object SimulationSessionStateCodec {

    fun encode(state: SimulationSessionState): String =
        listOf(
            state.incidentActive,
            state.traits.rateLimitEnabled,
            state.traits.cacheTtlSeconds,
            state.traits.dbPoolSize,
        ).joinToString("|")

    fun decode(raw: String): SimulationSessionState {
        val parts = raw.split("|")
        return SimulationSessionState(
            incidentActive = parts[0].toBoolean(),
            traits = DesignTraits(
                rateLimitEnabled = parts[1].toBoolean(),
                cacheTtlSeconds = parts[2].toInt(),
                dbPoolSize = parts[3].toInt(),
            ),
        )
    }
}
