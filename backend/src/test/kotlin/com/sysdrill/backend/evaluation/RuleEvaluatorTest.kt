package com.sysdrill.backend.evaluation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {

    @Test
    fun `flags all four concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_IDEMPOTENCY",
            "MISSING_CONCURRENCY_CONTROL",
            "MISSING_RATE_LIMIT",
            "MISSING_OBSERVABILITY",
        )
    }

    @Test
    fun `does not flag idempotency when the submission mentions it`() {
        val findings = RuleEvaluator.evaluate("멱등성을 보장하기 위해 idempotency key를 사용합니다.")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_IDEMPOTENCY")
    }

    @Test
    fun `is case-insensitive for English keywords`() {
        val findings = RuleEvaluator.evaluate("We apply Rate Limit at the gateway and log every request.")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_RATE_LIMIT", "MISSING_OBSERVABILITY")
    }

    @Test
    fun `flags everything for a blank submission`() {
        assertThat(RuleEvaluator.evaluate(null)).hasSize(4)
        assertThat(RuleEvaluator.evaluate("")).hasSize(4)
    }
}
