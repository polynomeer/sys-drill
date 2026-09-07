package com.sysdrill.backend.scenario

import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Phase 5 — Scenario Marketplace (docs/adr/0031). Any authenticated user
 * publishes a scenario, same INITIAL+FOLLOWUP-only shape as
 * [CustomScenarioService], but organizationId stays null and creatorUserId is
 * set instead — the scenario joins the same public pool
 * [ScenarioController] already serves, so session start needs no changes.
 */
@Service
class MarketplaceScenarioService(
    private val contentItemRepository: ContentItemRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun publish(creatorUserId: UUID, request: PublishMarketplaceScenarioRequest): ScenarioDetailResponse {
        val content = contentItemRepository.save(
            ContentItem(type = "SCENARIO", title = request.title, difficulty = request.difficulty)
        )
        val scenario = scenarioRepository.save(
            Scenario(contentId = content.id!!, domain = request.domain, creatorUserId = creatorUserId)
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
        val creatorNickname = userRepository.findById(creatorUserId).orElse(null)?.nickname
        return ScenarioResponses.toDetail(scenario, content, objectMapper, creatorNickname)
    }

    fun listAll(): List<ScenarioSummaryResponse> = toSummaries(scenarioRepository.findByCreatorUserIdIsNotNull())

    fun listMine(userId: UUID): List<ScenarioSummaryResponse> = toSummaries(scenarioRepository.findByCreatorUserId(userId))

    private fun toSummaries(scenarios: List<Scenario>): List<ScenarioSummaryResponse> {
        val contentById = contentItemRepository.findAllById(scenarios.map { it.contentId }).associateBy { it.id }
        val nicknameByCreatorId = userRepository.findAllById(scenarios.mapNotNull { it.creatorUserId }).associate { it.id to it.nickname }
        return scenarios.map { scenario ->
            ScenarioResponses.toSummary(scenario, contentById[scenario.contentId], scenario.creatorUserId?.let { nicknameByCreatorId[it] })
        }
    }
}
