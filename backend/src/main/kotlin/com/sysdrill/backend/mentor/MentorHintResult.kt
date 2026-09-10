package com.sysdrill.backend.mentor

/** The structured schema the system prompt (see V41 migration) instructs the model to return. */
data class MentorHintResult(
    val hints: List<String> = emptyList(),
)
