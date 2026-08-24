package com.sysdrill.backend.simulation

/** docs/ARCHITECTURE.md §6.1 — the first three action/effect pairs (PLAN.md step 4). */
enum class SimulationActionType {
    STRENGTHEN_RATE_LIMIT,
    INCREASE_CACHE_TTL,
    INCREASE_DB_POOL,
}
