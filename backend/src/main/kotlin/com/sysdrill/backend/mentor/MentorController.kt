package com.sysdrill.backend.mentor

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import java.util.UUID
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
) {

    @PostMapping
    fun getHint(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @RequestBody(required = false) request: MentorHintRequest?,
    ): MentorHintResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return mentorService.getHint(sessionId, request?.rawText)
    }
}
