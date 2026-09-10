package com.sysdrill.backend.postmortem

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions/{sessionId}/postmortem")
class PostmortemController(
    private val postmortemService: PostmortemService,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    @GetMapping
    fun get(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): PostmortemResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return postmortemService.get(sessionId)
    }

    @PutMapping
    fun save(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: SavePostmortemRequest,
    ): PostmortemResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return postmortemService.save(sessionId, request)
    }
}

/**
 * Phase 3-C — separate flat controller (not a method on [PostmortemController]) because that controller's
 * class-level `@RequestMapping("/sessions/{sessionId}/postmortem")` would prefix this onto a `{sessionId}`
 * path it doesn't take; matches `identity/SkillProfileController`'s flat, token-derived-identity style.
 */
@RestController
class PostmortemSummaryController(private val postmortemService: PostmortemService) {

    @GetMapping("/postmortem-summary")
    fun getSummary(@AuthenticatedUserId userId: UUID): PostmortemSummaryResponse = postmortemService.getSummary(userId)
}
