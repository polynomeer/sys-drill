package com.sysdrill.backend.evaluation.llm

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Anthropic Messages API client (docs/ARCHITECTURE.md's "LLM Provider API").
 *
 * If no API key is configured (LLM_ANTHROPIC_API_KEY — deliberately not
 * ANTHROPIC_API_KEY/ANTHROPIC_BASE_URL, which Claude Code itself may already
 * have set in this shell for unrelated purposes), this returns a canned
 * offline placeholder instead of failing outright, so the rest of the
 * evaluation pipeline stays exercisable without a real key. Once a key is
 * added, real calls take over automatically — no code change needed.
 */
@Component
class AnthropicLlmClient(
    @Value("\${sysdrill.llm.anthropic.base-url}") baseUrl: String,
    @Value("\${sysdrill.llm.anthropic.api-key:}") private val apiKey: String,
    @Value("\${sysdrill.llm.anthropic.model}") private val model: String,
    @Value("\${sysdrill.llm.anthropic.max-tokens}") private val maxTokens: Int,
) : LlmClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", ANTHROPIC_API_VERSION)
        .defaultHeader("content-type", "application/json")
        .build()

    override fun complete(systemPrompt: String, userPrompt: String): LlmCompletionResult {
        if (apiKey.isBlank()) {
            return fakeCompletion(userPrompt)
        }

        val request = AnthropicRequest(
            model = model,
            max_tokens = maxTokens,
            system = systemPrompt,
            messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
        )

        val startedAt = System.currentTimeMillis()
        val response = restClient.post()
            .uri("/v1/messages")
            .body(request)
            .retrieve()
            .body(AnthropicResponse::class.java)
            ?: error("Empty response body from Anthropic")
        val latencyMs = (System.currentTimeMillis() - startedAt).toInt()

        val text = response.content.firstOrNull { it.type == "text" }?.text
            ?: error("No text content block in Anthropic response")

        return LlmCompletionResult(
            text = text,
            model = model,
            inputTokens = response.usage?.input_tokens ?: 0,
            outputTokens = response.usage?.output_tokens ?: 0,
            latencyMs = latencyMs,
        )
    }

    private fun fakeCompletion(userPrompt: String): LlmCompletionResult {
        log.warn(
            "LLM_ANTHROPIC_API_KEY is not set — returning an offline placeholder evaluation. " +
                "Set it in backend/.env.local to enable real evaluations."
        )
        check(!userPrompt.contains(FORCE_FAILURE_MARKER)) {
            "Offline fallback forced failure for testing (marker found in prompt)"
        }
        return LlmCompletionResult(
            text = OFFLINE_FALLBACK_JSON,
            model = "offline-fallback",
            inputTokens = 0,
            outputTokens = 0,
            latencyMs = 0,
        )
    }

    companion object {
        const val FORCE_FAILURE_MARKER = "FORCE_EVAL_FAILURE"
        private const val ANTHROPIC_API_VERSION = "2023-06-01"

        @Suppress("ktlint:standard:max-line-length")
        private val OFFLINE_FALLBACK_JSON = """
            {
              "totalScore": 60,
              "rubricScores": {"요구사항 해석력": 10, "아키텍처 적합성": 12, "트레이드오프 설명": 8, "운영 리스크 인식": 8, "장애 대응 판단": 12, "Observability": 6, "커뮤니케이션": 4},
              "strengths": ["오프라인 모드: 실제 LLM 평가가 아닙니다."],
              "missedPoints": ["LLM_ANTHROPIC_API_KEY를 backend/.env.local에 설정하면 실제 평가를 받을 수 있습니다."],
              "topRisks": [],
              "followupQuestions": [],
              "recommendedChanges": []
            }
        """.trimIndent()
    }
}
