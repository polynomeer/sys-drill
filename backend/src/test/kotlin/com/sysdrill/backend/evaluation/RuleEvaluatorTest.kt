package com.sysdrill.backend.evaluation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {

    @Test
    fun `flags all four coupon concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.", "coupon")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_IDEMPOTENCY",
            "MISSING_CONCURRENCY_CONTROL",
            "MISSING_RATE_LIMIT",
            "MISSING_OBSERVABILITY",
        )
    }

    @Test
    fun `does not flag idempotency when the submission mentions it`() {
        val findings = RuleEvaluator.evaluate("멱등성을 보장하기 위해 idempotency key를 사용합니다.", "coupon")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_IDEMPOTENCY")
    }

    @Test
    fun `is case-insensitive for English keywords`() {
        val findings = RuleEvaluator.evaluate("We apply Rate Limit at the gateway and log every request.", "coupon")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_RATE_LIMIT", "MISSING_OBSERVABILITY")
    }

    @Test
    fun `flags everything for a blank coupon submission`() {
        assertThat(RuleEvaluator.evaluate(null, "coupon")).hasSize(4)
        assertThat(RuleEvaluator.evaluate("", "coupon")).hasSize(4)
    }

    @Test
    fun `flags all five notification concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.", "notification")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_ASYNC_BOUNDARY",
            "MISSING_IDEMPOTENT_CONSUMER",
            "MISSING_RETRY_BACKOFF",
            "MISSING_DLQ",
            "MISSING_CIRCUIT_BREAKER",
        )
    }

    @Test
    fun `does not flag DLQ when the submission mentions it`() {
        val findings = RuleEvaluator.evaluate("실패한 메시지는 DLQ로 격리합니다.", "notification")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_DLQ")
    }

    @Test
    fun `flags all four product-browsing concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.", "product-browsing")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_CACHE_POLICY_SEPARATION",
            "MISSING_KEY_DISTRIBUTION",
            "MISSING_SINGLE_FLIGHT",
            "MISSING_READ_REPLICA",
        )
    }

    @Test
    fun `does not flag key distribution when the submission mentions hot key sharding`() {
        val findings = RuleEvaluator.evaluate("hot key는 샤딩으로 분산 처리합니다.", "product-browsing")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_KEY_DISTRIBUTION")
    }

    @Test
    fun `falls back to coupon concepts for an unknown domain`() {
        assertThat(RuleEvaluator.evaluate(null, "unknown-domain")).hasSize(4)
    }

    @Test
    fun `flags all three payment concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.", "payment")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_TRANSACTION_BOUNDARY",
            "MISSING_PAYMENT_IDEMPOTENCY",
            "MISSING_PG_RETRY_BACKOFF",
        )
    }

    @Test
    fun `does not flag transaction boundary when the submission mentions outbox`() {
        val findings = RuleEvaluator.evaluate("outbox 테이블에 이벤트를 기록한 뒤 별도로 발행합니다.", "payment")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_TRANSACTION_BOUNDARY")
    }

    @Test
    fun `payment riskKeys do not collide with notification or coupon riskKeys`() {
        val paymentKeys = RuleEvaluator.evaluate(null, "payment").map { it.riskKey }.toSet()
        val notificationKeys = RuleEvaluator.evaluate(null, "notification").map { it.riskKey }.toSet()
        val couponKeys = RuleEvaluator.evaluate(null, "coupon").map { it.riskKey }.toSet()

        assertThat(paymentKeys).doesNotContainAnyElementsOf(notificationKeys)
        assertThat(paymentKeys).doesNotContainAnyElementsOf(couponKeys)
    }
}
