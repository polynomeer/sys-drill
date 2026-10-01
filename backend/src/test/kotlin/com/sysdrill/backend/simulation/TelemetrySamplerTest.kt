package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ADR-0045 / PLAN.md Round E4 — the time axis is two generic effects layered
 * on the unchanged domain functions, so every number here is derived from the
 * same hand-calculated steady states [SimulationEngineTest] already pins.
 *
 * Notification (defaults: 4 consumers, no circuit breaker, backoff ×1):
 * - calm: 50 events/s, 4 × 1000/20ms = 200/s throughput → surplus 0
 * - incident: 500/s × (1 + 2/1 retry) = 1500/s vs 4 × 1000/300ms = 13.33/s → surplus 1486/s
 * - after ADD_CONSUMERS + ENABLE_CIRCUIT_BREAKER + ADJUST_RETRY_BACKOFF:
 *   500 × (1 + 2/5) = 700/s vs 12 × 1000/10ms = 1200/s → no surplus, drains at 1200 − 500 = 700/s
 */
class TelemetrySamplerTest {

    private val start = Instant.parse("2026-10-02T00:00:00Z")
    private val delta = Offset.offset(0.001)

    private fun at(second: Long) = start.plusSeconds(second)

    private fun sampleCoupon(vararg seconds: Long) = TelemetrySampler.sampleAt(
        RuleBasedSimulationEngine.DOMAIN_COUPON, DesignTraits(), start, emptyList(), seconds.map(::at),
    )

    private fun steady(domain: String, incidentActive: Boolean, traits: DesignTraits = DesignTraits()) =
        RuleBasedSimulationEngine.computeState(SimulationSessionState(UUID.randomUUID(), domain, incidentActive, traits))

    @Test
    fun `before the incident the series is the calm steady state`() {
        val point = sampleCoupon(-30).single()
        assertThat(point.state).isEqualTo(steady(RuleBasedSimulationEngine.DOMAIN_COUPON, incidentActive = false))
        assertThat(point.backlog).isZero()
    }

    @Test
    fun `halfway through the ramp every field is the midpoint`() {
        val point = sampleCoupon(45).single()
        // coupon: 300 → 6000 rps, P95 80ms (util 0.09) → 640ms (util 1.8 → ×8)
        assertThat(point.state.trafficRps).isCloseTo(3150.0, delta)
        assertThat(point.state.p95LatencyMs).isCloseTo(360.0, delta)
    }

    @Test
    fun `after the ramp a non-backlog domain is exactly the incident steady state`() {
        val point = sampleCoupon(TelemetrySampler.RAMP_SECONDS + 10).single()
        assertThat(point.state).isEqualTo(steady(RuleBasedSimulationEngine.DOMAIN_COUPON, incidentActive = true))
    }

    @Test
    fun `a backlog grows by the surplus each second and drains once the surplus is gone`() {
        val fixes = listOf(
            SimulationActionType.ADD_CONSUMERS,
            SimulationActionType.ENABLE_CIRCUIT_BREAKER,
            SimulationActionType.ADJUST_RETRY_BACKOFF,
        ).map { TimedAction(at(120), it) }
        val points = TelemetrySampler.sampleAt(
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, DesignTraits(), start, fixes,
            listOf(at(100), at(110), at(119), at(120), at(121), at(277), at(278)),
        ).associate { it.at to it.backlog }

        // Ramp: Σ floor(1486·k/90) for k=1..89, then 1486/s — Σ up to 119s = 110,663.
        assertThat(points[at(110)]!! - points[at(100)]!!).isEqualTo(14_860)
        assertThat(points[at(119)]).isEqualTo(110_663)
        // Fixed at 120s: drains 700/s from that second on.
        assertThat(points[at(120)]).isEqualTo(110_663 - 700)
        assertThat(points[at(121)]).isEqualTo(110_663 - 1_400)
        assertThat(points[at(277)]).isEqualTo(110_663 - 700 * 158) // 63 left
        assertThat(points[at(278)]).isZero()
    }

    @Test
    fun `the sampling step never changes the numbers`() {
        val coarse = TelemetrySampler.sample(
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, DesignTraits(), start, emptyList(),
            from = start, to = at(120), step = Duration.ofSeconds(30),
        ).associate { it.at to it.backlog }
        val fine = TelemetrySampler.sampleAt(
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, DesignTraits(), start, emptyList(), listOf(at(60), at(120)),
        ).associate { it.at to it.backlog }
        assertThat(coarse[at(60)]).isEqualTo(fine[at(60)])
        assertThat(coarse[at(120)]).isEqualTo(fine[at(120)])
    }

    @Test
    fun `status moves from critical to recovering while the backlog drains, then recovered`() {
        val fixes = listOf(
            SimulationActionType.ADD_CONSUMERS,
            SimulationActionType.ENABLE_CIRCUIT_BREAKER,
            SimulationActionType.ADJUST_RETRY_BACKOFF,
        ).map { TimedAction(at(120), it) }
        val points = TelemetrySampler.sample(
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, DesignTraits(), start, fixes,
            from = start.minusSeconds(60), to = at(300), step = Duration.ofSeconds(10),
        )
        val statusAt = points.zip(TelemetrySampler.classify(points, start)).associate { (p, s) -> p.at to s }

        assertThat(statusAt[at(-60)]).isEqualTo(HealthStatus.HEALTHY)
        assertThat(statusAt[at(100)]).isEqualTo(HealthStatus.CRITICAL)
        // Symptoms are gone the moment the fixes land, but 110k events are still queued.
        assertThat(statusAt[at(130)]).isEqualTo(HealthStatus.RECOVERING)
        assertThat(statusAt[at(300)]).isEqualTo(HealthStatus.RECOVERED)
    }

    @Test
    fun `a domain that never degrades stays healthy`() {
        val noTrouble = DesignTraits(consumerCount = 100, circuitBreakerEnabled = true, retryBackoffMultiplier = 10)
        val points = TelemetrySampler.sample(
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, noTrouble, start, emptyList(),
            from = start, to = at(200), step = Duration.ofSeconds(50),
        )
        assertThat(TelemetrySampler.classify(points, start)).containsOnly(HealthStatus.HEALTHY)
    }
}
