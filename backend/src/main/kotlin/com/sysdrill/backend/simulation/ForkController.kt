package com.sysdrill.backend.simulation

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
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
    private val sessionAccessGuard: SessionAccessGuard,
) {

    @PostMapping("/sessions/{sessionId}/forks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID, @RequestBody request: CreateForkRequest): ForkResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
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
