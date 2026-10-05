package com.sysdrill.backend.simulation

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.max

/** One action on the incident's clock — when it was applied and what it was. */
data class TimedAction(val at: Instant, val action: SimulationActionType)

/**
 * One sample of the incident's telemetry.
 *
 * [state] carries the *accumulated* backlog in `queueLag` for the backlog
 * domains, so charts and the Mission Control bar show what's actually piled
 * up. [symptomLevel] is the level of the same instant **without** that
 * accumulation — "are requests failing / slow right now" — which is what the
 * health status is judged on; otherwise a backlog that's draining would keep
 * reading as CRITICAL long after every action that fixes it was applied
 * (`SystemState.cpuUtilization` treats `queueLag / 100` as load).
 */
data class TelemetryPoint(
    val at: Instant,
    val state: SystemState,
    val symptomLevel: String,
    val backlog: Long,
)

/** docs/OBSERVABILITY_UI_PLAN.md O1 — the five states the Mission Control bar shows. */
enum class HealthStatus { HEALTHY, DEGRADED, CRITICAL, RECOVERING, RECOVERED }

/**
 * ADR-0045 — the simulation's time axis, derived **outside** the per-domain
 * functions in [RuleBasedSimulationEngine]: those stay `state = f(traits,
 * incidentActive)` and their hand-calculated tests don't change. Time adds
 * exactly two generic effects here:
 *
 * 1. **Ramp-up** — for [RAMP_SECONDS] after the incident starts, every numeric
 *    field is linearly interpolated between the same traits' pre-incident and
 *    incident steady states. An incident that is instantly at full strength
 *    leaves no window to notice it coming, and alert rules with a duration
 *    (O5) would fire at t=0 every time.
 * 2. **Backlog accumulation** — for [BACKLOG_DOMAINS], whose steady-state
 *    `queueLag` is a per-second surplus (incoming − throughput over a one-second
 *    window), the backlog is integrated second by second: it grows by the
 *    surplus while there is one and drains at `consumerThroughput − trafficRps`
 *    once there isn't. The drain rate ignores retry amplification (the engine
 *    doesn't expose the amplified incoming rate) — an approximation that errs on
 *    the side of draining a bit fast. Other domains' `queueLag` (records to
 *    reprocess, crash-looping pods) isn't a rate, so it's only interpolated.
 *
 * Actions take effect from the second they were applied. Nothing is persisted
 * (ADR-0011) and nothing is random — the same inputs always produce the same
 * series, which replay and forks (ADR-0046) rely on.
 */
object TelemetrySampler {

    const val RAMP_SECONDS = 90L

    val BACKLOG_DOMAINS = setOf(
        RuleBasedSimulationEngine.DOMAIN_NOTIFICATION,
        RuleBasedSimulationEngine.DOMAIN_PAYMENT,
        RuleBasedSimulationEngine.DOMAIN_RESERVATION,
    )

    private val PLACEHOLDER_SESSION_ID = UUID(0, 0) // the rule-based engine never reads sessionId

    /**
     * Samples at every [step] from [from] through [to] (inclusive of [from];
     * [to] is included when it falls on the grid, and always appended as the
     * last point so "now" is never missing).
     */
    fun sample(
        domain: String,
        baseTraits: DesignTraits,
        incidentStartedAt: Instant,
        actions: List<TimedAction>,
        from: Instant,
        to: Instant,
        step: Duration,
    ): List<TelemetryPoint> {
        require(!step.isNegative && !step.isZero) { "step must be positive" }
        val times = buildList {
            var t = from
            while (t.isBefore(to)) {
                add(t)
                t = t.plus(step)
            }
            add(to)
        }
        return sampleAt(domain, baseTraits, incidentStartedAt, actions, times)
    }

    fun sampleAt(
        domain: String,
        baseTraits: DesignTraits,
        incidentStartedAt: Instant,
        actions: List<TimedAction>,
        times: List<Instant>,
    ): List<TelemetryPoint> {
        val sortedActions = actions.sortedBy { it.at }
        val backlogDomain = domain in BACKLOG_DOMAINS

        // Integrate the backlog once, a second at a time, up to the last
        // requested instant — independent of the sampling step, so asking for
        // a coarser series never changes the numbers.
        val lastSecond = times.maxOfOrNull { secondsAfter(incidentStartedAt, it) } ?: 0L
        val backlogBySecond = LongArray((max(lastSecond, 0L) + 1).toInt())
        if (backlogDomain) {
            var backlog = 0.0
            for (second in 1..max(lastSecond, 0L)) {
                val steady = steadyAt(domain, baseTraits, sortedActions, incidentStartedAt, second)
                backlog = if (steady.queueLag > 0) {
                    backlog + steady.queueLag
                } else {
                    max(0.0, backlog - max(0.0, steady.consumerThroughput - steady.trafficRps))
                }
                backlogBySecond[second.toInt()] = backlog.toLong()
            }
        }

        return times.map { at ->
            val second = secondsAfter(incidentStartedAt, at)
            if (second < 0) {
                val baseline = compute(domain, baseTraits, incidentActive = false)
                TelemetryPoint(at, baseline, baseline.level, backlog = 0)
            } else {
                // Actions are cut off at the sample's own instant, not the whole second it falls in —
                // otherwise an action applied a few hundred ms before "now" wouldn't show yet.
                val steady = steadyAt(domain, baseTraits, sortedActions, incidentStartedAt, second, actionCutoff = at)
                val backlog = if (backlogDomain) backlogBySecond[second.toInt()] else 0L
                val shown = if (backlogDomain) steady.copy(queueLag = backlog) else steady
                TelemetryPoint(at, shown, steady.level, backlog)
            }
        }
    }

