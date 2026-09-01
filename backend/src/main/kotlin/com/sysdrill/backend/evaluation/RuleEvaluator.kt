package com.sysdrill.backend.evaluation

data class RuleFinding(val riskKey: String, val severity: String, val description: String)

/**
 * The deterministic half of the Rule+AI hybrid pipeline (docs/ARCHITECTURE.md
 * §1/§7): decidable facts belong in a rule engine, not the LLM. v1 is a
 * simple keyword scan for the "평가 포인트" concepts docs/PRD.md §8 lists per
 * scenario — one concept list per domain, since a coupon-scenario answer and
 * a notification-scenario answer aren't judged on the same vocabulary. A
 * real static/semantic analyzer is future work; this only proves the
 * rule-engine-feeds-the-LLM shape end to end. Applied to every phase's
 * submission (INITIAL/FOLLOWUP/INCIDENT) rather than varying by phase — see
 * PLAN.md step 7 notes on reusing the retrospective submission as-is.
 */
object RuleEvaluator {

    private data class Concept(val riskKey: String, val keywords: List<String>, val description: String)

    /** docs/PRD.md §8.1 평가 포인트: 멱등성 키, 동시성 제어, rate limiting, ... 관측. */
    private val couponConcepts = listOf(
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

    /** docs/PRD.md §8.2 평가 포인트: 비동기 경계, idempotent consumer, retry/backoff, DLQ, provider별 circuit breaker. */
    private val notificationConcepts = listOf(
        Concept(
            "MISSING_ASYNC_BOUNDARY",
            listOf("비동기", "async", "queue", "메시지 큐", "kafka", "이벤트 큐"),
            "비동기 처리 경계에 대한 언급이 없습니다. 이벤트 발행과 전달을 어떻게 분리했는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_IDEMPOTENT_CONSUMER",
            listOf("idempotent", "멱등", "중복 처리", "중복 전송"),
            "idempotent consumer에 대한 언급이 없습니다. 재시도로 인한 중복 발송을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_RETRY_BACKOFF",
            listOf("retry", "재시도", "backoff", "백오프"),
            "retry/backoff 전략에 대한 언급이 없습니다. provider 장애 시 재시도 폭풍을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_DLQ",
            listOf("dlq", "dead letter", "데드레터", "실패 큐"),
            "DLQ(dead letter queue)에 대한 언급이 없습니다. poison message를 어떻게 격리하는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_CIRCUIT_BREAKER",
            listOf("circuit breaker", "서킷 브레이커", "장애 격리"),
            "provider별 circuit breaker에 대한 언급이 없습니다. 특정 provider 장애가 전체 처리량에 전이되지 않도록 격리했는지 확인이 필요합니다.",
        ),
    )

    /** docs/PRD.md §8.3 평가 포인트: 데이터별 캐시 정책 분리, key 분산, single-flight/lock, read replica. */
    private val productBrowsingConcepts = listOf(
        Concept(
            "MISSING_CACHE_POLICY_SEPARATION",
            listOf("캐시 정책", "ttl", "stale", "cache policy", "만료 시간"),
            "데이터별 캐시 정책 분리에 대한 언급이 없습니다. 가격처럼 최신성이 필요한 데이터와 리뷰처럼 stale을 허용하는 데이터를 어떻게 다르게 다루는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_KEY_DISTRIBUTION",
            listOf("hot key", "샤딩", "key 분산", "sharding", "파티셔닝", "분산"),
            "hot key 분산 전략에 대한 언급이 없습니다. 특정 상품에 트래픽이 몰릴 때 어떻게 대응하는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_SINGLE_FLIGHT",
            listOf("single-flight", "singleflight", "락", "lock", "중복 요청 방지", "dogpile"),
            "single-flight/lock에 대한 언급이 없습니다. 동시에 캐시가 miss될 때 DB 요청이 중복 실행되는 것을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_READ_REPLICA",
            listOf("read replica", "복제본", "리드 레플리카", "읽기 전용"),
            "read replica에 대한 언급이 없습니다. 읽기 트래픽 증가에 대비한 확장 전략이 필요합니다.",
        ),
    )

    /**
     * docs/PRD.md §8 "주문/결제" 평가 포인트: transaction boundary, outbox/saga,
     * idempotency. riskKey는 다른 도메인과 겹치지 않도록 domain-qualified로
     * 짓는다 — [domainByRiskKey]가 riskKey 하나당 도메인 하나를 가정하므로,
     * "결제의 재시도"와 "알림의 재시도"처럼 개념은 비슷해도 도메인이 다르면
     * 반드시 다른 riskKey를 써야 한다.
     */
    private val paymentConcepts = listOf(
        Concept(
            "MISSING_TRANSACTION_BOUNDARY",
            listOf("트랜잭션 경계", "outbox", "saga", "아웃박스", "이벤트 발행"),
            "DB 트랜잭션과 외부 결제 API 호출을 어떻게 분리했는지(outbox/saga)에 대한 언급이 없습니다. 하나의 트랜잭션으로 묶을 수 없는 두 작업을 어떻게 일관되게 처리하는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_PAYMENT_IDEMPOTENCY",
            listOf("멱등", "idempoten"),
            "결제 재시도 시 이중 결제를 어떻게 막는지(멱등성 키)에 대한 언급이 없습니다.",
        ),
        Concept(
            "MISSING_PG_RETRY_BACKOFF",
            listOf("retry", "재시도", "backoff", "백오프"),
            "PG 장애 시 재시도 전략에 대한 언급이 없습니다. 응답 유실(partial failure)에 어떻게 대응하는지 확인이 필요합니다.",
        ),
    )

    /** docs/PRD.md §8 "예약 시스템" 평가 포인트: locking, inventory consistency, timeout. */
    private val reservationConcepts = listOf(
        Concept(
            "MISSING_RESERVATION_LOCKING",
            listOf("락", "lock", "잠금", "동시성"),
            "동시 예약 요청 시 자원(좌석/슬롯) 잠금을 어떻게 관리하는지에 대한 언급이 없습니다. 락의 세분화 수준(전체 vs 개별 자원)까지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_INVENTORY_CONSISTENCY",
            listOf("재고", "inventory", "정합성", "원자적", "atomic", "compare-and-swap", "cas"),
            "예약 가능 수량 확인과 예약 확정을 원자적으로 처리하는지(재고 정합성)에 대한 언급이 없습니다. 중복 예약(overbooking) 위험을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_RESERVATION_TIMEOUT",
            listOf("타임아웃", "timeout", "홀드", "hold", "자동 해제", "만료"),
            "예약 홀드(hold) 시간 초과 시 자동 해제 처리에 대한 언급이 없습니다. 결제를 완료하지 않고 이탈한 예약이 자원을 계속 점유하지 않는지 확인이 필요합니다.",
        ),
    )

    /** docs/PRD.md §8 "배치/정산" 평가 포인트: chunking, restartability, reconciliation. */
    private val batchSettlementConcepts = listOf(
        Concept(
            "MISSING_CHUNKING",
            listOf("청킹", "chunk", "배치 크기", "batch size", "분할 처리"),
            "대용량 배치를 작은 단위로 나누어 처리하는 전략(chunking)에 대한 언급이 없습니다. 청크 크기가 실패 시 재처리 범위와 처리량에 미치는 영향을 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_RESTARTABILITY",
            listOf("재시작", "restart", "체크포인트", "checkpoint", "재개"),
            "중간 실패 시 처음부터 재실행하지 않고 이어서 재개(restartability)하는 전략에 대한 언급이 없습니다. 체크포인트 없이 실패하면 이미 처리한 작업까지 모두 낭비됩니다.",
        ),
        Concept(
            "MISSING_RECONCILIATION",
            listOf("정합성", "reconciliation", "재처리", "중복 정산", "대사"),
            "재처리 시 이미 처리된 레코드가 중복 반영되지 않도록 하는 정산 정합성(reconciliation) 처리에 대한 언급이 없습니다.",
        ),
    )

    /** docs/PRD.md §8 "실시간 추천 API" 평가 포인트: 오토스케일링, 리소스 request/limit, 무중단 배포 안전장치. */
    private val autoscalingConcepts = listOf(
        Concept(
            "MISSING_AUTOSCALING",
            listOf("오토스케일", "autoscal", "hpa", "수평 확장", "horizontal pod", "replica"),
            "오토스케일링(HPA)에 대한 언급이 없습니다. 트래픽 급증에 맞춰 Pod 수를 어떻게 조절하는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_RESOURCE_LIMITS",
            listOf("리소스 제한", "resource limit", "requests", "limits", "메모리 제한", "cpu 제한", "oom"),
            "Pod의 리소스 request/limit 설정에 대한 언급이 없습니다. 메모리 초과로 인한 OOM kill·재시작 반복을 어떻게 막는지 확인이 필요합니다.",
        ),
        Concept(
            "MISSING_ROLLOUT_SAFETY",
            listOf("롤링", "rolling update", "readiness", "무중단", "maxunavailable", "pdb", "disruption budget"),
            "무중단 배포(readiness probe/PodDisruptionBudget)에 대한 언급이 없습니다. 배포 도중 가용 용량이 줄어들지 않는지 확인이 필요합니다.",
        ),
    )

    private val conceptsByDomain = mapOf(
        "coupon" to couponConcepts,
        "notification" to notificationConcepts,
        "product-browsing" to productBrowsingConcepts,
        "payment" to paymentConcepts,
        "reservation" to reservationConcepts,
        "batch-settlement" to batchSettlementConcepts,
        "autoscaling" to autoscalingConcepts,
    )

    /** Reverse lookup (riskKey -> domain), derived from [conceptsByDomain] rather than duplicated — used by SkillProfileController (PLAN.md step 13) to group weaknesses by scenario domain. */
    val domainByRiskKey: Map<String, String> =
        conceptsByDomain.flatMap { (domain, concepts) -> concepts.map { it.riskKey to domain } }.toMap()

    fun evaluate(rawText: String?, domain: String): List<RuleFinding> {
        val concepts = conceptsByDomain[domain] ?: couponConcepts
        val text = (rawText ?: "").lowercase()
        return concepts
            .filter { concept -> concept.keywords.none { text.contains(it.lowercase()) } }
            .map { RuleFinding(it.riskKey, "MEDIUM", it.description) }
    }
}
