package com.sysdrill.backend.mentor

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.evaluation.PromptTemplateRepository
import com.sysdrill.backend.evaluation.RuleEvaluator
import com.sysdrill.backend.evaluation.RuleFinding
import com.sysdrill.backend.evaluation.llm.LlmClient
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * AI 4역할 Slice 3 (Mentor, docs/DRILLS_SIMULATION_VISION.md §6) — an
 * on-demand hint for a draft the user hasn't submitted yet. Reuses the same
 * [RuleEvaluator] (docs which concepts the draft hasn't covered) +
 * [LlmClient]/[PromptTemplateRepository] (purpose-keyed system prompt)
 * plumbing [com.sysdrill.backend.evaluation.HybridRuleAiEvaluator] does for
 * scored evaluation, but produces hints instead — no [com.sysdrill.backend.evaluation.Rubric],
 * no persisted [com.sysdrill.backend.submission.Submission]. Nothing here is
 * persisted: a hint is thrown away the moment the response is sent, there's
 * no later point in re-reading "the hint for this draft" since the draft
 * itself keeps changing.
 */
@Service
class MentorService(
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val llmClient: LlmClient,
    private val resultParser: MentorHintResultParser,
) {

    fun getHint(sessionId: UUID, rawText: String?): MentorHintResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        val domain = sessionService.getScenarioDomain(session)

        val template = promptTemplateRepository.findFirstByPurposeAndActiveTrue(PURPOSE)
            ?: error("No active prompt template for purpose=$PURPOSE")
        val ruleFindings = RuleEvaluator.evaluate(rawText, domain)
        val userPrompt = buildUserPrompt(rawText, ruleFindings)

        val completion = llmClient.complete(template.templateBody, userPrompt)
        val result = resultParser.parse(completion.text)
        return MentorHintResponse(hints = result.hints)
    }

    private fun buildUserPrompt(rawText: String?, ruleFindings: List<RuleFinding>): String = buildString {
        appendLine("## 지금까지 작성한 답안")
        appendLine(rawText?.takeIf { it.isNotBlank() } ?: "(아직 아무것도 작성하지 않음)")
        appendLine()
        appendLine("## 아직 다루지 않은 것으로 확인된 개념 (참고용)")
        if (ruleFindings.isEmpty()) {
            appendLine("- 특이사항 없음")
        } else {
            ruleFindings.forEach { appendLine("- ${it.description}") }
        }
    }

    private companion object {
        const val PURPOSE = "mentor_hint"
    }
}
