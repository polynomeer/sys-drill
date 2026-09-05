package com.sysdrill.backend.session

import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.OrganizationAccessGuard
import com.sysdrill.backend.organization.OrganizationMembershipRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

data class GameDaySessionResponse(
    val sessionId: UUID,
    val scenarioTitle: String,
    val ownerNickname: String,
    val domain: String,
    val status: SessionStatus,
    val currentPhase: String?,
    val startedAt: Instant,
)

/**
 * PLAN.md step 36 — "진행 중인 팀 세션", the entry point into Game Day
 * spectating: every active session started by a fellow member of this
 * organization, on whatever scenario they're doing (see
 * SessionAccessGuard.requireOwnerOrSpectator for why this isn't scoped to
 * the organization's own custom scenarios). Bulk lookups throughout — one
 * query per step, not per session — same shape as OrganizationService.getDashboard.
 */
@Service
class GameDaySessionService(
    private val organizationAccessGuard: OrganizationAccessGuard,
    private val membershipRepository: OrganizationMembershipRepository,
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
    private val userRepository: UserRepository,
) {

    fun listActiveForOrganization(orgId: UUID, userId: UUID): List<GameDaySessionResponse> {
        organizationAccessGuard.requireMember(orgId, userId)

        val memberIds = membershipRepository.findByOrganizationId(orgId).map { it.userId }
        if (memberIds.isEmpty()) return emptyList()

        val sessions = sessionRepository.findByUserIdInAndStatusNotIn(
            memberIds,
            listOf(SessionStatus.COMPLETED, SessionStatus.ABANDONED),
        )
        if (sessions.isEmpty()) return emptyList()

        val versionsById = scenarioVersionRepository.findAllById(sessions.map { it.scenarioVersionId }.distinct())
            .associateBy { it.id }
        val scenariosById = scenarioRepository.findAllById(versionsById.values.map { it.scenarioId }.distinct())
            .associateBy { it.id }
        val titleByScenarioId = contentItemRepository.findAllById(scenariosById.values.map { it.contentId }.distinct())
            .associateBy { it.id }
            .let { contentById -> scenariosById.mapValues { (_, scenario) -> contentById[scenario.contentId]?.title ?: scenario.domain } }
        val ownersById = userRepository.findAllById(sessions.map { it.userId }.distinct()).associateBy { it.id }

        return sessions.mapNotNull { session ->
            val version = versionsById[session.scenarioVersionId] ?: return@mapNotNull null
            val scenario = scenariosById[version.scenarioId] ?: return@mapNotNull null
            GameDaySessionResponse(
                sessionId = session.id!!,
                scenarioTitle = titleByScenarioId[scenario.id] ?: scenario.domain,
                ownerNickname = ownersById[session.userId]?.nickname ?: "알 수 없음",
                domain = scenario.domain,
                status = session.status,
                currentPhase = session.currentPhase,
                startedAt = session.startedAt,
            )
        }
    }
}
