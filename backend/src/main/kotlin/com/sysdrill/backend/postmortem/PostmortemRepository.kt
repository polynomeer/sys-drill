package com.sysdrill.backend.postmortem

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PostmortemRepository : JpaRepository<Postmortem, UUID> {
    fun findBySessionId(sessionId: UUID): Postmortem?
}
