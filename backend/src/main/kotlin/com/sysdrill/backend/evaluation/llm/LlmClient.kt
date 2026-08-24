package com.sysdrill.backend.evaluation.llm

data class LlmCompletionResult(
    val text: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Int,
)

interface LlmClient {
    fun complete(systemPrompt: String, userPrompt: String): LlmCompletionResult
}
