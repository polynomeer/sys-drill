package com.sysdrill.backend.simulation

import com.sysdrill.backend.mission.AlertRule
import com.sysdrill.backend.mission.SloTargets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * PLAN.md Round E12 (O5/M3) — alerts and SLO over the notification incident, sampled every
 * second. The crossings are hand-derived from [TelemetrySamplerTest]'s numbers:
 * - error rate ramps 0.1% → 30% over 90s: 0.1 + 29.9·k/90 > 5 first at k = 15
 * - backlog Σ⌊1486k/90⌋ > 1000 first at k = 11
 * - fixes at 120s: error rate back to 0.1% at once; backlog 110,663 drains 700/s → ≤ 1000 at 276s
 */
class ObservabilityEvaluatorTest {

    private val start = Instant.parse("2026-10-02T00:00:00Z")
    private fun at(second: Long) = start.plusSeconds(second)

    private val fixes = listOf(
        SimulationActionType.ADD_CONSUMERS,
        SimulationActionType.ENABLE_CIRCUIT_BREAKER,
        SimulationActionType.ADJUST_RETRY_BACKOFF,
    ).map { TimedAction(at(120), it) }

    private val points = TelemetrySampler.sample(
        RuleBasedSimulationEngine.DOMAIN_NOTIFICATION, DesignTraits(), start, fixes,
        from = at(-30), to = at(300), step = Duration.ofSeconds(1),
    )

    private fun rule(id: String, metric: String, op: String, threshold: Double, forSeconds: Int, createdAt: Instant = at(-60)) =
        AlertRule(id, metric, op, threshold, forSeconds, "WARN", createdAt)

    @Test
    fun `a rule fires once its condition has held for the duration, and resolves when it stops`() {
        val summary = ObservabilityEvaluator.evaluate(
            points, start,
            listOf(rule("err", "errorRatePct", ">", 5.0, 60), rule("lag", "backlog", ">", 1000.0, 60)),
            SloTargets(),
        )
        val err = summary.alerts.single { it.ruleId == "err" }
        assertThat(err.firedAt).isEqualTo(at(75))
        assertThat(err.resolvedAt).isEqualTo(at(120))
        assertThat(err.falseAlarm).isFalse()

        val lag = summary.alerts.single { it.ruleId == "lag" }
        assertThat(lag.firedAt).isEqualTo(at(71))
        assertThat(lag.resolvedAt).isEqualTo(at(276))
    }

    @Test
    fun `a rule written mid-incident only counts from when it existed`() {
        val late = ObservabilityEvaluator.evaluate(points, start, listOf(rule("late", "errorRatePct", ">", 5.0, 60, createdAt = at(30))), SloTargets())
        assertThat(late.alerts.single().firedAt).isEqualTo(at(90))
    }

    @Test
    fun `a threshold below normal fires before the incident and is a false alarm`() {
        val noisy = ObservabilityEvaluator.evaluate(points, start, listOf(rule("noisy", "errorRatePct", ">", 0.05, 10)), SloTargets())
        assertThat(noisy.alerts.first().firedAt).isEqualTo(at(-20))
        assertThat(noisy.alerts.first().falseAlarm).isTrue()
    }

    @Test
    fun `the SLO compares the latest point and spends error budget during the incident`() {
        val midIncident = points.filter { !it.at.isAfter(at(100)) }
        val during = ObservabilityEvaluator.evaluate(midIncident, start, emptyList(), SloTargets(99.9, 500.0, 1.0)).slo!!
        assertThat(during.availabilityMet).isFalse()
        assertThat(during.errorRateMet).isFalse()
        assertThat(during.p95Met).isFalse()
        // Σ errorRate·dt over 0..100s, 1s steps
        val expected = midIncident.filter { !it.at.isBefore(start) }.zipWithNext().sumOf { (a, _) -> a.state.errorRate }
        assertThat(during.budgetSpentSeconds).isCloseTo(expected, Offset.offset(1e-6))
        assertThat(during.monthlyBudgetSeconds).isCloseTo(0.001 * 30 * 86_400, Offset.offset(1e-6))
        assertThat(during.burnRate).isGreaterThan(100.0) // ~20% average errors vs 0.1% allowed

        val after = ObservabilityEvaluator.evaluate(points, start, emptyList(), SloTargets(99.9, 500.0, 1.0)).slo!!
        assertThat(after.errorRateMet).isTrue()
        assertThat(after.p95Met).isTrue()
    }
}
