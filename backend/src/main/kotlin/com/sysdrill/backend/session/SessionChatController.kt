package com.sysdrill.backend.session

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.identity.UserRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PostChatMessageRequest(@field:NotBlank val body: String)

data class ChatMessageResponse(
    val id: UUID,
    val authorUserId: UUID,
    val authorNickname: String,
    val body: String,
    val createdAt: Instant?,
)

/** PLAN.md step 36 — Game Day's spectate-and-chat channel; open to the session's owner and any spectator (SessionAccessGuard.requireOwnerOrSpectator), never to anyone else. */
@RestController
@RequestMapping("/sessions/{sessionId}/chat")
class SessionChatController(
    private val sessionAccessGuard: SessionAccessGuard,
    private val chatMessageRepository: SessionChatMessageRepository,
    private val userRepository: UserRepository,
) {

    @PostMapping
    fun post(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: PostChatMessageRequest,
    ): ResponseEntity<ChatMessageResponse> {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        val saved = chatMessageRepository.save(
            SessionChatMessage(sessionId = sessionId, authorUserId = userId, body = request.body)
        )
        val nickname = userRepository.findById(userId).orElseThrow().nickname
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved, nickname))
    }

    @GetMapping
    fun list(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): List<ChatMessageResponse> {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        val messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val nicknamesByUserId = userRepository.findAllById(messages.map { it.authorUserId }.distinct())
            .associate { it.id to it.nickname }
        return messages.map { toResponse(it, nicknamesByUserId[it.authorUserId] ?: "알 수 없음") }
    }

    private fun toResponse(message: SessionChatMessage, authorNickname: String) = ChatMessageResponse(
        id = message.id!!,
        authorUserId = message.authorUserId,
        authorNickname = authorNickname,
        body = message.body,
        createdAt = message.createdAt,
    )
}
