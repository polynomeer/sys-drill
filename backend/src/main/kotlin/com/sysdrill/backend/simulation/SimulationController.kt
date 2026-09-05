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
) {

    @PostMapping("/incident")
    fun startIncident(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @RequestParam(defaultValue = "false") realInfra: Boolean,
    ): SystemStateResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return SystemStateResponse.from(simulationService.startIncident(sessionId, realInfra))
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

    /** PLAN.md step 36 — a Game Day spectator may also view the timeline. */
    @GetMapping("/timeline")
    fun getTimeline(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): List<TimelineStepResponse> {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return simulationService.getTimeline(sessionId).map(TimelineStepResponse::from)
    }
}
