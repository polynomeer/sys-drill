package com.sysdrill.backend.scenario

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.Rubric
import com.sysdrill.backend.organization.OrganizationAccessGuard
import com.sysdrill.backend.organization.OrganizationAuditAction
import com.sysdrill.backend.organization.OrganizationAuditLogService
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * PLAN.md step 34 — org-scoped private scenarios, authored via API instead of
 * a Flyway seed (docs/adr/0024, a deliberate partial exception to
 * docs/adr/0002 which still governs the public scenarios).
 *
 * ADR-0038 — an optional 3rd INCIDENT step is now allowed, but only for
 * [RuleBasedSimulationEngine.KNOWN_DOMAINS] (the 7 domains the simulation
 * engine actually has a formula for) — never a free-text domain, which is
 * exactly what would fall through to `RuleBasedSimulationEngine`'s
 * hardcoded dispatch `error(...)`. This keeps the engine/RuleEvaluator
 * completely unmodified; a real per-domain-formula authoring DSL remains a
 * separate, much larger candidate.
 *
 * ROADMAP.md Phase 4 "커스텀 루브릭" — an org can also optionally define its
 * own scoring dimensions ([CreateCustomScenarioRequest.rubricDimensions]),
 * stored in `Scenario.scoringProfile` and read by
 * [com.sysdrill.backend.evaluation.HybridRuleAiEvaluator] instead of the
 * default [Rubric].
 */
@Service
class CustomScenarioService(
    private val contentItemRepository: ContentItemRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val accessGuard: OrganizationAccessGuard,
    private val auditLog: OrganizationAuditLogService,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun create(orgId: UUID, adminUserId: UUID, request: CreateCustomScenarioRequest): ScenarioDetailResponse {
        accessGuard.requireAdmin(orgId, adminUserId)
        if (!request.incidentPrompt.isNullOrBlank() && request.domain !in RuleBasedSimulationEngine.KNOWN_DOMAINS) {
            throw BadRequestException(
                "domain must be one of ${RuleBasedSimulationEngine.KNOWN_DOMAINS} to include an incident step: ${request.domain}"
            )
        }
        if (request.rubricDimensions != null && request.rubricDimensions.values.sum() != Rubric.maxTotal) {
            throw BadRequestException("rubricDimensions must sum to ${Rubric.maxTotal}: got ${request.rubricDimensions.values.sum()}")
        }

        val content = contentItemRepository.save(
            ContentItem(type = "SCENARIO", title = request.title, difficulty = request.difficulty)
        )
        val scenario = scenarioRepository.save(
            Scenario(
                contentId = content.id!!,
                domain = request.domain,
                organizationId = orgId,
                // ROADMAP.md Phase 4 "커스텀 루브릭" — this column already existed
                // (seeded for every official scenario as a doc-reference pointer,
                // e.g. V2__seed_coupon_scenario.sql's {"rubricRef": "..."}) but was
                // never read anywhere; HybridRuleAiEvaluator now reads a "dimensions"
                // key from it when present. null (the default) means "use Rubric's
                // built-in 7-dimension set", same as every scenario before this field
                // had a second meaning.
                scoringProfile = request.rubricDimensions?.let { objectMapper.writeValueAsString(mapOf("dimensions" to it)) },
            )
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
        // ADR-0038 — same content shape as INITIAL/FOLLOWUP (and identical to
        // every official scenario's own INCIDENT step, e.g. V2__seed_coupon_scenario.sql)
        // — the domain check above already guarantees this reaches a real
        // SimulationEngine/RuleEvaluator instead of RuleBasedSimulationEngine's
        // `error(...)` fallback.
        if (!request.incidentPrompt.isNullOrBlank()) {
            scenarioStepRepository.save(
                ScenarioStep(
                    scenarioVersionId = version.id!!,
                    stepOrder = 3,
                    stepType = "INCIDENT",
                    triggerCondition = objectMapper.writeValueAsString(mapOf("afterStepOrder" to 2)),
                    content = objectMapper.writeValueAsString(mapOf("prompt" to request.incidentPrompt)),
                )
            )
        }
        auditLog.record(orgId, adminUserId, OrganizationAuditAction.CUSTOM_SCENARIO_CREATED, mapOf("scenarioId" to scenario.id.toString(), "title" to request.title))
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
