package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

/**
 * Pure math tests for the "선착순 쿠폰" incident template — no Spring context,
 * no DB, no Redis. Expected numbers are hand-derived from
 * SimulationEngine's constants; see PLAN.md step 4 notes for the worked
 * calculation this mirrors.
 */
class SimulationEngineTest {

    private val delta = Offset.offset(0.001)

    @Test
    fun `baseline traffic with no incident is stable`() {
        val state = SimulationEngine.computeState(SimulationSessionState(incidentActive = false, traits = DesignTraits()))

        assertThat(state.trafficRps).isEqualTo(300.0)
        assertThat(state.dbWriteLoad).isCloseTo(0.09, delta)
        assertThat(state.p95LatencyMs).isCloseTo(80.0, delta)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
    }

    @Test
    fun `the coupon incident overloads both read and write paths with default traits`() {
        val state = SimulationEngine.computeState(SimulationSessionState(incidentActive = true, traits = DesignTraits()))

        assertThat(state.trafficRps).isEqualTo(6000.0)
        assertThat(state.dbReadLoad).isCloseTo(0.8547, Offset.offset(0.001))
        assertThat(state.dbWriteLoad).isCloseTo(1.8, delta)
        // overall utilization is driven by the worse of the two axes (write, at 1.8)
        assertThat(state.errorRate).isCloseTo(0.30, delta)
        assertThat(state.p95LatencyMs).isCloseTo(640.0, delta)
    }

    @Test
    fun `strengthening the rate limit fixes the read axis and partially recovers writes`() {
        var session = SimulationSessionState(incidentActive = true, traits = DesignTraits())
        session = SimulationEngine.applyAction(session, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        val state = SimulationEngine.computeState(session)

        assertThat(state.trafficRps).isEqualTo(3000.0)
        assertThat(state.dbReadLoad).isLessThan(0.6) // recovered
        assertThat(state.dbWriteLoad).isCloseTo(0.9, delta) // improved from 1.8, but not yet healthy
    }

    @Test
    fun `increasing cache TTL only affects the read axis, not writes`() {
        var session = SimulationSessionState(incidentActive = true, traits = DesignTraits(rateLimitEnabled = true))
        val before = SimulationEngine.computeState(session)

        session = SimulationEngine.applyAction(session, SimulationActionType.INCREASE_CACHE_TTL)
        val after = SimulationEngine.computeState(session)

        assertThat(after.cacheHitRatio).isGreaterThan(before.cacheHitRatio)
        assertThat(after.dbReadLoad).isLessThan(before.dbReadLoad)
        assertThat(after.dbWriteLoad).isCloseTo(before.dbWriteLoad, delta) // unaffected
    }

    @Test
    fun `all three actions together fully recover the session to a stable state`() {
        var session = SimulationSessionState(incidentActive = true, traits = DesignTraits())
        session = SimulationEngine.applyAction(session, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        session = SimulationEngine.applyAction(session, SimulationActionType.INCREASE_CACHE_TTL)
        session = SimulationEngine.applyAction(session, SimulationActionType.INCREASE_DB_POOL)

        val state = SimulationEngine.computeState(session)

        assertThat(state.dbReadLoad).isLessThan(0.6)
        assertThat(state.dbWriteLoad).isLessThan(0.6)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
        assertThat(state.p95LatencyMs).isCloseTo(80.0, delta)
    }

    @Test
    fun `utilization bands match docs ARCHITECTURE md section 6`() {
        assertThat(SimulationEngine.latencyMultiplier(0.3)).isEqualTo(1.0)
        assertThat(SimulationEngine.latencyMultiplier(0.7)).isEqualTo(1.5)
        assertThat(SimulationEngine.latencyMultiplier(0.9)).isEqualTo(3.0)
        assertThat(SimulationEngine.latencyMultiplier(0.99)).isEqualTo(5.0)
        assertThat(SimulationEngine.latencyMultiplier(1.5)).isEqualTo(8.0)

        assertThat(SimulationEngine.errorRateFor(0.3)).isCloseTo(0.001, delta)
        assertThat(SimulationEngine.errorRateFor(1.5)).isCloseTo(0.30, delta)
    }
}
