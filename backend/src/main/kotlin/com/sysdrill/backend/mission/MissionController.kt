package com.sysdrill.backend.mission

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/DRILLS_EXPANSION_PLAN.md M1 (PLAN.md Round E8) — "질문하기": the deliberately incomplete brief's missing pieces. */
@RestController
@RequestMapping("/sessions/{sessionId}/clarifications")
class MissionController(
    private val missionService: MissionService,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    /** Questions without answers until asked. A Game Day spectator sees what the owner has asked so far. */
    @GetMapping
    fun list(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): ClarificationsResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return missionService.clarifications(sessionId)
    }

    @PostMapping("/{questionId}")
    fun ask(
        @PathVariable sessionId: UUID,
        @PathVariable questionId: String,
        @AuthenticatedUserId userId: UUID,
    ): ClarificationsResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return missionService.ask(sessionId, questionId)
    }
}
