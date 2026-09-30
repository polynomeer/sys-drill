package com.sysdrill.backend.community

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 순수 단위 테스트 — 티어 경계와 난이도 가중치는 DB와 무관하게 결정론적이다
 * (docs/TESTING.md §1).
 */
class DrillScoreTierTest {

    @Test
    fun `티어는 구간의 시작점에서 바뀐다`() {
        assertThat(DrillTier.of(0)).isEqualTo(DrillTier.TRAINEE)
        assertThat(DrillTier.of(99)).isEqualTo(DrillTier.TRAINEE)
        assertThat(DrillTier.of(100)).isEqualTo(DrillTier.OPERATOR)
        assertThat(DrillTier.of(299)).isEqualTo(DrillTier.OPERATOR)
        assertThat(DrillTier.of(300)).isEqualTo(DrillTier.RESPONDER)
        assertThat(DrillTier.of(500)).isEqualTo(DrillTier.ARCHITECT)
        assertThat(DrillTier.of(700)).isEqualTo(DrillTier.PRINCIPAL)
    }

    @Test
    fun `최고 티어 위로는 더 올라가지 않는다`() {
        assertThat(DrillTier.of(99999)).isEqualTo(DrillTier.PRINCIPAL)
    }

    @Test
    fun `어려운 난이도일수록 같은 점수가 더 많은 포인트가 된다`() {
        val easy = DrillScoreService.weightOf("EASY")
        val medium = DrillScoreService.weightOf("MEDIUM")
        val hard = DrillScoreService.weightOf("HARD")
        assertThat(easy).isLessThan(medium)
        assertThat(medium).isLessThan(hard)
    }

    @Test
    fun `난이도가 없거나 모르는 값이면 중간으로 본다 - 점수가 0이 되어 사라지지 않는다`() {
        val medium = DrillScoreService.weightOf("MEDIUM")
        assertThat(DrillScoreService.weightOf(null)).isEqualTo(medium)
        assertThat(DrillScoreService.weightOf("LEGENDARY")).isEqualTo(medium)
        // 대소문자는 가리지 않는다 — 시드 데이터가 소문자로 들어와도 가중이 유지된다.
        assertThat(DrillScoreService.weightOf("hard")).isEqualTo(DrillScoreService.weightOf("HARD"))
    }
}
