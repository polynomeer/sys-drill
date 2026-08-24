package com.sysdrill.backend.evaluation

/** docs/PRD.md §10 — the MVP's single 100-point rubric, shared by every scenario. */
object Rubric {

    val dimensions: Map<String, Int> = linkedMapOf(
        "요구사항 해석력" to 15,
        "아키텍처 적합성" to 20,
        "트레이드오프 설명" to 15,
        "운영 리스크 인식" to 15,
        "장애 대응 판단" to 20,
        "Observability" to 10,
        "커뮤니케이션" to 5,
    )

    val maxTotal: Int = dimensions.values.sum()

    /**
     * Recomputes the total from per-dimension scores rather than trusting
     * whatever total the LLM reported, clamping each dimension to its max
     * and ignoring unrecognized dimension names — the "구조화 JSON 스키마 검증"
     * PLAN.md step 5 asks for.
     */
    fun validateAndScore(rubricScores: Map<String, Int>): Int =
        dimensions.entries.sumOf { (name, max) -> (rubricScores[name] ?: 0).coerceIn(0, max) }
}
