package com.sysdrill.backend.mentor

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Mirrors com.sysdrill.backend.postmortem.PostmortemCoachResultParser's approach (strip markdown fences, parse as JSON), for the smaller mentor-hint schema. */
@Component
class MentorHintResultParser(private val objectMapper: ObjectMapper) {

    fun parse(rawText: String): MentorHintResult {
        val cleaned = stripMarkdownFences(rawText)
        return try {
            objectMapper.readValue(cleaned, MentorHintResult::class.java)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to parse mentor hint JSON: ${ex.message}", ex)
        }
    }

    private fun stripMarkdownFences(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
