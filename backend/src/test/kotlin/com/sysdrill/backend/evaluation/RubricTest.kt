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
}
