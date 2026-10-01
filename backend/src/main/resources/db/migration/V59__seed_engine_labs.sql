-- docs/LEARNING_EXPANSION_PLAN.md L5 (PLAN.md Round E11, ADR-0047) — 시뮬레이션 엔진 랩 7개.
--
-- 도메인마다 하나, 그 도메인 인시던트와 **같은 수식**(RuleBasedSimulationEngine)을 세션 없이
-- 부른다. spec은 어느 DesignTraits 필드를 어떤 범위의 손잡이(knob)로 줄지, 어떤 지표를 보여주고
-- 예측하게 할지만 정한다. 정답은 저장하지 않는다 — 결과는 엔진이 낸다.
--
-- 엔진이 실제로 표현하는 현상만 골랐다(계획 문서 L5 표, 2026-10-01 수식 확인):
-- coupon의 DB 쓰기 용량은 pool 크기에 정비례하므로 "pool은 클수록 좋은가"가 아니라
-- "내가 고친 게 실제 병목인가"를 묻는다.
insert into learning_labs (slug, kind, risk_key, domain, title, summary, spec, display_order) values
('engine-coupon-bottleneck', 'ENGINE', 'MISSING_RATE_LIMIT', 'coupon', '어느 병목인가 — 선착순 쿠폰',
 'Redis가 느려진 상태에서 TTL과 DB 커넥션 풀을 바꿔 봅니다. 풀을 늘리면 무엇이 좋아지고, 무엇은 그대로인가요?',
 '{
   "knobs": [
     {"trait": "cacheTtlSeconds", "label": "캐시 TTL (초)", "type": "number", "min": 1, "max": 600, "step": 1},
     {"trait": "dbPoolSize", "label": "DB 커넥션 풀", "type": "number", "min": 10, "max": 500, "step": 10},
     {"trait": "rateLimitEnabled", "label": "Rate Limit", "type": "boolean"}
   ],
   "watch": ["cacheHitRatio", "dbReadLoad", "dbWriteLoad", "p95LatencyMs", "errorRate"],
   "predict": ["dbReadLoad", "dbWriteLoad", "errorRate"]
 }', 11),
('engine-retry-storm', 'ENGINE', 'MISSING_RETRY_BACKOFF', 'notification', 'Retry Storm — 알림 이벤트',
 'provider가 느려지면 재시도가 몰립니다. 컨슈머 수, 백오프, circuit breaker 중 무엇이 무엇을 고치는지 하나씩 켜 보세요.',
 '{
   "knobs": [
     {"trait": "consumerCount", "label": "컨슈머 수", "type": "number", "min": 1, "max": 64, "step": 1},
     {"trait": "retryBackoffMultiplier", "label": "재시도 백오프 배수", "type": "number", "min": 1, "max": 20, "step": 1},
     {"trait": "circuitBreakerEnabled", "label": "Circuit Breaker", "type": "boolean"}
   ],
   "watch": ["consumerThroughput", "queueLag", "errorRate", "p95LatencyMs"],
   "predict": ["queueLag", "errorRate"]
 }', 12),
('engine-cache-stampede', 'ENGINE', 'MISSING_SINGLE_FLIGHT', 'product-browsing', 'Cache Stampede — 상품 조회',
 'hot key에 트래픽이 몰리면 miss 하나가 DB 읽기 여러 개가 됩니다. single-flight, 캐시 정책 분리, read replica를 비교해 보세요.',
 '{
   "knobs": [
     {"trait": "singleFlightEnabled", "label": "Single-flight", "type": "boolean"},
     {"trait": "cachePolicySplit", "label": "캐시 정책 분리", "type": "boolean"},
     {"trait": "readReplicaCount", "label": "Read Replica 수", "type": "number", "min": 0, "max": 10, "step": 1}
   ],
   "watch": ["cacheHitRatio", "dbReadLoad", "p95LatencyMs", "errorRate"],
   "predict": ["dbReadLoad", "p95LatencyMs"]
 }', 13),
('engine-payment-idempotency', 'ENGINE', 'MISSING_PAYMENT_IDEMPOTENCY', 'payment', '격리와 멱등 재시도 — 주문/결제',
 'PG가 느려지면 응답 유실 재시도가 부하를 키우고, 쌓인 outbox가 주문 처리까지 번집니다. 각 조치가 어느 쪽을 막는지 보세요.',
 '{
   "knobs": [
     {"trait": "idempotentPgRetryEnabled", "label": "멱등 재시도", "type": "boolean"},
     {"trait": "paymentPoolIsolated", "label": "결제 커넥션 풀 격리", "type": "boolean"},
     {"trait": "dispatcherWorkers", "label": "디스패처 워커", "type": "number", "min": 1, "max": 64, "step": 1}
   ],
   "watch": ["connectionPoolUsage", "queueLag", "errorRate", "p95LatencyMs"],
   "predict": ["connectionPoolUsage", "queueLag"]
 }', 14),
('engine-reservation-hold', 'ENGINE', 'MISSING_RESERVATION_TIMEOUT', 'reservation', '락 세분화와 유령 홀드 — 예약',
 '결제하지 않고 떠난 사용자의 홀드는 타임아웃까지 좌석을 붙잡습니다. 타임아웃, 락 단위, 원자적 재고 확인을 바꿔 보세요.',
 '{
   "knobs": [
     {"trait": "holdTimeoutSeconds", "label": "홀드 타임아웃 (초)", "type": "number", "min": 30, "max": 900, "step": 30},
     {"trait": "fineGrainedLockingEnabled", "label": "좌석 단위 락", "type": "boolean"},
     {"trait": "atomicInventoryCheckEnabled", "label": "원자적 재고 확인", "type": "boolean"}
   ],
   "watch": ["consumerThroughput", "queueLag", "errorRate", "p95LatencyMs"],
   "predict": ["queueLag", "errorRate"]
 }', 15),
('engine-batch-chunk', 'ENGINE', 'MISSING_CHUNKING', 'batch-settlement', '청크 크기 트레이드오프 — 배치/정산',
 '청크가 작으면 실패 시 버리는 양이 줄지만 커밋 오버헤드로 처리량이 떨어집니다. 작을수록 좋기만 한지 확인해 보세요.',
 '{
   "knobs": [
     {"trait": "chunkSize", "label": "청크 크기 (레코드)", "type": "number", "min": 100, "max": 100000, "step": 100},
     {"trait": "checkpointingEnabled", "label": "체크포인트 재개", "type": "boolean"},
     {"trait": "idempotentReconciliationEnabled", "label": "멱등 재처리", "type": "boolean"}
   ],
   "watch": ["consumerThroughput", "queueLag", "errorRate", "p95LatencyMs"],
   "predict": ["consumerThroughput", "errorRate"]
 }', 16),
('engine-autoscaling', 'ENGINE', 'MISSING_RESOURCE_LIMITS', 'autoscaling', 'Scale-out의 한계 — 오토스케일링',
 'Pod만 늘리면 해결될까요? OOM kill과 롤아웃 중 용량 손실은 Pod 수와 곱해집니다.',
 '{
   "knobs": [
     {"trait": "podReplicas", "label": "Pod 수", "type": "number", "min": 1, "max": 100, "step": 1},
     {"trait": "resourceLimitsTuned", "label": "리소스 제한 조정", "type": "boolean"},
     {"trait": "rolloutSafeguardEnabled", "label": "롤아웃 안전장치", "type": "boolean"}
   ],
   "watch": ["consumerThroughput", "queueLag", "errorRate", "p95LatencyMs"],
   "predict": ["errorRate", "consumerThroughput"]
 }', 17);
