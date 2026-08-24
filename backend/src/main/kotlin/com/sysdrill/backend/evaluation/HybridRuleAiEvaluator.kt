package com.sysdrill.backend.evaluation

import com.sysdrill.backend.evaluation.llm.LlmClient
import com.sysdrill.backend.evaluation.llm.LlmEvaluationResultParser
import com.sysdrill.backend.submission.Submission
import org.springframework.stereotype.Component

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
) {
    private val purpose = "design_evaluation"

    fun evaluate(submission: Submission): HybridEvaluationOutcome {
        val template = promptTemplateRepository.findFirstByPurposeAndActiveTrue(purpose)
            ?: error("No active prompt template for purpose=$purpose")

        val ruleFindings = RuleEvaluator.evaluate(submission.rawText)
        val userPrompt = buildUserPrompt(ruleFindings, submission)

        val completion = llmClient.complete(template.templateBody, userPrompt)
        val llmResult = resultParser.parse(completion.text)
        val score = Rubric.validateAndScore(llmResult.rubricScores)

        return HybridEvaluationOutcome(
            promptTemplateId = template.id!!,
            rubricVersion = "prd-10-v${template.version}",
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

    private fun buildUserPrompt(ruleFindings: List<RuleFinding>, submission: Submission): String = buildString {
        appendLine("## 사용자 제출 답안")
        appendLine(submission.rawText?.takeIf { it.isNotBlank() } ?: "(제출된 텍스트가 없습니다)")
        appendLine()
        appendLine("## 규칙 기반 사전 점검 결과 (참고용)")
        if (ruleFindings.isEmpty()) {
            appendLine("- 특이사항 없음")
        } else {
            ruleFindings.forEach { appendLine("- [${it.severity}] ${it.description}") }
        }
    }
}
