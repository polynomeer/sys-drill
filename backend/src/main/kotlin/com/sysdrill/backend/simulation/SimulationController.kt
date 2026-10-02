package com.sysdrill.backend.simulation

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions/{sessionId}/simulation")
class SimulationController(
    private val simulationService: SimulationService,
    private val sessionAccessGuard: SessionAccessGuard,
    private val investigationService: InvestigationService,
    private val traceService: TraceService,
) {

    @PostMapping("/incident")
    fun startIncident(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @RequestParam(defaultValue = "false") realInfra: Boolean,
        @RequestBody(required = false) request: StartIncidentRequest?,
    ): SystemStateResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        val traits = request?.traits ?: DesignTraits()
        val result = simulationService.startIncident(sessionId, realInfra, traits, request?.targetRps, request?.loadDurationSeconds)
        return SystemStateResponse.from(result.state, narration = result.narration)
    }

    /** PLAN.md step 36 — a Game Day spectator may also view live state. */
    @GetMapping("/state")
    fun getState(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): SystemStateResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return SystemStateResponse.from(simulationService.getState(sessionId))
    }

    @PostMapping("/actions")
    fun applyAction(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: ApplyActionRequest,
    ): SystemStateResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return SystemStateResponse.from(simulationService.applyAction(sessionId, request.actionType))
    }

    /** PLAN.md Round E4 (ADR-0045) — the incident as a time series; spectators too, like /state and /timeline. */
    @GetMapping("/series")
    fun getSeries(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): SimulationSeriesResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return SimulationSeriesResponse.from(simulationService.getSeries(sessionId))
    }

    /** PLAN.md Round E13 (M5) — declare recovery (once). */
    @PostMapping("/resolve")
    fun resolve(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): RecoveryReport {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return simulationService.resolve(sessionId)
    }

    /** M5 — the recovery report, or the "if declared now" checklist before the declaration. */
    @GetMapping("/recovery")
    fun recovery(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): RecoveryReport {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return simulationService.recovery(sessionId)
    }

    /** PLAN.md step 36 — a Game Day spectator may also view the timeline. */
    @GetMapping("/timeline")
    fun getTimeline(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): List<TimelineStepResponse> {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return simulationService.getTimeline(sessionId).map(TimelineStepResponse::from)
    }

    /** PLAN.md Round E17 (O4) — server logs generated from the series; spectators too. */
    @GetMapping("/logs")
    fun getLogs(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): List<LogLine> {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return simulationService.getLogs(sessionId)
    }

    /** PLAN.md Round E17 (O0-b) — "what did I look at"; owner only, fire-and-forget. */
    @PostMapping("/investigations")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    fun recordInvestigation(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: RecordInvestigationRequest,
    ) {
        sessionAccessGuard.requireOwner(sessionId, userId)
        investigationService.record(sessionId, request.kind, request.target)
    }

    /** PLAN.md Round E25 (O6) — recent traces: real Jaeger spans for real-infra coupon, synthetic otherwise. */
    @GetMapping("/traces")
    fun traces(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): TraceList {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return traceService.list(sessionId)
    }

    @GetMapping("/traces/{traceId}")
    fun trace(@PathVariable sessionId: UUID, @PathVariable traceId: String, @AuthenticatedUserId userId: UUID): TraceView {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return traceService.get(sessionId, traceId)
    }
}
