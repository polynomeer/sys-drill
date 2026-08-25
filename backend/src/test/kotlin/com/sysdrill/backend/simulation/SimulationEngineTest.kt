package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

/**
 * Pure math tests for all three incident templates — no Spring context, no
 * DB, no Redis. Expected numbers are hand-derived from SimulationEngine's
 * constants; see PLAN.md step 4 (coupon) and step 11 (notification,
 * product-browsing) notes for the worked calculations these mirror.
 */
class SimulationEngineTest {

    private val delta = Offset.offset(0.001)

    // ---- coupon (PLAN.md step 4) ----

    @Test
    fun `baseline traffic with no incident is stable`() {
        val state = SimulationEngine.computeState(couponSession(incidentActive = false))

        assertThat(state.trafficRps).isEqualTo(300.0)
        assertThat(state.dbWriteLoad).isCloseTo(0.09, delta)
        assertThat(state.p95LatencyMs).isCloseTo(80.0, delta)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
    }

    @Test
    fun `the coupon incident overloads both read and write paths with default traits`() {
        val state = SimulationEngine.computeState(couponSession(incidentActive = true))

        assertThat(state.trafficRps).isEqualTo(6000.0)
        assertThat(state.dbReadLoad).isCloseTo(0.8547, Offset.offset(0.001))
        assertThat(state.dbWriteLoad).isCloseTo(1.8, delta)
        // overall utilization is driven by the worse of the two axes (write, at 1.8)
        assertThat(state.errorRate).isCloseTo(0.30, delta)
        assertThat(state.p95LatencyMs).isCloseTo(640.0, delta)
    }

    @Test
    fun `strengthening the rate limit fixes the read axis and partially recovers writes`() {
        var session = couponSession(incidentActive = true)
        session = SimulationEngine.applyAction(session, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        val state = SimulationEngine.computeState(session)

        assertThat(state.trafficRps).isEqualTo(3000.0)
        assertThat(state.dbReadLoad).isLessThan(0.6) // recovered
        assertThat(state.dbWriteLoad).isCloseTo(0.9, delta) // improved from 1.8, but not yet healthy
    }

    @Test
    fun `increasing cache TTL only affects the read axis, not writes`() {
        var session = couponSession(incidentActive = true, traits = DesignTraits(rateLimitEnabled = true))
        val before = SimulationEngine.computeState(session)

        session = SimulationEngine.applyAction(session, SimulationActionType.INCREASE_CACHE_TTL)
        val after = SimulationEngine.computeState(session)

        assertThat(after.cacheHitRatio).isGreaterThan(before.cacheHitRatio)
        assertThat(after.dbReadLoad).isLessThan(before.dbReadLoad)
        assertThat(after.dbWriteLoad).isCloseTo(before.dbWriteLoad, delta) // unaffected
    }

    @Test
    fun `all three coupon actions together fully recover the session to a stable state`() {
        var session = couponSession(incidentActive = true)
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
    fun `an out-of-domain action is rejected`() {
        assertThatThrownBy {
            SimulationEngine.applyAction(couponSession(incidentActive = true), SimulationActionType.ADD_CONSUMERS)
        }.isInstanceOf(IllegalStateException::class.java)
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

    // ---- notification (PLAN.md step 11 — provider timeout -> retry storm -> consumer lag) ----

    @Test
    fun `baseline notification traffic is stable`() {
        val state = SimulationEngine.computeState(notificationSession(incidentActive = false))

        assertThat(state.trafficRps).isEqualTo(50.0)
        assertThat(state.consumerThroughput).isCloseTo(200.0, delta) // 4 consumers * (1000/20ms)
        assertThat(state.queueLag).isEqualTo(0)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
    }

    @Test
    fun `the notification incident collapses consumer throughput and backs up the queue`() {
        val state = SimulationEngine.computeState(notificationSession(incidentActive = true))

        assertThat(state.trafficRps).isEqualTo(500.0)
        assertThat(state.consumerThroughput).isCloseTo(13.333, Offset.offset(0.01)) // 4 * (1000/300ms)
        assertThat(state.queueLag).isGreaterThan(0)
        assertThat(state.errorRate).isCloseTo(0.30, delta)
    }

    @Test
    fun `no single notification action alone fully recovers the session`() {
        val breakerOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(notificationSession(incidentActive = true), SimulationActionType.ENABLE_CIRCUIT_BREAKER)
        )
        val consumersOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(notificationSession(incidentActive = true), SimulationActionType.ADD_CONSUMERS)
        )
        val backoffOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(notificationSession(incidentActive = true), SimulationActionType.ADJUST_RETRY_BACKOFF)
        )

        assertThat(breakerOnly.errorRate).isCloseTo(0.30, delta)
        assertThat(consumersOnly.errorRate).isCloseTo(0.30, delta)
        assertThat(backoffOnly.errorRate).isCloseTo(0.30, delta)
    }

