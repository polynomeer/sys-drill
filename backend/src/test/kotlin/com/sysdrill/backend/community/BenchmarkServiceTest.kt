package com.sysdrill.backend.community

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

/**
 * 순수 단위 테스트 — 백분위 계산은 결정론적이므로 정확한 값을 단언한다
 * (docs/TESTING.md §1 의 "결과가 결정론적이냐" 기준).
 */
class BenchmarkPercentileTest {

    private fun p(values: List<Long>, p: Int) = BenchmarkService.percentile(values.sorted(), p)

    @Test
    fun `최근접 순위법이라 결과는 항상 실제 표본 중 하나다`() {
        val sample = listOf(10L, 20L, 30L, 40L)
        // 보간했다면 p50 이 25가 되지만, 최근접 순위법은 ceil(0.5*4)=2번째 값을 고른다.
        assertThat(p(sample, 50)).isEqualTo(20L)
        assertThat(sample).contains(p(sample, 50), p(sample, 90))
    }

    @Test
    fun `p90 은 위쪽 꼬리를 가리킨다`() {
        val sample = (1L..10L).toList()
        assertThat(p(sample, 50)).isEqualTo(5L)
        assertThat(p(sample, 90)).isEqualTo(9L)
    }

    @Test
    fun `표본이 하나여도 경계를 벗어나지 않는다`() {
        assertThat(p(listOf(7L), 50)).isEqualTo(7L)
        assertThat(p(listOf(7L), 90)).isEqualTo(7L)
    }

    @Test
    fun `빈 표본은 계산하지 않고 거부한다`() {
        assertThatIllegalArgumentException().isThrownBy { BenchmarkService.percentile(emptyList(), 50) }
    }
}
