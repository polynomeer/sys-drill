package com.sysdrill.backend.postmortem

/** The structured schema the system prompt (see V40 migration) instructs the model to return. */
data class PostmortemCoachResult(
    val strengths: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val followupQuestions: List<String> = emptyList(),
)
