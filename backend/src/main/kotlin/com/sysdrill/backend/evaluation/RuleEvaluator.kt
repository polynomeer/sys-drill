package com.sysdrill.backend.evaluation

data class RuleFinding(val riskKey: String, val severity: String, val description: String)

/**
 * The deterministic half of the Rule+AI hybrid pipeline (docs/ARCHITECTURE.md
 * §1/§7): decidable facts belong in a rule engine, not the LLM. v1 is a
 * simple keyword scan for the concepts docs/PRD.md §8.1 says the "선착순
 * 쿠폰" scenario should be evaluated on ("평가 포인트: 멱등성 키, 동시성 제어,
 * rate limiting, ... p95/error rate/DB lock 관측"). A real static/semantic
 * analyzer is future work; this only proves the rule-engine-feeds-the-LLM
 * shape end to end.
 */
object RuleEvaluator {

    private data class Concept(val riskKey: String, val keywords: List<String>, val description: String)

    private val couponScenarioConcepts = listOf(
        Concept(
            "MISSING_IDEMPOTENCY",
            listOf("멱등", "idempoten"),
            "멱등성(idempotency) 처리에 대한 언급이 없습니다. 중복 발급 방지를 어떻게 보장하는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_CONCURRENCY_CONTROL",
            listOf("동시성", "lock", "락", "concurrency", "트랜잭션", "transaction"),
            "동시성 제어(락/트랜잭션)에 대한 언급이 없습니다. 선착순 발급의 재고 경합을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_RATE_LIMIT",
            listOf("rate limit", "레이트 리밋", "요청 제한", "쓰로틀"),
            "Rate Limit에 대한 언급이 없습니다. 트래픽 급증 시 다운스트림 보호 전략이 필요합니다.",
        ),
        Concept(
            "MISSING_OBSERVABILITY",
            listOf("metric", "지표", "로그", "log", "관측", "alert", "알람", "trace"),
            "관측 가능성(metrics/logs/alert)에 대한 언급이 없습니다.",
        ),
    )

    fun evaluate(rawText: String?): List<RuleFinding> {
        val text = (rawText ?: "").lowercase()
        return couponScenarioConcepts
            .filter { concept -> concept.keywords.none { text.contains(it.lowercase()) } }
            .map { RuleFinding(it.riskKey, "MEDIUM", it.description) }
    }
}
