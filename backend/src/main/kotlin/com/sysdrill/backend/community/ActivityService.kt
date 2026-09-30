package com.sysdrill.backend.community

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.OrganizationAssessmentRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.8 — social proof: who recently finished a
 * Drill, and a user's completion timeline.
 *
 * Exposure rules (PLAN.md "CodeCrafters 벤치마킹 P2" 공개 범위 원칙, after
 * docs/LEARNING_COMMUNITY_PLAN.md §6.4/§7):
 * - completions only — never in-progress sessions, failures or scores;
 * - users with `rankingOptOut` are left out of both, the same switch that
 *   hides them from rankings;
 * - public scenarios only (no organization or private scenarios);
 * - hiring-assessment sessions (`organization_assessments.result_session_id`)
 *   are never shown, even though they run on public scenarios.
 */
@Service
class ActivityService(
    private val sessionRepository: SessionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val userRepository: UserRepository,
    private val assessmentRepository: OrganizationAssessmentRepository,
) {

    fun recentCompletions(scenarioId: UUID, limit: Int = 5): List<RecentCompletionResponse> {
        val scenario = scenarioRepository.findById(scenarioId).orElse(null)
        if (scenario == null || scenario.organizationId != null || scenario.visibility != "PUBLIC") {
            throw NotFoundException("Scenario not found: $scenarioId")
        }
        val versionIds = scenarioVersionRepository.findByScenarioIdIn(listOf(scenarioId)).mapNotNull { it.id }
        if (versionIds.isEmpty()) return emptyList()

        val sessions = withoutAssessments(
            sessionRepository.findTop50ByScenarioVersionIdInAndStatusOrderByCompletedAtDesc(versionIds, SessionStatus.COMPLETED)
        )
        val usersById = userRepository.findAllById(sessions.map { it.userId }.toSet()).associateBy { it.id }
        return sessions
            .filter { usersById[it.userId]?.rankingOptOut == false && it.completedAt != null }
            .distinctBy { it.userId }
            .take(limit)
            .map { RecentCompletionResponse(nickname = usersById.getValue(it.userId).nickname, completedAt = it.completedAt!!) }
    }

    fun userActivity(userId: UUID): UserActivityResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
        if (user.rankingOptOut) return UserActivityResponse(nickname = user.nickname, hidden = true, entries = emptyList())

        val completed = withoutAssessments(
            sessionRepository.findByUserIdOrderByStartedAtDesc(userId).filter { it.status == SessionStatus.COMPLETED && it.completedAt != null }
        )
        val versions = scenarioVersionRepository.findAllById(completed.map { it.scenarioVersionId }.toSet()).associateBy { it.id }
        val scenarios = scenarioRepository.findAllById(versions.values.map { it.scenarioId }.toSet()).associateBy { it.id }
        val contents = contentItemRepository.findAllById(scenarios.values.map { it.contentId }.toSet()).associateBy { it.id }

        val entries = completed.mapNotNull { session ->
            val scenario = versions[session.scenarioVersionId]?.let { scenarios[it.scenarioId] } ?: return@mapNotNull null
            if (scenario.organizationId != null || scenario.visibility != "PUBLIC") return@mapNotNull null
            ActivityEntryResponse(
                scenarioTitle = contents[scenario.contentId]?.title ?: scenario.domain,
                domain = scenario.domain,
                completedAt = session.completedAt!!,
            )
        }.sortedByDescending { it.completedAt }
        return UserActivityResponse(nickname = user.nickname, hidden = false, entries = entries)
    }

    private fun withoutAssessments(sessions: List<Session>): List<Session> {
        if (sessions.isEmpty()) return sessions
        val assessmentSessionIds = assessmentRepository.findByResultSessionIdIn(sessions.mapNotNull { it.id })
            .mapNotNull { it.resultSessionId }
            .toSet()
        return sessions.filterNot { it.id in assessmentSessionIds }
    }
}
