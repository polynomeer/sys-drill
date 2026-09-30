package com.sysdrill.backend.community

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ADR-0040 — 시나리오 **버전** 단위 토론 스레드의 메시지 한 개.
 * [com.sysdrill.backend.session.SessionChatMessage] 를 참조 구현으로 삼았다.
 */
@Entity
@Table(name = "scenario_discussions")
class ScenarioDiscussion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "scenario_version_id", nullable = false)
    var scenarioVersionId: UUID,

    @Column(name = "author_user_id", nullable = false)
    var authorUserId: UUID,

    @Column(nullable = false)
    var body: String,

    /** 공개 풀이를 인용한 경우 그 세션. 인용은 ADR-0041 의 열람 조건을 따른다. */
    @Column(name = "quoted_session_id")
    var quotedSessionId: UUID? = null,

    /** 모더레이션으로 숨겨진 시각. 지우지 않고 숨긴다 — 오판을 되돌릴 수 있어야 한다. */
    @Column(name = "hidden_at")
    var hiddenAt: Instant? = null,

    @Column(name = "hidden_by_user_id")
    var hiddenByUserId: UUID? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "scenario_discussion_reports")
class ScenarioDiscussionReport(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "discussion_id", nullable = false)
    var discussionId: UUID,

    @Column(name = "reporter_user_id", nullable = false)
    var reporterUserId: UUID,

    var reason: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

/** 신고 건수 집계 한 줄 — 관리자 검토 목록이 N+1 없이 정렬할 수 있게. */
interface DiscussionReportCount {
    fun getDiscussionId(): UUID
    fun getReportCount(): Long
}

interface ScenarioDiscussionRepository : JpaRepository<ScenarioDiscussion, UUID> {
    fun findByScenarioVersionIdOrderByCreatedAtAsc(scenarioVersionId: UUID): List<ScenarioDiscussion>
}

interface ScenarioDiscussionReportRepository : JpaRepository<ScenarioDiscussionReport, UUID> {
    fun existsByDiscussionIdAndReporterUserId(discussionId: UUID, reporterUserId: UUID): Boolean
    fun countByDiscussionId(discussionId: UUID): Long

    /** 스레드 한 번 조회에 신고 여부 쿼리도 한 번 — 메시지 수만큼 도는 대신. */
    fun findByReporterUserIdAndDiscussionIdIn(
        reporterUserId: UUID,
        discussionIds: Collection<UUID>,
    ): List<ScenarioDiscussionReport>

    @org.springframework.data.jpa.repository.Query(
        "select r.discussionId as discussionId, count(r) as reportCount " +
            "from ScenarioDiscussionReport r group by r.discussionId"
    )
    fun countsByDiscussion(): List<DiscussionReportCount>
}
