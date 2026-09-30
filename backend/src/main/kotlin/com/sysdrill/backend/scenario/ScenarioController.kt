package com.sysdrill.backend.scenario

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

/** docs/ARCHITECTURE.md §10 API table: GET /scenarios, GET /scenarios/{id}. Public/unauthenticated — serves only public scenarios (PLAN.md step 34's org-scoped ones are 404 here, reachable only via /organizations/{orgId}/scenarios). Phase 5's marketplace scenarios (docs/adr/0031) are also organizationId == null, so they appear here too, distinguished by a non-null creatorNickname. Phase 6's Architecture Linter scenarios (docs/adr/0034) are visibility == "PRIVATE" and never appear here, even if requested by id — an unauthenticated endpoint has no caller identity to check ownership against. */
@RestController
@RequestMapping("/scenarios")
class ScenarioController(
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val scenarioStatsService: ScenarioStatsService,
    private val contentItemRepository: ContentItemRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun list(): List<ScenarioSummaryResponse> {
        val scenarios = scenarioRepository.findByOrganizationIdIsNullAndVisibility("PUBLIC")
        val scenarioIds = scenarios.mapNotNull { it.id }
        val contentById = contentItemRepository.findAllById(scenarios.map { it.contentId }).associateBy { it.id }
        val nicknameByCreatorId = userRepository.findAllById(scenarios.mapNotNull { it.creatorUserId }).associate { it.id to it.nickname }
        // docs/CODECRAFTERS_BENCHMARK.md §3.5 — the Drills catalog cards need stats and stage shape
        // for every public scenario; batched (a fixed number of queries, not one per scenario).
        val statsByScenarioId = scenarioStatsService.byScenarioId(scenarioIds)
        val stepTypesByScenarioId = publishedStepTypes(scenarioIds)
        return scenarios.map { scenario ->
            val stats = statsByScenarioId[scenario.id]
            ScenarioResponses.toSummary(scenario, contentById[scenario.contentId], scenario.creatorUserId?.let { nicknameByCreatorId[it] }).copy(
                completedCount = stats?.completedCount ?: 0,
                averageScore = stats?.averageScore,
                stepTypes = stepTypesByScenarioId[scenario.id].orEmpty(),
            )
        }
    }

    /** Latest PUBLISHED version per scenario — the same one SessionService.start picks — and its step types in order. */
    private fun publishedStepTypes(scenarioIds: Collection<UUID>): Map<UUID, List<String>> {
        if (scenarioIds.isEmpty()) return emptyMap()
        val latestVersionByScenarioId = scenarioVersionRepository.findByScenarioIdIn(scenarioIds)
            .filter { it.status == "PUBLISHED" }
            .groupBy { it.scenarioId }
            .mapValues { (_, versions) -> versions.maxBy { it.versionNo } }
        val scenarioIdByVersionId = latestVersionByScenarioId.entries.associate { (scenarioId, version) -> version.id!! to scenarioId }
        return scenarioStepRepository.findByScenarioVersionIdIn(scenarioIdByVersionId.keys)
            .groupBy { scenarioIdByVersionId.getValue(it.scenarioVersionId) }
            .mapValues { (_, steps) -> steps.sortedBy { it.stepOrder }.map { it.stepType } }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ScenarioDetailResponse {
        val scenario = scenarioRepository.findById(id).orElseThrow { NotFoundException("Scenario not found: $id") }
        if (scenario.organizationId != null || scenario.visibility == "PRIVATE") throw NotFoundException("Scenario not found: $id")
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        val creatorNickname = scenario.creatorUserId?.let { userRepository.findById(it).orElse(null)?.nickname }
        // Same version SessionService.start would pick, so the roadmap matches what the session will actually run.
        val version = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(id, "PUBLISHED")
        val steps = version?.let { scenarioStepRepository.findByScenarioVersionIdOrderByStepOrder(it.id!!) }.orEmpty()
        val initialPrompt = steps.firstOrNull { it.stepType == "INITIAL" }?.content
            ?.let { objectMapper.readValue(it, Map::class.java)["prompt"] as? String }
        val stats = scenarioStatsService.byScenarioId(listOf(id))[id]
        return ScenarioResponses.toDetail(scenario, content, objectMapper, creatorNickname).copy(
            steps = steps.map { ScenarioStepSummaryResponse(order = it.stepOrder, type = it.stepType) },
            initialPrompt = initialPrompt,
            completedCount = stats?.completedCount,
            averageScore = stats?.averageScore,
        )
    }
}
