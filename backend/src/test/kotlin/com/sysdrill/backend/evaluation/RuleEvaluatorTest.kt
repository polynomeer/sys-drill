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
    fun `returns no findings for an unknown domain`() {
        assertThat(RuleEvaluator.evaluate(null, "unknown-domain")).isEmpty()
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

    @Test
    fun `flags all three reservation concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 API 서버 하나로 처리합니다.", "reservation")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_RESERVATION_LOCKING",
            "MISSING_INVENTORY_CONSISTENCY",
            "MISSING_RESERVATION_TIMEOUT",
        )
    }

    @Test
    fun `does not flag inventory consistency when the submission mentions atomic compare-and-swap`() {
        val findings = RuleEvaluator.evaluate("재고 확인과 확정을 compare-and-swap으로 원자적으로 처리합니다.", "reservation")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_INVENTORY_CONSISTENCY")
    }

    @Test
    fun `reservation riskKeys do not collide with any other domain's riskKeys`() {
        val reservationKeys = RuleEvaluator.evaluate(null, "reservation").map { it.riskKey }.toSet()
        val otherKeys = listOf("coupon", "notification", "product-browsing", "payment")
            .flatMap { RuleEvaluator.evaluate(null, it).map { finding -> finding.riskKey } }
            .toSet()

        assertThat(reservationKeys).doesNotContainAnyElementsOf(otherKeys)
    }

    @Test
    fun `flags all three batch-settlement concepts when the submission mentions none of them`() {
        val findings = RuleEvaluator.evaluate("그냥 전체 레코드를 한 번에 처리합니다.", "batch-settlement")

        assertThat(findings.map { it.riskKey }).containsExactlyInAnyOrder(
            "MISSING_CHUNKING",
            "MISSING_RESTARTABILITY",
            "MISSING_RECONCILIATION",
        )
    }

    @Test
    fun `does not flag restartability when the submission mentions checkpoint`() {
        val findings = RuleEvaluator.evaluate("매 청크마다 체크포인트를 저장해 실패 시 이어서 재개합니다.", "batch-settlement")

        assertThat(findings.map { it.riskKey }).doesNotContain("MISSING_RESTARTABILITY")
    }

    @Test
    fun `batch-settlement riskKeys do not collide with any other domain's riskKeys`() {
        val batchSettlementKeys = RuleEvaluator.evaluate(null, "batch-settlement").map { it.riskKey }.toSet()
        val otherKeys = listOf("coupon", "notification", "product-browsing", "payment", "reservation")
            .flatMap { RuleEvaluator.evaluate(null, it).map { finding -> finding.riskKey } }
            .toSet()

        assertThat(batchSettlementKeys).doesNotContainAnyElementsOf(otherKeys)
    }
}
