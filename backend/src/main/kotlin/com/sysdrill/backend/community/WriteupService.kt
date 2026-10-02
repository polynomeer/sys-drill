package com.sysdrill.backend.community

import com.sysdrill.backend.organization.AssessmentSessions
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.ForbiddenException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.postmortem.PostmortemService
import com.sysdrill.backend.reporting.ReportRepository
import com.sysdrill.backend.reporting.ReportResponses
import com.sysdrill.backend.reporting.TimelineEntry
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionAccessGuard
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 / [ADR-0041] — 풀이 공유.
 *
 * 두 가지 규칙이 이 서비스의 전부다.
 *
 * 하나, **기본은 비공개이고 공개는 본인의 명시적 선택**이다(ARCHITECTURE §13).
 * 그래서 공개 상태는 세션에 붙은 컬럼일 뿐 별도 엔티티가 아니고, 공개할 수 있는
 * 것은 끝까지 마친 세션뿐이다 — 중간에 멈춘 답안은 읽는 쪽에도 쓸모가 없다.
 *
 * 둘, **그 시나리오를 완료한 사람만 본문을 본다**(ADR-0041). 이 검사는 목록과
 * 상세 양쪽에 있어야 한다: 목록만 막으면 세션 id 를 아는 사람이 상세로 바로
 * 들어올 수 있고, 상세만 막으면 목록의 점수 분포가 이미 답을 흘린다.
 */
