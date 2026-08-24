package com.sysdrill.backend.scenario

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

/** docs/ARCHITECTURE.md §10 API table: GET /scenarios, GET /scenarios/{id}. */
@RestController
@RequestMapping("/scenarios")
class ScenarioController(
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun list(): List<ScenarioSummaryResponse> = scenarioRepository.findAll().map { scenario ->
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        ScenarioSummaryResponse(
            id = scenario.id!!,
            domain = scenario.domain,
            title = content?.title ?: scenario.domain,
            difficulty = content?.difficulty,
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ScenarioDetailResponse {
        val scenario = scenarioRepository.findById(id).orElseThrow { NotFoundException("Scenario not found: $id") }
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return ScenarioDetailResponse(
            id = scenario.id!!,
            domain = scenario.domain,
            title = content?.title ?: scenario.domain,
            difficulty = content?.difficulty,
            baseRequirements = scenario.baseRequirements?.let { objectMapper.readValue(it, Any::class.java) },
        )
    }
}
