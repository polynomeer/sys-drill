package com.sysdrill.backend.evaluation.llm

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class LlmEvaluationResultParser(private val objectMapper: ObjectMapper) {

    fun parse(rawText: String): LlmEvaluationResult {
        val cleaned = stripMarkdownFences(rawText)
        return try {
            objectMapper.readValue(cleaned, LlmEvaluationResult::class.java)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to parse LLM evaluation JSON: ${ex.message}", ex)
        }
    }

    private fun stripMarkdownFences(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
