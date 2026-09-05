package com.sysdrill.backend.scenario

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.organization.OrganizationAccessGuard
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * PLAN.md step 34 — org-scoped private scenarios, authored via API instead of
 * a Flyway seed (docs/adr/0024, a deliberate partial exception to
 * docs/adr/0002 which still governs the public scenarios). v1 is fixed to
 * exactly two steps (INITIAL, FOLLOWUP) — no Incident/Wargame — so this
 * never touches RuleBasedSimulationEngine's hardcoded domain dispatch.
 */
@Service
class CustomScenarioService(
    private val contentItemRepository: ContentItemRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val accessGuard: OrganizationAccessGuard,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun create(orgId: UUID, adminUserId: UUID, request: CreateCustomScenarioRequest): ScenarioDetailResponse {
        accessGuard.requireAdmin(orgId, adminUserId)

        val content = contentItemRepository.save(
            ContentItem(type = "SCENARIO", title = request.title, difficulty = request.difficulty)
        )
        val scenario = scenarioRepository.save(
            Scenario(contentId = content.id!!, domain = request.domain, organizationId = orgId)
        )
        val version = scenarioVersionRepository.save(
            ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED")
        )
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!,
                stepOrder = 1,
                stepType = "INITIAL",
                content = objectMapper.writeValueAsString(mapOf("prompt" to request.initialPrompt)),
            )
        )
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!,
                stepOrder = 2,
                stepType = "FOLLOWUP",
                triggerCondition = objectMapper.writeValueAsString(mapOf("afterStepOrder" to 1)),
                content = objectMapper.writeValueAsString(mapOf("prompt" to request.followupPrompt)),
            )
        )
        return ScenarioResponses.toDetail(scenario, content, objectMapper)
    }

    fun listForOrganization(orgId: UUID, userId: UUID): List<ScenarioSummaryResponse> {
        accessGuard.requireMember(orgId, userId)
        return scenarioRepository.findByOrganizationId(orgId).map { scenario ->
            val content = contentItemRepository.findById(scenario.contentId).orElse(null)
            ScenarioResponses.toSummary(scenario, content)
        }
    }

    fun getForOrganization(orgId: UUID, scenarioId: UUID, userId: UUID): ScenarioDetailResponse {
        accessGuard.requireMember(orgId, userId)
        val scenario = scenarioRepository.findById(scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: $scenarioId") }
        if (scenario.organizationId != orgId) throw NotFoundException("Scenario not found: $scenarioId")
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return ScenarioResponses.toDetail(scenario, content, objectMapper)
    }
}
