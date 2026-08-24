package com.sysdrill.backend.session

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SessionRepository : JpaRepository<Session, UUID> {

    /**
     * Guarded state transition per docs/ARCHITECTURE.md §5: only applies when the
     * row is still in [from]. Returns the number of rows updated (0 = a concurrent
     * transition already moved the session elsewhere).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "update Session s set s.status = :to, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.id = :id and s.status = :from"
    )
    fun compareAndSetStatus(id: UUID, from: SessionStatus, to: SessionStatus): Int
}

interface SessionPhaseRepository : JpaRepository<SessionPhase, UUID> {
    fun findTopBySessionIdOrderByPhaseOrderDesc(sessionId: UUID): SessionPhase?
}
