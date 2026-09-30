package com.sysdrill.backend.community

import com.sysdrill.backend.organization.AssessmentSessions
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStatsService
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ADR-0040 / docs/LEARNING_COMMUNITY_PLAN.md §6.5 — 시나리오별 토론.
 *
 * **스레드는 시나리오가 아니라 버전에 매달린다.** 새 버전은 인시던트나 채점
 * 기준이 달라질 수 있어서, 옛 문답이 새 버전 학습자를 잘못 이끌 수 있다.
 * 대가는 버전이 올라가면 스레드가 새로 시작된다는 것인데, 벤치마크(§6.1)가
 * 같은 이유로 버전을 고정하는 것과 같은 선택이다.
 *
 * **읽기에는 완료 조건을 걸지 않는다.** 걸리는 것은 [ADR-0041] 이 정한 대상,
 * 즉 **인용된 공개 풀이**뿐이다 — 토론 자체까지 완료자에게만 열면 "먼저 질문하고
 * 싶은 사람"이 갈 곳이 없어지고, 그건 이 기능을 만든 이유와 정반대다.
 */
@Service
class DiscussionService(
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val scenarioStatsService: ScenarioStatsService,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val discussionRepository: ScenarioDiscussionRepository,
    private val reportRepository: ScenarioDiscussionReportRepository,
    private val writeupService: WriteupService,
    private val assessmentSessions: AssessmentSessions,
) {

    fun thread(scenarioId: UUID, viewerId: UUID): DiscussionThread {
        val scenario = requireScenario(scenarioId)
        val versionId = currentVersionId(scenarioId)
        val stats = scenarioStatsService.byScenarioId(listOf(scenarioId))[scenarioId]
        val completedByMe = hasCompleted(viewerId, scenarioId)

        val messages = discussionRepository.findByScenarioVersionIdOrderByCreatedAtAsc(versionId)
            // 숨겨진 글은 목록에서 빠진다. 행은 남기되(오판을 되돌릴 수 있게) 읽는
            // 쪽에는 "삭제됨" 같은 흔적도 남기지 않는다 — 흔적이 곧 신고 대상을
            // 지목하는 신호가 되기 때문이다.
            .filter { it.hiddenAt == null }

        return DiscussionThread(
            scenarioId = scenarioId,
            scenarioVersionId = versionId,
            scenarioTitle = titleOf(scenario),
            completedCount = stats?.completedCount ?: 0,
            averageScore = stats?.averageScore,
            completedByMe = completedByMe,
            messages = toResponses(messages, viewerId, completedByMe),
        )
    }

    @Transactional
    fun post(scenarioId: UUID, authorId: UUID, request: PostDiscussionRequest): DiscussionMessage {
        requireScenario(scenarioId)
        val versionId = currentVersionId(scenarioId)

        // 내가 열 수 없는 풀이는 인용할 수도 없다 — 인용을 통해 우회로가 생기면
        // ADR-0041 의 조건이 사실상 무력해진다. detail() 이 404/403 으로 막는다.
        request.quotedSessionId?.let { writeupService.detail(it, authorId) }

        val saved = discussionRepository.save(
            ScenarioDiscussion(
                scenarioVersionId = versionId,
                authorUserId = authorId,
                body = request.body.trim(),
                quotedSessionId = request.quotedSessionId,
            )
        )
        return toResponses(listOf(saved), authorId, hasCompleted(authorId, scenarioId)).single()
    }

    @Transactional
    fun report(discussionId: UUID, reporterId: UUID, request: ReportDiscussionRequest) {
        val discussion = discussionRepository.findById(discussionId)
            .orElseThrow { NotFoundException("Discussion not found: $discussionId") }
        if (discussion.authorUserId == reporterId) {
            throw BadRequestException("자기 글은 신고할 수 없습니다")
        }
        // 이미 신고했으면 조용히 넘어간다 — 재시도와 더블클릭이 오류가 되면 안 된다.
        if (reportRepository.existsByDiscussionIdAndReporterUserId(discussionId, reporterId)) return
        reportRepository.save(
            ScenarioDiscussionReport(
                discussionId = discussionId,
                reporterUserId = reporterId,
                reason = request.reason?.trim()?.ifBlank { null },
            )
        )
    }

    /** PLATFORM_ADMIN 검토 목록. 신고 많은 순. */
    fun reported(): List<ReportedDiscussion> {
        val counts = reportRepository.countsByDiscussion().associate { it.getDiscussionId() to it.getReportCount() }
        if (counts.isEmpty()) return emptyList()
        val discussions = discussionRepository.findAllById(counts.keys)
        val nicknames = nicknamesOf(discussions.map { it.authorUserId })
        return discussions
            .map { discussion ->
                ReportedDiscussion(
                    id = discussion.id!!,
                    scenarioVersionId = discussion.scenarioVersionId,
                    authorNickname = nicknames[discussion.authorUserId] ?: UNKNOWN_AUTHOR,
                    body = discussion.body,
                    reportCount = counts[discussion.id] ?: 0,
                    hidden = discussion.hiddenAt != null,
                    createdAt = discussion.createdAt,
                )
            }
            .sortedWith(compareByDescending<ReportedDiscussion> { it.reportCount }.thenBy { it.createdAt })
    }

    @Transactional
    fun setHidden(discussionId: UUID, adminUserId: UUID, hidden: Boolean): ReportedDiscussion {
        val discussion = discussionRepository.findById(discussionId)
            .orElseThrow { NotFoundException("Discussion not found: $discussionId") }
        discussion.hiddenAt = if (hidden) Instant.now() else null
        discussion.hiddenByUserId = if (hidden) adminUserId else null
        discussionRepository.save(discussion)
        return ReportedDiscussion(
            id = discussion.id!!,
            scenarioVersionId = discussion.scenarioVersionId,
            authorNickname = nicknamesOf(listOf(discussion.authorUserId))[discussion.authorUserId] ?: UNKNOWN_AUTHOR,
            body = discussion.body,
            reportCount = reportRepository.countByDiscussionId(discussionId),
            hidden = discussion.hiddenAt != null,
            createdAt = discussion.createdAt,
        )
    }

    private fun toResponses(
        messages: List<ScenarioDiscussion>,
        viewerId: UUID,
        completedByMe: Boolean,
    ): List<DiscussionMessage> {
        if (messages.isEmpty()) return emptyList()
        val nicknames = nicknamesOf(messages.map { it.authorUserId })
        val myReports = reportRepository
            .findByReporterUserIdAndDiscussionIdIn(viewerId, messages.mapNotNull { it.id })
            .map { it.discussionId }
            .toSet()
        val quoted = quotedWriteups(messages, completedByMe)

        return messages.map { message ->
            DiscussionMessage(
                id = message.id!!,
                authorUserId = message.authorUserId,
                authorNickname = nicknames[message.authorUserId] ?: UNKNOWN_AUTHOR,
                body = message.body,
                createdAt = message.createdAt,
                mine = message.authorUserId == viewerId,
                quoted = message.quotedSessionId?.let { quoted[it] },
                reportedByMe = message.id in myReports,
            )
        }
    }

    /**
     * 인용된 풀이의 작성자·점수는 ADR-0041 을 통과한 viewer 에게만 채운다.
     * 통과하지 못하면 `locked` 만 내려가고, 화면은 "이 시나리오를 완료하면 열립니다"를 띄운다.
     */
    private fun quotedWriteups(
        messages: List<ScenarioDiscussion>,
        completedByMe: Boolean,
    ): Map<UUID, QuotedWriteup> {
        val ids = messages.mapNotNull { it.quotedSessionId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        if (!completedByMe) {
            return ids.associateWith { QuotedWriteup(it, locked = true, authorNickname = null) }
        }
        // ADR-0043 — an assessment session never shows up as a quotable writeup, even if it was public before.
        val assessmentIds = assessmentSessions.among(ids)
        val sessions = sessionRepository.findAllById(ids).filter { it.visibility == "PUBLIC" && it.id !in assessmentIds }
        val nicknames = nicknamesOf(sessions.filterNot { it.sharedAnonymously }.map { it.userId })
        return sessions.mapNotNull { session ->
            val id = session.id ?: return@mapNotNull null
            id to QuotedWriteup(
                sessionId = id,
                locked = false,
                authorNickname = if (session.sharedAnonymously) null else nicknames[session.userId],
            )
        }.toMap()
    }

    private fun hasCompleted(userId: UUID, scenarioId: UUID): Boolean {
        val versionIds = scenarioVersionRepository.findByScenarioIdIn(listOf(scenarioId)).mapNotNull { it.id }
        return versionIds.isNotEmpty() &&
            sessionRepository.existsByUserIdAndScenarioVersionIdInAndStatus(userId, versionIds, SessionStatus.COMPLETED)
    }

    private fun requireScenario(scenarioId: UUID): Scenario = scenarioRepository.findById(scenarioId)
        .orElseThrow { NotFoundException("Scenario not found: $scenarioId") }

    /**
     * 글을 쓰는 곳과 읽는 곳이 같은 버전이어야 하므로, 세션이 쓰는 것과 같은
     * "가장 최신 PUBLISHED 버전"을 쓴다.
     */
    private fun currentVersionId(scenarioId: UUID): UUID =
        scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId, "PUBLISHED")?.id
            ?: throw NotFoundException("No published version for scenario $scenarioId")

    private fun titleOf(scenario: Scenario): String =
        contentItemRepository.findById(scenario.contentId).map { it.title }.orElse(null) ?: scenario.domain

    private fun nicknamesOf(userIds: Collection<UUID>): Map<UUID, String> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return userRepository.findAllById(ids).mapNotNull { user -> user.id?.let { it to user.nickname } }.toMap()
    }

    companion object {
        private const val UNKNOWN_AUTHOR = "알 수 없음"
    }
}
