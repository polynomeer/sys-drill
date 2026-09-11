package com.sysdrill.backend.evaluation

import com.sysdrill.backend.evaluation.llm.LlmClient
import com.sysdrill.backend.evaluation.llm.LlmEvaluationResultParser
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.submission.Submission
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

data class HybridEvaluationOutcome(
    val promptTemplateId: java.util.UUID,
    val rubricVersion: String,
    val modelProvider: String,
    val modelName: String,
    val latencyMs: Int,
    val totalScore: Int,
    val rubricScores: Map<String, Int>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val followupQuestions: List<String>,
    val recommendedChanges: List<String>,
    val riskFlags: List<RuleFinding>,
)

/**
 * The real Rule+AI pipeline (docs/ARCHITECTURE.md §7) that replaces the
 * PLAN.md step 3 stub: decidable facts (missing concepts) come from
 * [RuleEvaluator]; everything requiring judgment (trade-off quality, risk
 * severity, follow-up questions) comes from the LLM, constrained to
 * structured JSON and re-validated against [Rubric] rather than trusted
 * as-is.
 */
@Component
class HybridRuleAiEvaluator(
    private val promptTemplateRepository: PromptTemplateRepository,
    private val llmClient: LlmClient,
    private val resultParser: LlmEvaluationResultParser,
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val objectMapper: ObjectMapper,
) {
    private val designPurpose = "design_evaluation"

    /**
     * AI 4역할 Slice 1 (Interviewer, docs/DRILLS_SIMULATION_VISION.md §6) —
     * `Session.interviewMode` sessions get a distinct, stricter interviewer
     * persona ([V39__seed_interview_evaluation_prompt.sql]) instead of the
     * default design-review persona. Deliberately reuses everything else
     * unchanged (same [Rubric], same JSON schema, same [LlmClient]) — only
     * the system prompt this `purpose` resolves to differs.
     */
    private val interviewPurpose = "interview_evaluation"

    fun evaluate(submission: Submission): HybridEvaluationOutcome {
        val session = sessionRepository.findById(submission.sessionId)
            .orElseThrow { error("Session not found: ${submission.sessionId}") }
        val purpose = if (session.interviewMode) interviewPurpose else designPurpose
        val template = promptTemplateRepository.findFirstByPurposeAndActiveTrue(purpose)
            ?: error("No active prompt template for purpose=$purpose")

        val scenario = resolveScenario(session)
        val customDimensions = resolveCustomDimensions(scenario.scoringProfile)
        val dimensions = customDimensions ?: Rubric.dimensions
        val ruleFindings = RuleEvaluator.evaluate(submission.rawText, scenario.domain)
        val userPrompt = buildUserPrompt(ruleFindings, submission, dimensions)

        val completion = llmClient.complete(template.templateBody, userPrompt)
        val llmResult = resultParser.parse(completion.text)
        val score = Rubric.validateAndScore(llmResult.rubricScores, dimensions)

        return HybridEvaluationOutcome(
            promptTemplateId = template.id!!,
            rubricVersion = "prd-10-$purpose-v${template.version}" + if (customDimensions != null) "-custom" else "",
            modelProvider = "anthropic",
            modelName = completion.model,
            latencyMs = completion.latencyMs,
            totalScore = score,
            rubricScores = llmResult.rubricScores,
            strengths = llmResult.strengths,
            weaknesses = llmResult.missedPoints,
            followupQuestions = llmResult.followupQuestions,
            recommendedChanges = llmResult.recommendedChanges,
            riskFlags = ruleFindings + llmResult.topRisks.map {
                RuleFinding(riskKey = "LLM_TOP_RISK", severity = "HIGH", description = it)
            },
        )
    }

    private fun resolveScenario(session: Session): Scenario {
        val version = scenarioVersionRepository.findById(session.scenarioVersionId)
            .orElseThrow { error("Scenario version not found: ${session.scenarioVersionId}") }
        return scenarioRepository.findById(version.scenarioId)
            .orElseThrow { error("Scenario not found: ${version.scenarioId}") }
    }

    /**
     * ROADMAP.md Phase 4 "커스텀 루브릭" — `Scenario.scoringProfile` predates
     * this feature (seeded for every official scenario as a doc-reference
     * pointer, e.g. `{"rubricRef": "..."}`) and was never read anywhere
     * before now. Returns null (→ caller falls back to [Rubric.dimensions])
     * unless it parses as JSON with a `"dimensions"` object — that covers
     * both `null` and the pre-existing `rubricRef` shape safely, without
     * needing to distinguish them explicitly.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveCustomDimensions(scoringProfile: String?): Map<String, Int>? {
        if (scoringProfile.isNullOrBlank()) return null
        return try {
            val parsed = objectMapper.readValue(scoringProfile, Map::class.java) as Map<String, Any>
            val raw = parsed["dimensions"] as? Map<String, Any> ?: return null
            raw.mapValues { (_, v) -> (v as Number).toInt() }
        } catch (ex: Exception) {
            null
        }
    }

    private fun buildUserPrompt(ruleFindings: List<RuleFinding>, submission: Submission, dimensions: Map<String, Int>): String = buildString {
        appendLine("## 사용자 제출 답안")
        appendLine(submission.rawText?.takeIf { it.isNotBlank() } ?: "(제출된 텍스트가 없습니다)")
        appendLine()
        appendLine("## 규칙 기반 사전 점검 결과 (참고용)")
        if (ruleFindings.isEmpty()) {
            appendLine("- 특이사항 없음")
        } else {
            ruleFindings.forEach { appendLine("- [${it.severity}] ${it.description}") }
        }
        // Always explicit here (not just left to the system prompt), so a
        // scenario-specific rubric (Phase 4 "커스텀 루브릭") overrides the
        // system prompt's own listing without needing a second system prompt
        // variant per scenario — total always equals Rubric.maxTotal (100),
        // enforced by CustomScenarioService.create's validation at write time.
        appendLine()
        appendLine("## 채점 루브릭 (총 ${dimensions.values.sum()}점) — 아래 항목만 사용하세요")
        dimensions.forEach { (name, max) -> appendLine("- $name ($max 점)") }
        // Only ever non-null for interviewMode sessions (SessionService.submit) — surfaces
        // the existing timer/deadline tracking to the interviewer persona, which otherwise
        // had no way to know a submission missed its phase deadline.
        submission.onTime?.let { onTime ->
            appendLine()
            appendLine("## 제출 시각")
            appendLine(if (onTime) "제한시간 내 제출" else "제한시간을 초과해 제출")
        }
    }
}
