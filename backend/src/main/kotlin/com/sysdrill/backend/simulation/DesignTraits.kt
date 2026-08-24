package com.sysdrill.backend.simulation

/**
 * The tunable design/operational levers a user can adjust during a wargame
 * (docs/ARCHITECTURE.md §6's DesignTraits). ARCHITECTURE.md sketches a fuller
 * set of fields (hasIdempotency, retryPolicy, circuitBreaker, ...); this v1
 * only models the three this step's action handlers actually use
 * (docs/ARCHITECTURE.md §6.1 / PLAN.md step 4). Extend as later steps need
 * more levers rather than pre-building unused ones now.
 */
data class DesignTraits(
    val rateLimitEnabled: Boolean = false,
    val cacheTtlSeconds: Int = DEFAULT_CACHE_TTL_SECONDS,
    val dbPoolSize: Int = DEFAULT_DB_POOL_SIZE,
) {
    companion object {
        const val DEFAULT_CACHE_TTL_SECONDS = 10
        const val DEFAULT_DB_POOL_SIZE = 50
    }
}
