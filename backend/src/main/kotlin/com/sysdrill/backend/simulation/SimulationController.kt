package com.sysdrill.backend.simulation

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
class SimulationController(private val simulationService: SimulationService) {

    @PostMapping("/incident")
    fun startIncident(
        @PathVariable sessionId: UUID,
        @RequestParam(defaultValue = "false") realInfra: Boolean,
    ): SystemStateResponse =
        SystemStateResponse.from(simulationService.startIncident(sessionId, realInfra))

    @GetMapping("/state")
    fun getState(@PathVariable sessionId: UUID): SystemStateResponse =
        SystemStateResponse.from(simulationService.getState(sessionId))

    @PostMapping("/actions")
    fun applyAction(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: ApplyActionRequest,
    ): SystemStateResponse =
        SystemStateResponse.from(simulationService.applyAction(sessionId, request.actionType))

    @GetMapping("/timeline")
    fun getTimeline(@PathVariable sessionId: UUID): List<TimelineStepResponse> =
        simulationService.getTimeline(sessionId).map(TimelineStepResponse::from)
}
