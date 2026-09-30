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

/** docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 시나리오 난이도 신호용 집계 한 줄. */
interface ScenarioVersionStats {
    fun getScenarioVersionId(): UUID
    fun getCompletedCount(): Long
    fun getAverageScore(): Double?
}

/** ADR-0042 — 완료 세션 하나의 (사용자, 시나리오 버전, 세션 평균 점수). */
interface CompletedSessionScore {
    fun getUserId(): UUID
    fun getSessionId(): UUID
    fun getScenarioVersionId(): UUID
    fun getAverageScore(): Double?
    fun getCompletedAt(): Instant?
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

    /** PLAN.md step 36 — Game Day's active-session listing: every non-terminal session started by one of these organization members. */
    fun findByUserIdInAndStatusNotIn(userIds: Collection<UUID>, excludedStatuses: Collection<SessionStatus>): List<Session>

    /** PLAN.md step 33 — the org dashboard's per-member roster; one query for every member instead of N. */
    @Query(
        "select s.userId as userId, count(s) as completedCount, max(s.completedAt) as lastCompletedAt " +
            "from Session s where s.userId in :userIds and s.status = com.sysdrill.backend.session.SessionStatus.COMPLETED " +
            "group by s.userId"
    )
    fun completionStatsByUserIds(userIds: Collection<UUID>): List<SessionCompletionStats>

    /** docs/COMMERCIALIZATION.md — admin dashboard's daily activity count. */
    fun countByStatusAndCompletedAtAfter(status: SessionStatus, after: Instant): Long

    /**
     * ADR-0042 — 모든 사용자의 완료 세션을 (세션 평균 점수와 함께) 한 번에.
     *
     * 세션 단위로 묶는 것이 중요하다: DrillScore 는 "도메인별 **최고 세션** 점수"를
     * 쓰므로, (사용자, 버전) 으로 바로 평균내면 같은 시나리오를 두 번 푼 사용자의
     * 두 세션이 뭉개져 최고점을 고를 수 없다.
     */
    @Query(
        "select s.userId as userId, s.id as sessionId, s.scenarioVersionId as scenarioVersionId, " +
            "avg(e.totalScore) as averageScore, max(s.completedAt) as completedAt " +
            "from Session s, com.sysdrill.backend.submission.Submission sub, com.sysdrill.backend.evaluation.Evaluation e " +
            "where sub.sessionId = s.id and e.submissionId = sub.id and e.isActive = true " +
            "and s.status = com.sysdrill.backend.session.SessionStatus.COMPLETED " +
            "group by s.userId, s.id, s.scenarioVersionId"
    )
    fun completedSessionScores(): List<CompletedSessionScore>

    /**
     * docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 시나리오별 "몇 명이 풀었고 평균 몇 점인가".
     *
     * [ScenarioVersionStats.getAverageScore] 는 완료 세션들의 **단계 점수** 평균이다
     * (세션 평균을 다시 평균낸 값이 아니다). 세션마다 단계 수가 같아 실질적 차이는
     * 작고, 목적이 순위가 아니라 난이도 신호이므로 한 번의 집계 쿼리로 끝낸다.
     */
    @Query(
        "select s.scenarioVersionId as scenarioVersionId, " +
            "count(distinct s.id) as completedCount, avg(e.totalScore) as averageScore " +
            "from Session s, com.sysdrill.backend.submission.Submission sub, com.sysdrill.backend.evaluation.Evaluation e " +
            "where sub.sessionId = s.id and e.submissionId = sub.id and e.isActive = true " +
            "and s.status = com.sysdrill.backend.session.SessionStatus.COMPLETED " +
            "and s.scenarioVersionId in :versionIds " +
            "group by s.scenarioVersionId"
    )
    fun statsByScenarioVersionIds(versionIds: Collection<UUID>): List<ScenarioVersionStats>

    /**
     * docs/LEARNING_COMMUNITY_PLAN.md §6.1 (벤치마크) — every finished run of one
     * scenario *version*, across users. Pinned to the version, not the scenario:
     * a new version can change the incident or the rubric, so mixing versions
     * would compare runs that never faced the same problem.
     */
    fun findByScenarioVersionIdAndStatus(scenarioVersionId: UUID, status: SessionStatus): List<Session>

    /** 슬라이스 6 (docs/adr/0041) — 한 시나리오의 모든 버전에서 공개된 풀이, 최신 공개순. */
    fun findByScenarioVersionIdInAndVisibilityOrderBySharedAtDesc(
        scenarioVersionIds: Collection<UUID>,
        visibility: String,
    ): List<Session>

    /** 슬라이스 6 (docs/adr/0041) — 열람 자격 검사. 버전은 가리지 않는다(WriteupService 주석 참고). */
    fun existsByUserIdAndScenarioVersionIdInAndStatus(
        userId: UUID,
        scenarioVersionIds: Collection<UUID>,
        status: SessionStatus,
    ): Boolean
}

interface SessionPhaseRepository : JpaRepository<SessionPhase, UUID> {
    fun findTopBySessionIdOrderByPhaseOrderDesc(sessionId: UUID): SessionPhase?
}