    /**
     * docs/OBSERVABILITY_UI_PLAN.md §5-1 — the health status of each point,
     * judged on the symptom (not the accumulated backlog):
     * before the incident HEALTHY; ERROR → CRITICAL; WARN → DEGRADED, or
     * RECOVERING when both error rate and P95 are below where they were three
     * points ago; INFO → RECOVERING while a backlog remains, RECOVERED once it's
     * gone (if anything went wrong after the start), HEALTHY otherwise.
     */
    fun classify(points: List<TelemetryPoint>, incidentStartedAt: Instant): List<HealthStatus> {
        var troubled = false
        return points.mapIndexed { index, point ->
            if (point.at.isBefore(incidentStartedAt)) return@mapIndexed HealthStatus.HEALTHY
            when (point.symptomLevel) {
                "ERROR" -> {
                    troubled = true
                    if (improving(points, index)) HealthStatus.RECOVERING else HealthStatus.CRITICAL
                }
                "WARN" -> {
                    troubled = true
                    if (improving(points, index)) HealthStatus.RECOVERING else HealthStatus.DEGRADED
                }
                else -> when {
                    point.backlog > 0 -> HealthStatus.RECOVERING
                    troubled -> HealthStatus.RECOVERED
                    else -> HealthStatus.HEALTHY
                }
            }
        }
    }

    private fun improving(points: List<TelemetryPoint>, index: Int): Boolean {
        if (index < 3) return false
        val now = points[index].state
        val before = points[index - 3].state
        return now.errorRate < before.errorRate && now.p95LatencyMs < before.p95LatencyMs
    }

    /** The interpolated (ramp) steady state at [second] after the start, with every action applied by then. */
    private fun steadyAt(
        domain: String,
        baseTraits: DesignTraits,
        actions: List<TimedAction>,
        incidentStartedAt: Instant,
        second: Long,
        actionCutoff: Instant = incidentStartedAt.plusSeconds(second),
    ): SystemState {
        val folded = actions.filter { !it.at.isAfter(actionCutoff) }.fold(baseTraits) { traits, timed ->
            RuleBasedSimulationEngine.applyAction(session(domain, traits, incidentActive = true), timed.action).traits
        }
        // PLAN.md Round E30 — the one domain whose state moves with the clock: hand it the canary share at this second.
        val traits = if (domain == RuleBasedSimulationEngine.DOMAIN_DEPLOYMENT) {
            folded.copy(canaryPercent = RuleBasedSimulationEngine.deploymentRolloutAt(baseTraits, actions, incidentStartedAt, second, actionCutoff, ::rampFraction))
        } else {
            folded
        }
        val incident = compute(domain, traits, incidentActive = true)
        if (second >= RAMP_SECONDS) return incident
        val calm = compute(domain, traits, incidentActive = false)
        return lerp(calm, incident, rampFraction(second))
    }

    /** How far into the ramp [second] is — 0 at the start, 1 from [RAMP_SECONDS] on. */
    private fun rampFraction(second: Long): Double = (second.toDouble() / RAMP_SECONDS).coerceIn(0.0, 1.0)

    private fun compute(domain: String, traits: DesignTraits, incidentActive: Boolean): SystemState =
        RuleBasedSimulationEngine.computeState(session(domain, traits, incidentActive))

    private fun session(domain: String, traits: DesignTraits, incidentActive: Boolean) =
        SimulationSessionState(PLACEHOLDER_SESSION_ID, domain, incidentActive, traits)

    /** Whole seconds from the incident start (negative before it). */
    private fun secondsAfter(start: Instant, at: Instant): Long = Math.floorDiv(Duration.between(start, at).toMillis(), 1000L)

    private fun lerp(a: SystemState, b: SystemState, r: Double): SystemState {
        fun d(x: Double, y: Double) = x + (y - x) * r
        return SystemState(
            trafficRps = d(a.trafficRps, b.trafficRps),
            p95LatencyMs = d(a.p95LatencyMs, b.p95LatencyMs),
            errorRate = d(a.errorRate, b.errorRate),
            availability = d(a.availability, b.availability),
            dbReadLoad = d(a.dbReadLoad, b.dbReadLoad),
            dbWriteLoad = d(a.dbWriteLoad, b.dbWriteLoad),
            connectionPoolUsage = d(a.connectionPoolUsage, b.connectionPoolUsage),
            cacheHitRatio = d(a.cacheHitRatio, b.cacheHitRatio),
            cacheLatencyMs = d(a.cacheLatencyMs, b.cacheLatencyMs),
            queueLag = d(a.queueLag.toDouble(), b.queueLag.toDouble()).toLong(),
            consumerThroughput = d(a.consumerThroughput, b.consumerThroughput),
            externalDependencyLatencyMs = d(a.externalDependencyLatencyMs, b.externalDependencyLatencyMs),
        )
    }
}
