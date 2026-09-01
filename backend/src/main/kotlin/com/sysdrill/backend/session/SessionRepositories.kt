package com.sysdrill.backend.session

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** PLAN.md step 33 — one row per user in [SessionRepository.completionStatsByUserIds], never per-session. */
interface SessionCompletionStats {
    fun getUserId(): UUID
    fun getCompletedCount(): Long
    fun getLastCompletedAt(): Instant?
}

interface SessionRepository : JpaRepository<Session, UUID> {

    /**
     * Guarded state transition per docs/ARCHITECTURE.md §5: only applies when the
     * row is still in [from]. Returns the number of rows updated (0 = a concurrent
     * transition already moved the session elsewhere).
     *
     * Callers must provide the transaction (SessionService methods are already
     * @Transactional; EvaluationWorker/EvaluationRequestPublisher wrap calls in
     * a TransactionTemplate) — see TransactionSupportConfig for why.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "update Session s set s.status = :to, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.id = :id and s.status = :from"
    )
    fun compareAndSetStatus(id: UUID, from: SessionStatus, to: SessionStatus): Int

    fun findByUserIdOrderByStartedAtDesc(userId: UUID): List<Session>

    /** PLAN.md step 33 — the org dashboard's per-member roster; one query for every member instead of N. */
    @Query(
        "select s.userId as userId, count(s) as completedCount, max(s.completedAt) as lastCompletedAt " +
            "from Session s where s.userId in :userIds and s.status = com.sysdrill.backend.session.SessionStatus.COMPLETED " +
            "group by s.userId"
    )
    fun completionStatsByUserIds(userIds: Collection<UUID>): List<SessionCompletionStats>
}

interface SessionPhaseRepository : JpaRepository<SessionPhase, UUID> {
    fun findTopBySessionIdOrderByPhaseOrderDesc(sessionId: UUID): SessionPhase?
}
