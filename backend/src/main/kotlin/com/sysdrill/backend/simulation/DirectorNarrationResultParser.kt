package com.sysdrill.backend.simulation

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Mirrors com.sysdrill.backend.postmortem.PostmortemCoachResultParser's approach (strip markdown fences, parse as JSON), for the smaller director-narration schema. */
@Component
class DirectorNarrationResultParser(private val objectMapper: ObjectMapper) {

    fun parse(rawText: String): DirectorNarrationResult {
        val cleaned = stripMarkdownFences(rawText)
        return try {
            objectMapper.readValue(cleaned, DirectorNarrationResult::class.java)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to parse director narration JSON: ${ex.message}", ex)
        }
    }

    private fun stripMarkdownFences(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
