package com.sysdrill.backend.architecture

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioResponses
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioSummaryResponse
import com.sysdrill.backend.scenario.ScenarioVersion
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import io.swagger.v3.parser.OpenAPIV3Parser
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Phase 6 — Architecture Linter v1 (docs/adr/0034). The uploaded OpenAPI
 * spec is parsed, scanned, and used to synthesize a scenario in the same
 * shape [com.sysdrill.backend.scenario.CustomScenarioService.create] and
 * [com.sysdrill.backend.scenario.MarketplaceScenarioService.publish] already
 * build — INITIAL+FOLLOWUP only, no Incident/Wargame. The spec content
 * itself is never persisted; only the derived scenario is.
 */
@Service
class ArchitectureAnalysisService(
    private val contentItemRepository: ContentItemRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun analyze(userId: UUID, openApiSpecContent: String): ArchitectureAnalysisResponse {
        val result = OpenAPIV3Parser().readContents(openApiSpecContent, null, null)
        val openApi = result.openAPI ?: throw BadRequestException(
            "OpenAPI 스펙을 파싱할 수 없습니다: ${result.messages?.joinToString("; ") ?: "알 수 없는 오류"}"
        )
        val findings = ArchitectureRiskScanner.scan(openApi)
        val diagram = ArchitectureDiagramGenerator.generate(openApi, findings)

        val title = openApi.info?.title?.takeIf { it.isNotBlank() } ?: "내 API 정적 분석"
        val findingDescriptions = findings.map { it.description }

        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = title, difficulty = "MEDIUM"))
        val scenario = scenarioRepository.save(
            Scenario(contentId = content.id!!, domain = "architecture-analysis", creatorUserId = userId, visibility = "PRIVATE")
        )
        val version = scenarioVersionRepository.save(
            ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED")
        )
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!,
                stepOrder = 1,
                stepType = "INITIAL",
                content = objectMapper.writeValueAsString(mapOf("prompt" to buildInitialPrompt(title, findingDescriptions))),
            )
        )
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!,
                stepOrder = 2,
                stepType = "FOLLOWUP",
                triggerCondition = objectMapper.writeValueAsString(mapOf("afterStepOrder" to 1)),
                content = objectMapper.writeValueAsString(mapOf("prompt" to FOLLOWUP_PROMPT)),
            )
        )

        return ArchitectureAnalysisResponse(
            scenario = ScenarioResponses.toDetail(scenario, content, objectMapper),
            findings = findingDescriptions,
            diagram = diagram,
        )
    }

    fun listMine(userId: UUID): List<ScenarioSummaryResponse> {
        val scenarios = scenarioRepository.findByCreatorUserIdAndVisibility(userId, "PRIVATE")
        val contentById = contentItemRepository.findAllById(scenarios.map { it.contentId }).associateBy { it.id }
        return scenarios.map { ScenarioResponses.toSummary(it, contentById[it.contentId]) }
    }

    private fun buildInitialPrompt(title: String, findings: List<String>): String {
        if (findings.isEmpty()) {
            return "\"$title\" API 스펙에서는 규칙 기반 스캐너가 뚜렷한 문제를 찾지 못했습니다. 그래도 이 API를 실제 운영한다고 가정하고, 트래픽 급증·의존 서비스 장애 상황에서 어떤 부분이 가장 먼저 흔들릴지 설계해보세요."
        }
        val bulleted = findings.joinToString("\n") { "- $it" }
        return "\"$title\" API 스펙을 정적 분석한 결과 다음과 같은 문제가 발견됐습니다:\n\n$bulleted\n\n이 API를 실제로 운영하는 담당자라고 가정하고, 위 문제들을 어떻게 보완할지 설계하세요."
    }

    private companion object {
        const val FOLLOWUP_PROMPT =
            "위에서 설계한 보완책을 적용했다고 가정합니다. 그런데 트래픽이 평소의 10배로 급증하면서, 방금 보완한 부분과는 다른 지점에서 새로운 문제가 나타나기 시작했습니다. 어떤 지점이 취약할지, 그리고 어떻게 대응할지 설계를 이어가세요."
    }
}
