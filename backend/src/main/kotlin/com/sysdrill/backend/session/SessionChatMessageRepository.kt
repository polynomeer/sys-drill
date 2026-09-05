package com.sysdrill.backend.session

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SessionChatMessageRepository : JpaRepository<SessionChatMessage, UUID> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<SessionChatMessage>
}
