package com.sysdrill.backend.simulation

import com.sysdrill.backend.mission.AlertRule
import com.sysdrill.backend.mission.SloTargets
import java.time.Duration
import java.time.Instant

/** One metric an alert rule may watch, read off a [TelemetryPoint] in the unit the UI shows. */
data class AlertMetric(val key: String, val label: String, val unit: String, val read: (TelemetryPoint) -> Double)

/**
 * docs/OBSERVABILITY_UI_PLAN.md O5 (PLAN.md Round E12) — alert rules and SLO, judged over
 * the incident's time series (ADR-0045). Pure: same series and rules → same alerts, so a
 * spectator, a reload and the postmortem all agree.
 */
object ObservabilityEvaluator {

    val METRICS: List<AlertMetric> = listOf(
        AlertMetric("errorRatePct", "에러율", "%") { it.state.errorRate * 100 },
        AlertMetric("p95LatencyMs", "P95 지연", "ms") { it.state.p95LatencyMs },
        AlertMetric("trafficRps", "트래픽", "rps") { it.state.trafficRps },
        AlertMetric("availabilityPct", "가용성", "%") { it.state.availability * 100 },
        AlertMetric("backlog", "적체", "건") { maxOf(it.backlog, it.state.queueLag).toDouble() },
        AlertMetric("dbReadLoadPct", "DB 읽기 사용률", "%") { it.state.dbReadLoad * 100 },
        AlertMetric("dbWriteLoadPct", "DB 쓰기 사용률", "%") { it.state.dbWriteLoad * 100 },
        AlertMetric("connectionPoolPct", "커넥션 풀 사용률", "%") { it.state.connectionPoolUsage * 100 },
        AlertMetric("cacheHitPct", "캐시 hit ratio", "%") { it.state.cacheHitRatio * 100 },
        AlertMetric("saturationPct", "Saturation", "%") { it.state.cpuUtilization * 100 },
    )
    private val metricsByKey = METRICS.associateBy { it.key }

    fun isKnownMetric(key: String): Boolean = key in metricsByKey

    /**
     * Rules that fit the domain — offered as a starting point, never enabled for the
     * learner. Thresholds are deliberately round, not tuned to fire at the "right" moment.
     */
    fun suggestedRules(domain: String): List<AlertRule> = buildList {
        add(AlertRule("suggest-errors", "errorRatePct", ">", 5.0, 60, "CRITICAL"))
        add(AlertRule("suggest-latency", "p95LatencyMs", ">", 1000.0, 120, "WARN"))
        when (domain) {
            RuleBasedSimulationEngine.DOMAIN_COUPON -> add(AlertRule("suggest-db", "dbWriteLoadPct", ">", 90.0, 60, "WARN"))
            RuleBasedSimulationEngine.DOMAIN_PRODUCT_BROWSING -> add(AlertRule("suggest-cache", "cacheHitPct", "<", 70.0, 60, "WARN"))
            RuleBasedSimulationEngine.DOMAIN_PAYMENT -> add(AlertRule("suggest-pool", "connectionPoolPct", ">", 80.0, 60, "WARN"))
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION,
            RuleBasedSimulationEngine.DOMAIN_RESERVATION -> add(AlertRule("suggest-backlog", "backlog", ">", 1000.0, 60, "WARN"))
            else -> add(AlertRule("suggest-saturation", "saturationPct", ">", 90.0, 120, "WARN"))
        }
    }

    fun evaluate(
        points: List<TelemetryPoint>,
        incidentStartedAt: Instant,
        rules: List<AlertRule>,
        slo: SloTargets,
    ): ObservabilitySummary = ObservabilitySummary(
        alerts = rules.flatMap { alertsFor(points, incidentStartedAt, it) }.sortedBy { it.firedAt },
        slo = sloStatus(points, incidentStartedAt, slo),
    )

    /**
     * A rule fires once its condition has held continuously for `forSeconds` — counting
     * only from when the rule existed — and resolves when the condition stops holding.
     * A rule can fire more than once. Firing before the incident started is a false alarm.
     */
    private fun alertsFor(points: List<TelemetryPoint>, incidentStartedAt: Instant, rule: AlertRule): List<AlertEvent> {
        val metric = metricsByKey[rule.metric] ?: return emptyList()
        val events = mutableListOf<AlertEvent>()
        var since: Instant? = null
        var firing: AlertEvent? = null
        for (point in points) {
            val value = metric.read(point)
            val holds = if (rule.op == "<") value < rule.threshold else value > rule.threshold
            val active = rule.createdAt == null || !point.at.isBefore(rule.createdAt)
            if (holds && active) {
                if (since == null) since = point.at
                if (firing == null && Duration.between(since, point.at).seconds >= rule.forSeconds) {
                    firing = AlertEvent(
                        ruleId = rule.id,
                        metric = rule.metric,
                        label = metric.label,
                        unit = metric.unit,
                        op = rule.op,
                        threshold = rule.threshold,
                        severity = rule.severity,
                        firedAt = point.at,
                        resolvedAt = null,
                        value = value,
                        falseAlarm = point.at.isBefore(incidentStartedAt),
                    )
                }
                firing = firing?.copy(value = value)
            } else {
                firing?.let { events += it.copy(resolvedAt = point.at) }
                firing = null
                since = null
            }
        }
        firing?.let { events += it }
        return events
    }

    /**
     * docs/DRILLS_EXPANSION_PLAN.md M3 — the latest point against each target, plus the error
     * budget: errors spent during the incident (Σ errorRate·dt, i.e. seconds of total outage
     * equivalent) against a 30-day budget, and the burn rate (incident error rate ÷ allowed rate).
     */
    private fun sloStatus(points: List<TelemetryPoint>, incidentStartedAt: Instant, slo: SloTargets): SloStatus? {
        val latest = points.lastOrNull() ?: return null
        val during = points.filter { !it.at.isBefore(incidentStartedAt) }
        var spent = 0.0
        during.zipWithNext().forEach { (a, b) -> spent += a.state.errorRate * Duration.between(a.at, b.at).toMillis() / 1000.0 }
        val elapsed = if (during.size >= 2) Duration.between(during.first().at, during.last().at).toMillis() / 1000.0 else 0.0
        val allowed = (1 - slo.availabilityPct / 100).coerceAtLeast(1e-9)
        return SloStatus(
            targets = slo,
            availabilityMet = latest.state.availability * 100 >= slo.availabilityPct,
            p95Met = latest.state.p95LatencyMs <= slo.p95Ms,
            errorRateMet = latest.state.errorRate * 100 <= slo.errorRatePct,
            budgetSpentSeconds = spent,
            monthlyBudgetSeconds = allowed * 30 * 86_400,
            burnRate = if (elapsed > 0) (spent / elapsed) / allowed else 0.0,
        )
    }
}

data class AlertEvent(
    val ruleId: String,
    val metric: String,
    val label: String,
    val unit: String,
    val op: String,
    val threshold: Double,
    val severity: String,
    val firedAt: Instant,
    /** Null while still firing. */
    val resolvedAt: Instant?,
    /** The metric's latest value while firing (at resolution, the last firing value). */
    val value: Double,
    /** Fired before the incident started — the threshold is too sensitive. */
    val falseAlarm: Boolean,
)

data class SloStatus(
    val targets: SloTargets,
    val availabilityMet: Boolean,
    val p95Met: Boolean,
    val errorRateMet: Boolean,
    val budgetSpentSeconds: Double,
    val monthlyBudgetSeconds: Double,
    val burnRate: Double,
)

data class ObservabilitySummary(val alerts: List<AlertEvent>, val slo: SloStatus?)
