package com.sysdrill.backend.simulation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppliedActionRepository : JpaRepository<AppliedAction, UUID> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<AppliedAction>
}
