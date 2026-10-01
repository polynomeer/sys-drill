package com.sysdrill.backend.simulation

import jakarta.validation.constraints.NotNull
import java.time.Instant

data class SystemStateResponse(
    val trafficRps: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
    val availability: Double,
    val dbReadLoad: Double,
    val dbWriteLoad: Double,
    val connectionPoolUsage: Double,
    val cacheHitRatio: Double,
    val cacheLatencyMs: Double,
    val queueLag: Long,
    val consumerThroughput: Double,
    val externalDependencyLatencyMs: Double,
    val cpuUtilization: Double,
    val memoryUtilization: Double,
    /** Phase 3-A (docs/DRILLS_SIMULATION_VISION.md §6) — see [SystemState.level]. */
    val level: String,
    /** AI 4역할 Slice 4 (Director) — LLM narration generated once, on a fresh (non-idempotent-replay) incident start, rule-based sessions only. Null everywhere else ([getState]/[applyAction]/[getTimeline], real-infra sessions, or on LLM failure — fail-open, see [SimulationService.startIncident]). */
    val narration: String? = null,
) {
    companion object {
        fun from(state: SystemState, narration: String? = null) = SystemStateResponse(
            trafficRps = state.trafficRps,
            p95LatencyMs = state.p95LatencyMs,
            errorRate = state.errorRate,
            availability = state.availability,
            dbReadLoad = state.dbReadLoad,
            dbWriteLoad = state.dbWriteLoad,
            connectionPoolUsage = state.connectionPoolUsage,
            cacheHitRatio = state.cacheHitRatio,
            cacheLatencyMs = state.cacheLatencyMs,
            queueLag = state.queueLag,
            consumerThroughput = state.consumerThroughput,
            externalDependencyLatencyMs = state.externalDependencyLatencyMs,
            cpuUtilization = state.cpuUtilization,
            memoryUtilization = state.memoryUtilization,
            level = state.level,
            narration = narration,
        )
    }
}

data class ApplyActionRequest(@field:NotNull val actionType: SimulationActionType)

/**
 * ADR-0037 / DRILLS_SIMULATION_VISION.md — the Architecture Canvas can now send
 * the design-time values it collected (e.g. a DB node's pool size) as this
 * session's starting [DesignTraits], instead of always starting from
 * [DesignTraits]' hardcoded defaults. Any field the client omits keeps its
 * Kotlin default, so the canvas only needs to send the handful of fields it
 * actually configured.
 */
/**
 * Phase 3-B (docs/DRILLS_SIMULATION_VISION.md §6) — [targetRps]/[loadDurationSeconds]
 * let the Traffic Lab gate override a real-infra coupon incident's k6 load, in place
 * of the fixed `baseline-rps`/`incident-rps`/`probe-duration-seconds` config values.
 * Ignored for every other domain/mode, same as [traits] already is outside real-infra.
 */
data class StartIncidentRequest(
    val traits: DesignTraits = DesignTraits(),
    val targetRps: Int? = null,
    val loadDurationSeconds: Int? = null,
)

data class TimelineStepResponse(
    val step: Int,
    val actionType: String?,
    val label: String,
    val appliedAt: Instant,
    val systemState: SystemStateResponse,
) {
    companion object {
        fun from(step: TimelineStep) = TimelineStepResponse(
            step = step.step,
            actionType = step.actionType,
            label = step.label,
            appliedAt = step.appliedAt,
            systemState = SystemStateResponse.from(step.systemState),
        )
    }
}

/** PLAN.md Round E4 (ADR-0045) — one sample of `GET /sessions/{id}/simulation/series`. */
data class SeriesPointResponse(
    val t: Instant,
    val state: SystemStateResponse,
    /** HEALTHY / DEGRADED / CRITICAL / RECOVERING / RECOVERED — see [TelemetrySampler.classify]. */
    val status: String,
    /** Accumulated backlog (already reflected in `state.queueLag` for backlog domains). */
    val backlog: Long,
)

data class SimulationSeriesResponse(
    /** RULE_BASED is sampled from the engine; REAL_INFRA is the stored snapshots as steps (ADR-0016). */
    val engineMode: String,
    /** Null until the incident starts — then `points` is empty too. */
    val incidentStartedAt: Instant?,
    /** PLAN.md Round E13 — the learner's "복구 선언"; null until then. */
    val resolvedAt: Instant? = null,
    val points: List<SeriesPointResponse>,
    /** PLAN.md Round E12 (O5) — fired alerts over this window, oldest first. */
    val alerts: List<AlertEvent> = emptyList(),
    /** M3 — the learner's SLO (or the defaults) against the latest point, with error budget. */
    val slo: SloStatus? = null,
) {
    companion object {
        fun from(series: SimulationSeries) = SimulationSeriesResponse(
            engineMode = series.engineMode,
            incidentStartedAt = series.incidentStartedAt,
            resolvedAt = series.resolvedAt,
            points = series.points.map { (point, status) ->
                SeriesPointResponse(point.at, SystemStateResponse.from(point.state), status.name, point.backlog)
            },
            alerts = series.observability?.alerts.orEmpty(),
            slo = series.observability?.slo,
        )
    }
}

/** docs/DRILLS_EXPANSION_PLAN.md M5 — one data-integrity item checked at recovery. */
data class IntegrityCheck(val key: String, val label: String, val ok: Boolean, val detail: String)

/**
 * PLAN.md Round E13 (M5) — mitigation vs recovery. `status`: RECOVERED (symptoms back, backlog
 * drained, integrity fine), PARTIAL (symptoms back but backlog or integrity left), NOT_RECOVERED.
 * Before the declaration it's a preview "as of now" — the checklist the learner looks at.
 */
data class RecoveryReport(
    val started: Boolean,
    val resolved: Boolean,
    val resolvedAt: Instant?,
    /** Incident start → declaration. A new metric next to MTTR, which keeps its definition. */
    val resolvedSeconds: Long?,
    val status: String,
    val healthStatus: String?,
    val symptomsOk: Boolean,
    val backlog: Long,
    val integrity: List<IntegrityCheck>,
) {
    companion object {
        fun notStarted() = RecoveryReport(false, false, null, null, "NOT_STARTED", null, false, 0, emptyList())
    }
}
