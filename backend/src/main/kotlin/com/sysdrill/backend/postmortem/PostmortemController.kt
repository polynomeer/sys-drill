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
