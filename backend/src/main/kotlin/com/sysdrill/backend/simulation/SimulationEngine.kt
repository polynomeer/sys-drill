package com.sysdrill.backend.simulation

/**
 * PLAN.md step 21 seam: lets a real-infra alternative
 * ([com.sysdrill.backend.simulation.realinfra.RealInfraCouponEngine]) stand in
 * for the pure-math [RuleBasedSimulationEngine] on a per-session basis,
 * without [SimulationService] needing to know which one it's talking to.
 */
interface SimulationEngine {
    fun computeState(session: SimulationSessionState): SystemState
    fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState
}