@Service
class WriteupService(
    private val sessionRepository: SessionRepository,
    private val sessionAccessGuard: SessionAccessGuard,
    private val assessmentSessions: AssessmentSessions,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val reportRepository: ReportRepository,
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
    private val postmortemService: PostmortemService,
    private val objectMapper: ObjectMapper,
    private val systemTopologyService: com.sysdrill.backend.simulation.SystemTopologyService,
    private val simulationService: com.sysdrill.backend.simulation.SimulationService,
) {

    @Transactional
    fun setVisibility(sessionId: UUID, userId: UUID, request: SessionVisibilityRequest): SessionVisibilityResponse {
        val visibility = request.visibility.uppercase()
        if (visibility !in ALLOWED_VISIBILITIES) {
            throw BadRequestException("visibility must be one of $ALLOWED_VISIBILITIES")
        }
        val session = sessionAccessGuard.requireOwner(sessionId, userId)
        if (visibility == PUBLIC && session.status != SessionStatus.COMPLETED) {
            throw ConflictException("끝까지 마친 세션만 공개할 수 있습니다 (현재 ${session.status})")
        }
        if (visibility == PUBLIC && !isShareable(session)) {
            throw ConflictException("조직 시나리오 세션과 채용 평가 세션은 공개할 수 없습니다")
        }

        session.visibility = visibility
        session.sharedAnonymously = if (visibility == PUBLIC) request.anonymous else false
        // 공개를 철회하면 sharedAt 도 비운다 — "언제 공개했나"는 공개 중에만 의미가 있고,
        // 다시 공개하면 그때가 새 공개 시점이다(목록은 최신순).
        session.sharedAt = if (visibility == PUBLIC) Instant.now() else null
        sessionRepository.save(session)
        return session.toVisibilityResponse()
    }

    fun visibility(sessionId: UUID, userId: UUID): SessionVisibilityResponse =
        sessionAccessGuard.requireOwner(sessionId, userId).toVisibilityResponse()

    @Transactional
    fun setNote(sessionId: UUID, userId: UUID, note: String?): WriteupDetail {
        val session = sessionAccessGuard.requireOwner(sessionId, userId)
        session.writeupNote = note?.trim()?.ifBlank { null }
        sessionRepository.save(session)
        return detail(sessionId, userId)
    }

    fun listForScenario(scenarioId: UUID, viewerId: UUID, sort: String? = null): WriteupListResponse {
        val scenario = scenarioRepository.findById(scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: $scenarioId") }
        val versionIds = versionIdsOf(scenarioId)
        // Filtered again on read so rows made public before ADR-0043 can't surface.
        val published = sessionRepository
            .findByScenarioVersionIdInAndVisibilityOrderBySharedAtDesc(versionIds, PUBLIC)
        val assessmentIds = assessmentSessions.among(published.mapNotNull { it.id })
        val shared = if (isPublicScenario(scenario)) published.filterNot { it.id in assessmentIds } else emptyList()

        // 잠겨 있으면 편 수만 알려주고 목록은 비운다 — 닉네임과 점수도 본문의 일부다.
        if (!hasCompleted(viewerId, versionIds)) {
            return WriteupListResponse(
                scenarioId = scenarioId,
                scenarioTitle = titleOf(scenario),
                locked = true,
                count = shared.size,
                writeups = emptyList(),
            )
        }

        val scores = averageScores(shared.mapNotNull { it.id })
        val nicknames = nicknamesOf(shared)
        // docs/COMMUNITY_EXPANSION_PLAN.md C8 — "나와 다른 순" is the default when the viewer has a canvas
        // to compare against; popularity is never the default (the plan's guard against a popularity vote).
        val myProfile = bestOwnSession(viewerId, versionIds, scenario.domain)?.let { systemTopologyService.designProfile(it.id!!, scenario.domain) }
        val distances = if (myProfile == null) emptyMap() else shared.mapNotNull { session ->
            systemTopologyService.designProfile(session.id!!, scenario.domain)?.let { session.id!! to myProfile.distanceTo(it) }
        }.toMap()
        val ordered = when (sort ?: if (myProfile != null) "different" else "recent") {
            "different" -> shared.sortedWith(compareByDescending<Session> { distances[it.id] ?: -1.0 }.thenByDescending { it.sharedAt })
            "score" -> shared.sortedByDescending { scores[it.id] ?: -1 }
            else -> shared
        }
        return WriteupListResponse(
            scenarioId = scenarioId,
            scenarioTitle = titleOf(scenario),
            locked = false,
            count = shared.size,
            writeups = ordered.map { session ->
                WriteupSummary(
                    sessionId = session.id!!,
                    authorNickname = if (session.sharedAnonymously) null else nicknames[session.userId],
                    anonymous = session.sharedAnonymously,
                    averageScore = scores[session.id],
                    completedAt = session.completedAt,
                    sharedAt = session.sharedAt,
                    mine = session.userId == viewerId,
                    distance = distances[session.id],
                )
            },
        )
    }

    fun detail(sessionId: UUID, viewerId: UUID): WriteupDetail {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Writeup not found: $sessionId") }
        // 비공개 세션은 "없는 것"으로 답한다 — 소유자 검사와 같은 이유로, 존재
        // 여부 자체가 새면 안 된다([SessionAccessGuard]).
        if (session.visibility != PUBLIC && session.userId != viewerId) {
            throw NotFoundException("Writeup not found: $sessionId")
        }
        if (session.userId != viewerId && !isShareable(session)) {
            throw NotFoundException("Writeup not found: $sessionId")
        }

        val scenario = scenarioOf(session)
            ?: throw NotFoundException("Scenario not found for session $sessionId")
        val versionIds = versionIdsOf(scenario.id!!)
        if (session.userId != viewerId && !hasCompleted(viewerId, versionIds)) {
            throw ForbiddenException("이 시나리오를 먼저 완료해야 다른 사람의 풀이를 볼 수 있습니다")
        }

        val timeline = timelineOf(sessionId)
        val answers = submissionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
            .associate { it.id to it.rawText }
        val postmortem = runCatching { postmortemService.get(sessionId) }.getOrNull()

        return WriteupDetail(
            sessionId = sessionId,
            scenarioId = scenario.id!!,
            scenarioTitle = titleOf(scenario),
            domain = scenario.domain,
            authorNickname = if (session.sharedAnonymously) null else nicknamesOf(listOf(session))[session.userId],
            anonymous = session.sharedAnonymously,
            averageScore = com.sysdrill.backend.reporting.averageScore(timeline),
            completedAt = session.completedAt,
            mine = session.userId == viewerId,
            phases = timeline.map { entry ->
                WriteupPhase(
                    phase = entry.phase,
                    answer = answers[entry.submissionId],
                    score = entry.totalScore,
                    topRisks = entry.topRisks,
                )
            },
            rootCause = postmortem?.rootCause,
            preventionItems = postmortem?.preventionItems ?: emptyList(),
            mttdSeconds = postmortem?.mttdSeconds,
            mttrSeconds = postmortem?.mttrSeconds,
            note = session.writeupNote,
            summary = systemTopologyService.designProfile(sessionId, scenario.domain).let { profile ->
                WriteupDesignSummary(
                    nodeKinds = profile?.nodeKinds.orEmpty(),
                    changedTraits = profile?.traits.orEmpty()
                        .filter { (k, v) -> profile!!.defaults[k] != v }
                        .map { (k, v) -> TraitValue(k, v, profile!!.defaults.getValue(k)) },
                    actions = postmortem?.actionsTimeline?.map { it.actionType }.orEmpty(),
                    actionSeconds = postmortem?.actionsTimeline?.map { it.elapsedSeconds }.orEmpty(),
                    forkable = runCatching { simulationService.forkSource(sessionId).engineMode == "RULE_BASED" }.getOrDefault(false),
                )
            },
        )
    }

    /**
     * docs/COMMUNITY_EXPANSION_PLAN.md C8 (PLAN.md Round E15) — the viewer's own best session vs
     * this writeup, as structural differences between the two canvases. Same gate as [detail]
     * (ADR-0041), which also guarantees the viewer has a session of their own to compare.
     */
    fun compare(sessionId: UUID, viewerId: UUID): WriteupComparison {
        val theirsDetail = detail(sessionId, viewerId)
        val domain = theirsDetail.domain
        val versionIds = versionIdsOf(theirsDetail.scenarioId)
        val theirs = sideOf(sessionId, domain, theirsDetail.averageScore, theirsDetail.mttrSeconds)
        val mine = bestOwnSession(viewerId, versionIds, domain)?.takeIf { it.id != sessionId }?.let { own ->
            val pm = runCatching { postmortemService.get(own.id!!) }.getOrNull()
            sideOf(own.id!!, domain, averageScores(listOf(own.id!!))[own.id], pm?.mttrSeconds)
        }
        val mineKinds = mine?.nodeKinds?.keys.orEmpty()
        val theirKinds = theirs.nodeKinds.keys
        val diffs = (mine?.traits?.keys.orEmpty() intersect theirs.traits.keys).map { k ->
            TraitDiff(k, mine!!.traits.getValue(k), theirs.traits.getValue(k))
        }.filter { it.mine != it.theirs }
        val largest = diffs.maxByOrNull { kotlin.math.abs(it.mine - it.theirs).toDouble() / maxOf(it.mine, it.theirs, 1) }?.key
        val myProfile = mine?.let { systemTopologyService.designProfile(it.sessionId, domain) }
        val theirProfile = systemTopologyService.designProfile(sessionId, domain)
        return WriteupComparison(
            mine = mine,
            theirs = theirs,
            onlyMine = (mineKinds - theirKinds).sorted(),
            onlyTheirs = (theirKinds - mineKinds).sorted(),
            shared = (mineKinds intersect theirKinds).sorted(),
            traitDiffs = diffs,
            largestDifference = largest,
            distance = if (myProfile != null && theirProfile != null) myProfile.distanceTo(theirProfile) else null,
        )
    }

    private fun sideOf(sessionId: UUID, domain: String, score: Int?, mttr: Long?): CompareSide {
        val profile = systemTopologyService.designProfile(sessionId, domain)
        val actions = runCatching { postmortemService.get(sessionId).actionsTimeline.map { it.actionType } }.getOrDefault(emptyList())
        return CompareSide(sessionId, score, mttr, profile?.nodeKinds.orEmpty(), profile?.traits.orEmpty(), actions)
    }

    /**
     * The viewer's completed session to compare against: one with a canvas if any (a design
     * without one has nothing structural to compare), then the highest score, then the latest.
     * Assessment sessions count — ADR-0043's exception for "did you do it yourself".
     */
    private fun bestOwnSession(userId: UUID, versionIds: Collection<UUID>, domain: String? = null): Session? {
        val own = sessionRepository.findByUserIdAndScenarioVersionIdInAndStatus(userId, versionIds, SessionStatus.COMPLETED)
        if (own.isEmpty()) return null
        val scores = averageScores(own.mapNotNull { it.id })
        val hasCanvas = if (domain == null) emptySet() else own.filter { systemTopologyService.designProfile(it.id!!, domain) != null }.mapNotNull { it.id }.toSet()
        return own.maxWithOrNull(
            compareBy<Session> { it.id in hasCanvas }.thenBy { scores[it.id] ?: -1 }.thenBy { it.completedAt },
        )
    }

    /**
     * ADR-0041 의 "완료"는 그 시나리오 세션을 `COMPLETED` 로 끝낸 것이다. 점수
     * 하한은 두지 않는다 — 낮은 점수로 끝냈더라도 스스로 판단해 본 경험은 이미
     * 했고, 오히려 그때 남의 풀이가 가장 필요하다.
     *
     * 버전은 가리지 않는다. 벤치마크(§6.1)가 버전을 고정하는 것과 반대인데,
     * 거기서는 같은 문제를 푼 사람끼리 비교해야 하지만 여기서는 "직접 한 번
     * 씨름해 봤는가"만 물으면 되기 때문이다.
     */
    private fun hasCompleted(userId: UUID, versionIds: Collection<UUID>): Boolean =
        versionIds.isNotEmpty() &&
            sessionRepository.existsByUserIdAndScenarioVersionIdInAndStatus(userId, versionIds, SessionStatus.COMPLETED)

    /**
     * ADR-0043 / docs/LEARNING_COMMUNITY_PLAN.md §7 — only sessions on public
     * scenarios that aren't hiring-assessment results can be shared. Organization
     * and private scenarios are the organization's content; an assessment answer
     * is the employer's evaluation artifact.
     */
    private fun isShareable(session: Session): Boolean {
        val scenario = scenarioOf(session) ?: return false
        return isPublicScenario(scenario) && !assessmentSessions.isAssessment(session.id!!)
    }

    private fun isPublicScenario(scenario: Scenario): Boolean =
        scenario.organizationId == null && scenario.visibility == "PUBLIC"

    private fun versionIdsOf(scenarioId: UUID): List<UUID> =
        scenarioVersionRepository.findByScenarioIdIn(listOf(scenarioId)).mapNotNull { it.id }

    private fun scenarioOf(session: Session): Scenario? {
        val scenarioId = scenarioVersionRepository.findById(session.scenarioVersionId)
            .map { it.scenarioId }.orElse(null) ?: return null
        return scenarioRepository.findById(scenarioId).orElse(null)
    }

    private fun titleOf(scenario: Scenario): String =
        contentItemRepository.findById(scenario.contentId).map { it.title }.orElse(null) ?: scenario.domain

    private fun timelineOf(sessionId: UUID): List<TimelineEntry> {
        val report = reportRepository.findFirstBySessionIdOrderByVersionDesc(sessionId) ?: return emptyList()
        return ReportResponses.toResponse(report, objectMapper).timelineFeedback
    }

    /** 목록의 점수는 리포트에서 온다 — 리포트 화면의 점수와 다른 값이 나오면 안 된다. */
    private fun averageScores(sessionIds: Collection<UUID>): Map<UUID, Int?> =
        sessionIds.associateWith { com.sysdrill.backend.reporting.averageScore(timelineOf(it)) }

    private fun nicknamesOf(sessions: Collection<Session>): Map<UUID, String> {
        val ids = sessions.filterNot { it.sharedAnonymously }.map { it.userId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return userRepository.findAllById(ids).mapNotNull { user -> user.id?.let { it to user.nickname } }.toMap()
    }

    private fun Session.toVisibilityResponse(): SessionVisibilityResponse {
        val scenario = scenarioOf(this)
        return SessionVisibilityResponse(
            sessionId = id!!,
            visibility = visibility,
            anonymous = sharedAnonymously,
            sharedAt = sharedAt,
            scenarioId = scenario?.id,
            scenarioTitle = scenario?.let { titleOf(it) },
            completed = status == SessionStatus.COMPLETED,
        )
    }

    companion object {
        private const val PUBLIC = "PUBLIC"
        private val ALLOWED_VISIBILITIES = setOf("PRIVATE", PUBLIC)
    }
}
