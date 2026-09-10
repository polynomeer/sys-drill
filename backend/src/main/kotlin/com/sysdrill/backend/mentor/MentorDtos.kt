package com.sysdrill.backend.mentor

data class MentorHintRequest(
    val rawText: String? = null,
)

data class MentorHintResponse(
    val hints: List<String>,
)
