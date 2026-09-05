package com.sysdrill.backend.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/** PLAN.md step 36 — a Game Day session's spectate-and-chat channel, open to the owner and any spectator (see SessionAccessGuard.requireOwnerOrSpectator). */
@Entity
@Table(name = "session_chat_messages")
class SessionChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "author_user_id", nullable = false)
    var authorUserId: UUID,

    @Column(nullable = false)
    var body: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
