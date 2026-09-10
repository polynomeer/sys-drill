package com.sysdrill.backend.postmortem

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Mirrors com.sysdrill.backend.evaluation.llm.LlmEvaluationResultParser's approach (strip markdown fences, parse as JSON), for the smaller postmortem-coaching schema. */
@Component
class PostmortemCoachResultParser(private val objectMapper: ObjectMapper) {

    fun parse(rawText: String): PostmortemCoachResult {
        val cleaned = stripMarkdownFences(rawText)
        return try {
            objectMapper.readValue(cleaned, PostmortemCoachResult::class.java)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to parse postmortem coaching JSON: ${ex.message}", ex)
        }
    }

    private fun stripMarkdownFences(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
