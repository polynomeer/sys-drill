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
    private val contentItemRepository: ContentItemRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun list(): List<ScenarioSummaryResponse> {
        val scenarios = scenarioRepository.findByOrganizationIdIsNullAndVisibility("PUBLIC")
        val nicknameByCreatorId = userRepository.findAllById(scenarios.mapNotNull { it.creatorUserId }).associate { it.id to it.nickname }
        return scenarios.map { scenario ->
            val content = contentItemRepository.findById(scenario.contentId).orElse(null)
            ScenarioResponses.toSummary(scenario, content, scenario.creatorUserId?.let { nicknameByCreatorId[it] })
        }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ScenarioDetailResponse {
        val scenario = scenarioRepository.findById(id).orElseThrow { NotFoundException("Scenario not found: $id") }
        if (scenario.organizationId != null || scenario.visibility == "PRIVATE") throw NotFoundException("Scenario not found: $id")
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        val creatorNickname = scenario.creatorUserId?.let { userRepository.findById(it).orElse(null)?.nickname }
        return ScenarioResponses.toDetail(scenario, content, objectMapper, creatorNickname)
    }
}
