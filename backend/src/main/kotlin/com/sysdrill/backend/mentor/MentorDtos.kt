package com.sysdrill.backend.mentor

import jakarta.validation.constraints.Size

/**
 * docs/COMMERCIALIZATION.md — [rawText] goes straight into an LLM prompt
 * ([MentorService.getHint]) with no other size check anywhere on this path
 * (unlike a real submission, a mentor hint isn't covered by
 * [com.sysdrill.backend.evaluation.LlmUsageGuard]'s daily counter either).
 * The cap is generous -- a few times longer than a realistic full design
 * answer -- it exists to bound worst-case cost/abuse, not to constrain
 * normal use.
 */
data class MentorHintRequest(
    @field:Size(max = 20_000, message = "답안은 20,000자를 넘을 수 없습니다") val rawText: String? = null,
)

data class MentorHintResponse(
    val hints: List<String>,
)
