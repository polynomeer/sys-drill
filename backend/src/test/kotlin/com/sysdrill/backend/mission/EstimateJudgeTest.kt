package com.sysdrill.backend.mission

import com.sysdrill.backend.learning.CapacityFormula
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

/** PLAN.md Round E9 — order-of-magnitude judging (M2/L6) and the Capacity Lab's formula parser. */
class EstimateJudgeTest {

    @Test
    fun `within a factor of two either way is on target`() {
        assertThat(EstimateJudge.judge("k", 200.0, 100.0).onTarget).isFalse() // log10(2)=0.301 > 0.3
        assertThat(EstimateJudge.judge("k", 199.0, 100.0).direction).isEqualTo("ON_TARGET")
        assertThat(EstimateJudge.judge("k", 51.0, 100.0).direction).isEqualTo("ON_TARGET")
        assertThat(EstimateJudge.judge("k", 30.0, 100.0).direction).isEqualTo("UNDER")
        assertThat(EstimateJudge.judge("k", 1000.0, 100.0).direction).isEqualTo("OVER")
    }

    @Test
    fun `a missing or non-positive estimate is MISSING, not a crash`() {
        assertThat(EstimateJudge.judge("k", null, 100.0).direction).isEqualTo("MISSING")
        assertThat(EstimateJudge.judge("k", 0.0, 100.0).direction).isEqualTo("MISSING")
        assertThat(EstimateJudge.judge("k", -5.0, 100.0).ratio).isNull()
    }

    @Test
    fun `formulas follow arithmetic precedence and parentheses`() {
        val vars = mapOf("dau" to 10_000_000.0, "posts" to 2.0)
        assertThat(CapacityFormula.evaluate("dau * posts / 86400", vars)).isCloseTo(231.481, Offset.offset(0.001))
        assertThat(CapacityFormula.evaluate("1 + 2 * 3", emptyMap())).isEqualTo(7.0)
        assertThat(CapacityFormula.evaluate("(1 + 2) * 3", emptyMap())).isEqualTo(9.0)
        assertThat(CapacityFormula.evaluate("-2 * -3", emptyMap())).isEqualTo(6.0)
    }

    @Test
    fun `anything but arithmetic over known names is rejected`() {
        assertThatThrownBy { CapacityFormula.evaluate("unknown * 2", emptyMap()) }.hasMessageContaining("Unknown variable")
        assertThatThrownBy { CapacityFormula.evaluate("2 ^ 3", emptyMap()) }.hasMessageContaining("Unexpected")
        assertThatThrownBy { CapacityFormula.evaluate("(1 + 2", emptyMap()) }.hasMessageContaining("Missing ')'")
    }
}