    @Test
    fun `all three notification actions together recover the session`() {
        var session = notificationSession(incidentActive = true)
        session = SimulationEngine.applyAction(session, SimulationActionType.ENABLE_CIRCUIT_BREAKER)
        session = SimulationEngine.applyAction(session, SimulationActionType.ADD_CONSUMERS)
        session = SimulationEngine.applyAction(session, SimulationActionType.ADJUST_RETRY_BACKOFF)

        val state = SimulationEngine.computeState(session)

        assertThat(state.errorRate).isCloseTo(0.001, delta)
        assertThat(state.p95LatencyMs).isCloseTo(300.0, delta)
        assertThat(state.queueLag).isEqualTo(0)
    }

    // ---- product-browsing (PLAN.md step 11 — hot key cache stampede -> DB read overload) ----

    @Test
    fun `baseline product-browsing traffic is stable`() {
        val state = SimulationEngine.computeState(productBrowsingSession(incidentActive = false))

        assertThat(state.trafficRps).isEqualTo(500.0)
        assertThat(state.cacheHitRatio).isCloseTo(0.9, delta)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
    }

    @Test
    fun `the product-browsing incident craters the cache hit ratio and overloads DB reads`() {
        val state = SimulationEngine.computeState(productBrowsingSession(incidentActive = true))

        assertThat(state.trafficRps).isEqualTo(10000.0)
        assertThat(state.cacheHitRatio).isCloseTo(0.2, delta)
        assertThat(state.dbReadLoad).isCloseTo(40.0, delta)
        assertThat(state.errorRate).isCloseTo(0.30, delta)
    }

    @Test
    fun `no single product-browsing action alone fully recovers the session`() {
        val splitOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(productBrowsingSession(incidentActive = true), SimulationActionType.SPLIT_CACHE_POLICY)
        )
        val singleFlightOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(productBrowsingSession(incidentActive = true), SimulationActionType.ENABLE_SINGLE_FLIGHT)
        )
        val replicaOnly = SimulationEngine.computeState(
            SimulationEngine.applyAction(productBrowsingSession(incidentActive = true), SimulationActionType.ADD_READ_REPLICA)
        )

        assertThat(splitOnly.errorRate).isCloseTo(0.30, delta)
        assertThat(singleFlightOnly.errorRate).isCloseTo(0.30, delta)
        assertThat(replicaOnly.errorRate).isCloseTo(0.30, delta)
    }

    @Test
    fun `all three product-browsing actions together recover the session`() {
        var session = productBrowsingSession(incidentActive = true)
        session = SimulationEngine.applyAction(session, SimulationActionType.SPLIT_CACHE_POLICY)
        session = SimulationEngine.applyAction(session, SimulationActionType.ENABLE_SINGLE_FLIGHT)
        session = SimulationEngine.applyAction(session, SimulationActionType.ADD_READ_REPLICA)

        val state = SimulationEngine.computeState(session)

        assertThat(state.dbReadLoad).isCloseTo(0.5, delta)
        assertThat(state.errorRate).isCloseTo(0.001, delta)
        assertThat(state.p95LatencyMs).isCloseTo(60.0, delta)
    }

    private fun couponSession(incidentActive: Boolean, traits: DesignTraits = DesignTraits()) =
        SimulationSessionState(domain = SimulationEngine.DOMAIN_COUPON, incidentActive = incidentActive, traits = traits)

    private fun notificationSession(incidentActive: Boolean, traits: DesignTraits = DesignTraits()) =
        SimulationSessionState(domain = SimulationEngine.DOMAIN_NOTIFICATION, incidentActive = incidentActive, traits = traits)

    private fun productBrowsingSession(incidentActive: Boolean, traits: DesignTraits = DesignTraits()) =
        SimulationSessionState(domain = SimulationEngine.DOMAIN_PRODUCT_BROWSING, incidentActive = incidentActive, traits = traits)
}
