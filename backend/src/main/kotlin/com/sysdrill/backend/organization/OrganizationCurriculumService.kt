package com.sysdrill.backend.organization

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * PLAN.md step 39 — a single ordered onboarding curriculum per organization
 * (docs/adr/0030: advisory, not gated — any curriculum scenario is startable
 * regardless of step order, same as today). Reuses the existing
 * Scenario/Session model entirely; the only new state is step order.
 */
@Service
class OrganizationCurriculumService(
    private val stepRepository: OrganizationCurriculumStepRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val sessionRepository: SessionRepository,
    private val accessGuard: OrganizationAccessGuard,
    private val auditLog: OrganizationAuditLogService,
) {

    @Transactional
    fun setCurriculum(orgId: UUID, adminUserId: UUID, scenarioIds: List<UUID>): CurriculumResponse {
        accessGuard.requireAdmin(orgId, adminUserId)

        val scenariosById = scenarioRepository.findAllById(scenarioIds).associateBy { it.id }
        scenarioIds.forEach { id ->
            val scenario = scenariosById[id] ?: throw NotFoundException("Scenario not found: $id")
            if (scenario.organizationId != null && scenario.organizationId != orgId) {
                throw NotFoundException("Scenario not found: $id")
            }
        }

        // flush() forces the deletes to hit the database before the inserts below —
        // otherwise Hibernate's action queue runs inserts before deletes within a
        // flush by default, and re-inserting the same (orgId, stepOrder) the old
        // rows still occupy violates the unique constraint.
        stepRepository.deleteByOrganizationId(orgId)
        stepRepository.flush()
        scenarioIds.forEachIndexed { index, scenarioId ->
            stepRepository.save(OrganizationCurriculumStep(organizationId = orgId, scenarioId = scenarioId, stepOrder = index + 1))
        }
        auditLog.record(orgId, adminUserId, OrganizationAuditAction.CURRICULUM_UPDATED, mapOf("stepCount" to scenarioIds.size))

        return getCurriculum(orgId, adminUserId)
    }

    fun getCurriculum(orgId: UUID, userId: UUID): CurriculumResponse {
        accessGuard.requireMember(orgId, userId)
        val steps = stepRepository.findByOrganizationIdOrderByStepOrder(orgId)
        if (steps.isEmpty()) return CurriculumResponse(emptyList())

        val scenariosById = scenarioRepository.findAllById(steps.map { it.scenarioId }).associateBy { it.id }
        val titleByScenarioId = contentItemRepository.findAllById(scenariosById.values.map { it.contentId }).associateBy { it.id }
            .let { contentById -> scenariosById.mapValues { (_, scenario) -> contentById[scenario.contentId]?.title ?: scenario.domain } }

        val completedSessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId).filter { it.status == SessionStatus.COMPLETED }
        val versionsById = scenarioVersionRepository.findAllById(completedSessions.map { it.scenarioVersionId }.distinct()).associateBy { it.id }
        val completedScenarioIds = completedSessions.mapNotNull { versionsById[it.scenarioVersionId]?.scenarioId }.toSet()

        return CurriculumResponse(
            steps.map { step ->
                val scenario = scenariosById[step.scenarioId]
                CurriculumStepResponse(
                    scenarioId = step.scenarioId,
                    title = titleByScenarioId[step.scenarioId] ?: scenario?.domain ?: "알 수 없음",
                    domain = scenario?.domain ?: "",
                    order = step.stepOrder,
                    completed = step.scenarioId in completedScenarioIds,
                )
            }
        )
    }
}
