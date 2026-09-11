package com.sysdrill.backend.evaluation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RubricTest {

    @Test
    fun `dimensions sum to 100 per docs PRD md section 10`() {
        assertThat(Rubric.maxTotal).isEqualTo(100)
    }

    @Test
    fun `sums per-dimension scores rather than trusting a reported total`() {
        val scores = mapOf(
            "요구사항 해석력" to 10,
            "아키텍처 적합성" to 15,
            "트레이드오프 설명" to 10,
            "운영 리스크 인식" to 10,
            "장애 대응 판단" to 15,
            "Observability" to 8,
            "커뮤니케이션" to 4,
        )

        assertThat(Rubric.validateAndScore(scores)).isEqualTo(72)
    }

    @Test
    fun `clamps a dimension score that exceeds its max`() {
        val scores = mapOf("커뮤니케이션" to 999)
        assertThat(Rubric.validateAndScore(scores)).isEqualTo(5) // clamped to the dimension's max
    }

    @Test
    fun `treats missing dimensions as zero and ignores unknown dimension names`() {
        val scores = mapOf("아키텍처 적합성" to 20, "이상한_키" to 100)
        assertThat(Rubric.validateAndScore(scores)).isEqualTo(20)
    }

    /** ROADMAP.md Phase 4 "커스텀 루브릭" — an explicit dimensions override scores against that set instead of the default 7. */
    @Test
    fun `scores against a custom dimension set when one is passed explicitly`() {
        val customDimensions = mapOf("보안 검토" to 50, "비용 효율성" to 50)
        val scores = mapOf("보안 검토" to 40, "비용 효율성" to 30, "요구사항 해석력" to 15) // last one is a default-set name, ignored here

        assertThat(Rubric.validateAndScore(scores, customDimensions)).isEqualTo(70)
    }
}
