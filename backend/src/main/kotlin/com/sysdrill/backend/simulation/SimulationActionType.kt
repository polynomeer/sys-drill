package com.sysdrill.backend.simulation

/**
 * docs/ARCHITECTURE.md §6.1 action/effect pairs. Grouped by which incident
 * template (scenario domain) they apply to — [SimulationEngine.applyAction]
 * rejects an action that doesn't belong to the session's domain.
 */
enum class SimulationActionType {
    // coupon (PLAN.md step 4)
    STRENGTHEN_RATE_LIMIT,
    INCREASE_CACHE_TTL,
    INCREASE_DB_POOL,
    // notification (PLAN.md step 11)
    ADD_CONSUMERS,
    ENABLE_CIRCUIT_BREAKER,
    ADJUST_RETRY_BACKOFF,
    // product-browsing (PLAN.md step 11)
    SPLIT_CACHE_POLICY,
    ENABLE_SINGLE_FLIGHT,
    ADD_READ_REPLICA,
}
