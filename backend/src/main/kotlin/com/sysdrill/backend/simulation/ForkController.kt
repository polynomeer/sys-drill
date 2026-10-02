package com.sysdrill.backend.simulation

import com.sysdrill.backend.auth.AuthenticatedUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateForkRequest(val atStep: Int)

/** docs/DRILLS_EXPANSION_PLAN.md M6 (PLAN.md Round E14, ADR-0046) — Counterfactual Replay. */
@RestController
class ForkController(
    private val forkService: ForkService,
    private val sessionRepository: com.sysdrill.backend.session.SessionRepository,
    private val writeupService: com.sysdrill.backend.community.WriteupService,
) {

    /**
     * Your own session (M6), or — docs/COMMUNITY_EXPANSION_PLAN.md C9 "Fork My Run" — someone
     * else's **public writeup** you're allowed to read: exactly the ADR-0041 gate (you completed
     * that scenario), checked by asking for the writeup itself. A fork exposes nothing the
     * writeup's replay doesn't already show, and it's yours alone (ADR-0046).
     */
    @PostMapping("/sessions/{sessionId}/forks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID, @RequestBody request: CreateForkRequest): ForkResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow { com.sysdrill.backend.common.web.NotFoundException("Session not found: $sessionId") }
        if (session.userId != userId) writeupService.detail(sessionId, userId) // 404 if private, 403 if not completed
        return forkService.create(sessionId, userId, request.atStep)
    }

    @GetMapping("/forks/{forkId}")
    fun get(@PathVariable forkId: String, @AuthenticatedUserId userId: UUID): ForkResponse = forkService.get(forkId, userId)

    @GetMapping("/forks/{forkId}/series")
    fun series(@PathVariable forkId: String, @AuthenticatedUserId userId: UUID): SimulationSeriesResponse =
        SimulationSeriesResponse.from(forkService.series(forkId, userId))

    @PostMapping("/forks/{forkId}/actions")
    fun apply(@PathVariable forkId: String, @AuthenticatedUserId userId: UUID, @Valid @RequestBody request: ApplyActionRequest): ForkResponse =
        forkService.apply(forkId, userId, request.actionType)

    @GetMapping("/forks/{forkId}/comparison")
    fun comparison(@PathVariable forkId: String, @AuthenticatedUserId userId: UUID): ForkComparison = forkService.compare(forkId, userId)
}
