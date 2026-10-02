package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.auth.PlatformAccessGuard
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/COMMUNITY_EXPANSION_PLAN.md C10 (PLAN.md Round E22) — anchored reviews on a public writeup. */
@RestController
class WriteupCommentController(private val service: WriteupCommentService) {

    @GetMapping("/writeups/{sessionId}/comments")
    fun list(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): WriteupCommentsResponse = service.list(sessionId, userId)

    @PostMapping("/writeups/{sessionId}/comments")
    fun post(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: PostWriteupCommentRequest,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<WriteupCommentsResponse> = ResponseEntity.status(HttpStatus.CREATED).body(service.post(sessionId, userId, request))

    @PostMapping("/writeup-comments/{commentId}/reports")
    fun report(
        @PathVariable commentId: UUID,
        @Valid @RequestBody request: ReportDiscussionRequest,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<Void> {
        service.report(commentId, userId, request.reason)
        return ResponseEntity.noContent().build()
    }
}

/** Moderation — the discussion review shape (reports → PLATFORM_ADMIN → hide). */
@RestController
@RequestMapping("/admin/writeup-comments")
class AdminWriteupCommentController(
    private val service: WriteupCommentService,
    private val accessGuard: PlatformAccessGuard,
) {
    @GetMapping("/reported")
    fun reported(@AuthenticatedUserId userId: UUID): List<ReportedWriteupComment> {
        accessGuard.requirePlatformAdmin(userId)
        return service.reported()
    }

    @PutMapping("/{commentId}/hidden")
    fun setHidden(
        @PathVariable commentId: UUID,
        @RequestBody request: HideDiscussionRequest,
        @AuthenticatedUserId userId: UUID,
    ): ReportedWriteupComment {
        accessGuard.requirePlatformAdmin(userId)
        return service.setHidden(commentId, userId, request.hidden)
    }
}
