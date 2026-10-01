package com.sysdrill.backend.mentor

import com.sysdrill.backend.auth.ActionRateLimiter
import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.TooManyRequestsException
import com.sysdrill.backend.session.SessionAccessGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions/{sessionId}/mentor-hint")
class MentorController(
    private val mentorService: MentorService,
    private val sessionAccessGuard: SessionAccessGuard,
    private val actionRateLimiter: ActionRateLimiter,
    @Value("\${sysdrill.mentor.rate-limit-per-minute}") private val rateLimitPerMinute: Long,
) {

    @PostMapping
    fun getHint(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody(required = false) request: MentorHintRequest?,
    ): MentorHintResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        // docs/COMMERCIALIZATION.md — unlike a real submission, a hint request
        // has no daily cap at all; this is the only guard on this LLM call.
        if (!actionRateLimiter.tryAcquire("mentor-hint", userId, rateLimitPerMinute)) {
            throw TooManyRequestsException("Too many hint requests -- please slow down")
        }
        return mentorService.getHint(sessionId, request?.rawText)
    }
}
