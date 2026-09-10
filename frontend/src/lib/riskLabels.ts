// Friendly labels for RuleEvaluator's riskKeys (backend/.../evaluation/RuleEvaluator.kt),
// used wherever a raw key like "MISSING_IDEMPOTENCY" would otherwise be shown as-is.
const LABELS: Record<string, string> = {
  MISSING_IDEMPOTENCY: "멱등성 처리",
  MISSING_CONCURRENCY_CONTROL: "동시성 제어",
  MISSING_RATE_LIMIT: "Rate Limit",
  MISSING_OBSERVABILITY: "관측 가능성",
  MISSING_ASYNC_BOUNDARY: "비동기 경계",
  MISSING_IDEMPOTENT_CONSUMER: "Idempotent Consumer",
  MISSING_RETRY_BACKOFF: "Retry/Backoff",
  MISSING_DLQ: "DLQ",
  MISSING_CIRCUIT_BREAKER: "Circuit Breaker",
  MISSING_CACHE_POLICY_SEPARATION: "캐시 정책 분리",
  MISSING_KEY_DISTRIBUTION: "Hot Key 분산",
  MISSING_SINGLE_FLIGHT: "Single-flight",
  MISSING_READ_REPLICA: "Read Replica",
  MISSING_TRANSACTION_BOUNDARY: "트랜잭션 경계 분리",
  MISSING_PAYMENT_IDEMPOTENCY: "결제 멱등성",
  MISSING_PG_RETRY_BACKOFF: "PG 재시도/백오프",
  MISSING_RESERVATION_LOCKING: "예약 락",
  MISSING_INVENTORY_CONSISTENCY: "재고 정합성",
  MISSING_RESERVATION_TIMEOUT: "예약 타임아웃",
  MISSING_CHUNKING: "청킹",
  MISSING_RESTARTABILITY: "재시작 가능성",
  MISSING_RECONCILIATION: "정산 정합성",
  MISSING_AUTOSCALING: "오토스케일링",
  MISSING_RESOURCE_LIMITS: "리소스 제한",
  MISSING_ROLLOUT_SAFETY: "무중단 배포",
};

/** app/learning/page.tsx 개념 레퍼런스용 — RuleEvaluator가 실제로 채점 기준
 * 삼는 패턴들이라, 세션 중 지적받는 놓친 점과 정확히 같은 어휘로 설명한다. */
