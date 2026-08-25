package com.sysdrill.backend.simulation

/**
 * The tunable design/operational levers a user can adjust during a wargame
 * (docs/ARCHITECTURE.md §6's DesignTraits). ARCHITECTURE.md sketches a fuller
 * set of fields; PLAN.md step 4 started with only the three the "coupon"
 * incident's actions use, and step 11 adds one group of fields per new
 * incident template (notification, product-browsing) rather than a generic
 * key-value bag — each field still maps 1:1 to a concrete [SimulationActionType].
 */
data class DesignTraits(
    // coupon
    val rateLimitEnabled: Boolean = false,
    val cacheTtlSeconds: Int = DEFAULT_CACHE_TTL_SECONDS,
    val dbPoolSize: Int = DEFAULT_DB_POOL_SIZE,
    // notification
    val consumerCount: Int = DEFAULT_CONSUMER_COUNT,
    val circuitBreakerEnabled: Boolean = false,
    val retryBackoffMultiplier: Int = DEFAULT_RETRY_BACKOFF_MULTIPLIER,
    // product-browsing
    val cachePolicySplit: Boolean = false,
    val singleFlightEnabled: Boolean = false,
    val readReplicaCount: Int = 0,
) {
    companion object {
        const val DEFAULT_CACHE_TTL_SECONDS = 10
        const val DEFAULT_DB_POOL_SIZE = 50
        const val DEFAULT_CONSUMER_COUNT = 4
        const val DEFAULT_RETRY_BACKOFF_MULTIPLIER = 1
    }
}