const DESCRIPTIONS: Record<string, string> = {
  MISSING_IDEMPOTENCY:
    "같은 요청이 여러 번 도착해도(네트워크 재시도, 중복 클릭 등) 결과가 한 번 처리한 것과 동일하게 유지되도록 하는 설계. 멱등성 키, 처리 전 중복 확인 등으로 구현합니다.",
  MISSING_CONCURRENCY_CONTROL:
    "여러 요청이 동시에 같은 자원에 접근할 때 경쟁 상태(race condition)로 데이터가 꼬이지 않도록 막는 전략 — 락, 원자적 연산, 낙관적/비관적 동시성 제어 등.",
  MISSING_RATE_LIMIT:
    "단위 시간당 허용 요청 수를 제한해 다운스트림 시스템을 보호하는 트래픽 조절 기법. Token Bucket, Sliding Window 같은 알고리즘이 흔히 쓰입니다.",
  MISSING_OBSERVABILITY:
    "시스템 내부 상태를 metrics·logs·trace로 파악할 수 있게 하는 능력. 장애가 났을 때 무엇이, 왜 잘못됐는지 빠르게 진단하는 데 필수적입니다.",
  MISSING_ASYNC_BOUNDARY:
    "요청을 즉시 처리하는 부분과 큐/이벤트로 나중에 처리하는 부분을 명확히 나누는 설계. 응답 지연을 줄이고 장애가 다른 컴포넌트로 번지는 것을 막습니다.",
  MISSING_IDEMPOTENT_CONSUMER:
    "메시지 큐 컨슈머가 같은 메시지를 두 번 이상 받아도(at-least-once 전달 특성상 흔한 일) 중복 처리하지 않도록 하는 설계.",
  MISSING_RETRY_BACKOFF:
    "실패한 요청을 즉시 재시도하지 않고 점점 늘어나는 대기 시간을 두고 재시도해, 장애 상황에서 재시도 폭풍(retry storm)이 상황을 더 악화시키는 것을 막는 전략.",
  MISSING_DLQ:
    "정상 처리에 반복 실패하는 메시지(poison message)를 별도 큐로 격리해, 그 메시지 하나 때문에 전체 처리 파이프라인이 막히지 않도록 하는 장치.",
  MISSING_CIRCUIT_BREAKER:
    "특정 의존성(외부 API 등)이 계속 실패하면 일정 시간 호출 자체를 차단해, 느린 응답을 기다리며 자원을 낭비하지 않고 빠르게 실패(fail fast)하도록 하는 패턴.",
  MISSING_CACHE_POLICY_SEPARATION:
    "접근·변경 빈도가 다른 데이터에 각각 다른 TTL/캐시 전략을 적용해, 자주 바뀌는 데이터가 안 바뀌는 데이터의 캐시 효율까지 깎아먹지 않도록 하는 설계.",
  MISSING_KEY_DISTRIBUTION:
    "특정 키에 트래픽이 몰려 캐시나 DB의 한 파티션만 과부하되는 hot key 현상을 막기 위해 키를 샤딩하거나 복제하는 전략.",
  MISSING_SINGLE_FLIGHT:
    "같은 키에 대한 동시 요청이 여러 건이어도 실제 조회(DB 등)는 한 번만 수행하고 결과를 공유해, cache miss 시 요청이 한꺼번에 몰리는 dogpile 현상을 막는 기법.",
  MISSING_READ_REPLICA:
    "읽기 전용 복제본을 두어 읽기 트래픽을 분산시키는 전략. 복제 지연(replication lag)으로 인한 데이터 최신성 저하를 감수해야 합니다.",
  MISSING_TRANSACTION_BOUNDARY:
    "DB 트랜잭션과 외부 결제 API 호출처럼 하나의 트랜잭션으로 묶을 수 없는 두 작업을 outbox/saga 패턴 등으로 어떻게 일관되게 처리하는지에 대한 설계.",
  MISSING_PAYMENT_IDEMPOTENCY:
    "결제 재시도 시 멱등성 키 등으로 이중 결제를 막는 설계.",
  MISSING_PG_RETRY_BACKOFF:
    "PG(결제대행사) 장애나 응답 유실(partial failure) 시 재시도 전략에 대한 설계.",
  MISSING_RESERVATION_LOCKING:
    "동시 예약 요청 시 자원(좌석/슬롯) 잠금을 어떻게 관리하는지에 대한 설계 — 락의 세분화 수준(전체 vs 개별 자원)이 유효 처리 용량을 좌우합니다.",
  MISSING_INVENTORY_CONSISTENCY:
    "예약 가능 수량 확인과 예약 확정을 원자적으로 처리해 중복 예약(overbooking)을 막는 재고 정합성 설계.",
  MISSING_RESERVATION_TIMEOUT:
    "결제를 완료하지 않고 이탈한 예약 홀드(hold)를 시간 초과 시 자동 해제해, 자원을 계속 점유하지 않도록 하는 설계.",
  MISSING_CHUNKING:
    "대용량 배치를 작은 단위로 나누어 처리하는 전략. 청크 크기가 실패 시 재처리 범위와 처리량에 미치는 영향까지 고려해야 합니다.",
  MISSING_RESTARTABILITY:
    "중간 실패 시 처음부터 재실행하지 않고 체크포인트부터 이어서 재개하는 설계 — 없으면 이미 처리한 작업까지 모두 낭비됩니다.",
  MISSING_RECONCILIATION:
    "재처리 시 이미 반영된 레코드가 중복 처리되지 않도록 하는 정산 정합성(reconciliation) 설계.",
  MISSING_AUTOSCALING:
    "트래픽 급증에 맞춰 Pod 수를 자동으로 조절하는 오토스케일링(HPA) 설계.",
  MISSING_RESOURCE_LIMITS:
    "Pod의 리소스 request/limit 설정 — 메모리 초과로 인한 OOM kill·재시작 반복을 막는 데 필수적입니다.",
  MISSING_ROLLOUT_SAFETY:
    "readiness probe/PodDisruptionBudget 등으로 배포 도중 가용 용량이 줄어들지 않도록 하는 무중단 배포 안전장치.",
};

export function riskLabel(riskKey: string): string {
  return LABELS[riskKey] ?? riskKey;
}

export function riskDescription(riskKey: string): string | null {
  return DESCRIPTIONS[riskKey] ?? null;
}

export const RISK_KEYS: string[] = Object.keys(LABELS);
