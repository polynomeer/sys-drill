-- docs/LEARNING_DEEPENING_PLAN.md L15 (PLAN.md Round E39) — 랩 고도화 콘텐츠.
--
-- 1. 엔진 랩마다 목표 과제 2개(spec.goals). 목표는 지표 조건과 손잡이 조건(비용 — "풀 300 이하",
--    "Rate Limit 없이")을 함께 건다. 정답 설정은 저장하지 않는다 — LabGoalTest가 손잡이 격자를 훑어
--    엔진으로 달성 가능한지, 기본값에서는 아직 달성되지 않는지를 확인한다.
-- 2. 랩이 다루는 다른 개념(spec.concepts) — 개념 페이지가 이 랩도 보여 준다. 콘텐츠에서 엔진 수치로
--    설명하는 개념은 모두 같은 도메인의 랩을 가진다(LabGoalTest의 커버리지 검사). 엔진이 표현하지 못하는
--    개념(DLQ·비동기 경계·관측 가능성·Hot Key)은 랩을 두지 않는다(2차 기획 원칙 2).
-- 3. Capacity Lab 문제 3개 — 설계 가이드 §1의 도메인 규모(쿠폰 오픈, 대량 알림, 결제 피크). 셋 다
--    "평균 → 피크 → 동시에 필요한 자원(Little's law)" 순서로 어림한다.

update learning_labs set spec = spec || $j${
  "concepts": [],
  "goals": [
    {"id": "fast-without-giant-pool", "label": "풀 100 이하로 P95 100ms 이하",
     "hint": "풀을 키우지 않고도 DB가 받는 일 자체를 줄이는 방법이 있습니다.",
     "conditions": [{"metric": "p95LatencyMs", "op": "<=", "value": 100}, {"trait": "dbPoolSize", "op": "<=", "value": 100}]},
    {"id": "no-rate-limit", "label": "Rate Limit 없이, 풀 300 이하로 에러율 1% 미만",
     "hint": "쓰기만 고치면 읽기가 남습니다. 두 병목을 모두 0.6 아래로.",
     "conditions": [{"metric": "errorRate", "op": "<", "value": 0.01}, {"trait": "rateLimitEnabled", "op": "==", "value": false}, {"trait": "dbPoolSize", "op": "<=", "value": 300}]}
  ]
}$j$::jsonb where slug = 'engine-coupon-bottleneck';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_CIRCUIT_BREAKER"],
  "goals": [
    {"id": "drain-the-queue", "label": "컨슈머 16개 이하로 적체 0",
     "hint": "컨슈머를 늘리는 것만으로는 provider 지연을 이기지 못합니다.",
     "conditions": [{"metric": "queueLag", "op": "<=", "value": 0}, {"trait": "consumerCount", "op": "<=", "value": 16}]},
    {"id": "no-breaker", "label": "Circuit Breaker 없이 적체 500건 이하",
     "hint": "가능합니다 — 다만 달성한 뒤 P95가 어디에 있는지 보세요. 느린 provider를 끊지 않으면 지연은 줄지 않습니다.",
     "conditions": [{"metric": "queueLag", "op": "<=", "value": 500}, {"trait": "circuitBreakerEnabled", "op": "==", "value": false}]}
  ]
}$j$::jsonb where slug = 'engine-retry-storm';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_CACHE_POLICY_SEPARATION", "MISSING_READ_REPLICA"],
  "goals": [
    {"id": "back-to-normal", "label": "replica 2대 이하로 P95 100ms 이하",
     "hint": "중복 조회와 miss 수, 두 원인을 모두 줄여야 합니다.",
     "conditions": [{"metric": "p95LatencyMs", "op": "<=", "value": 100}, {"trait": "readReplicaCount", "op": "<=", "value": 2}]},
    {"id": "replicas-only", "label": "Single-flight 없이 DB 읽기 사용률 100% 미만",
     "hint": "가능은 합니다 — 몇 대가 필요한지 보세요.",
     "conditions": [{"metric": "dbReadLoad", "op": "<", "value": 1.0}, {"trait": "singleFlightEnabled", "op": "==", "value": false}]}
  ]
}$j$::jsonb where slug = 'engine-cache-stampede';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_PG_RETRY_BACKOFF", "MISSING_TRANSACTION_BOUNDARY"],
  "goals": [
    {"id": "orders-fast", "label": "주문 P95 100ms 이하",
     "hint": "워커를 늘리기 전에, PG에 같은 일을 몇 번씩 보내고 있는지 보세요.",
     "conditions": [{"metric": "p95LatencyMs", "op": "<=", "value": 100}]},
    {"id": "no-backlog", "label": "outbox 적체 0, 커넥션 풀 50% 이하",
     "hint": "적체를 비우는 것과 적체가 번지지 않게 하는 것은 다른 손잡이입니다.",
     "conditions": [{"metric": "queueLag", "op": "<=", "value": 0}, {"metric": "connectionPoolUsage", "op": "<=", "value": 0.5}]}
  ]
}$j$::jsonb where slug = 'engine-payment-idempotency';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_RESERVATION_LOCKING", "MISSING_INVENTORY_CONSISTENCY", "MISSING_CONCURRENCY_CONTROL"],
  "goals": [
    {"id": "under-one-percent", "label": "예약 에러율 1% 미만",
     "hint": "락 단위와 홀드 시간, 둘 중 하나만으로는 부족합니다.",
     "conditions": [{"metric": "errorRate", "op": "<", "value": 0.01}]},
    {"id": "keep-long-holds", "label": "홀드 180초 이상을 유지하면서 P95 100ms 이하",
     "hint": "결제할 시간을 넉넉히 주면 유령 홀드가 늘어납니다. 그 손실을 다른 곳에서 메우세요.",
     "conditions": [{"metric": "p95LatencyMs", "op": "<=", "value": 100}, {"trait": "holdTimeoutSeconds", "op": ">=", "value": 180}]}
  ]
}$j$::jsonb where slug = 'engine-reservation-hold';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_RESTARTABILITY", "MISSING_RECONCILIATION", "MISSING_IDEMPOTENCY", "MISSING_IDEMPOTENT_CONSUMER"],
  "goals": [
    {"id": "no-duplicates", "label": "중복 반영 0, 재처리 대기 5,000건 이하",
     "hint": "재처리 범위를 줄이는 손잡이와 중복을 막는 손잡이는 다릅니다.",
     "conditions": [{"metric": "errorRate", "op": "<", "value": 0.0001}, {"metric": "queueLag", "op": "<=", "value": 5000}]},
    {"id": "fast-and-small", "label": "처리량 15,000건/초 이상, 재처리 대기 20,000건 이하",
     "hint": "청크가 너무 작으면 커밋 오버헤드가 처리량을 깎습니다.",
     "conditions": [{"metric": "consumerThroughput", "op": ">=", "value": 15000}, {"metric": "queueLag", "op": "<=", "value": 20000}]}
  ]
}$j$::jsonb where slug = 'engine-batch-chunk';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_AUTOSCALING", "MISSING_ROLLOUT_SAFETY"],
  "goals": [
    {"id": "fast-with-fifty", "label": "Pod 50개 이하로 P95 100ms 이하",
     "hint": "늘린 Pod가 실제 용량이 되려면 먼저 살아 있어야 합니다.",
     "conditions": [{"metric": "p95LatencyMs", "op": "<=", "value": 100}, {"trait": "podReplicas", "op": "<=", "value": 50}]},
    {"id": "no-safeguard", "label": "롤아웃 보호 없이 에러율 1% 미만",
     "hint": "배포가 깎는 용량만큼 다른 쪽에서 여유를 만들어야 합니다.",
     "conditions": [{"metric": "errorRate", "op": "<", "value": 0.01}, {"trait": "rolloutSafeguardEnabled", "op": "==", "value": false}]}
  ]
}$j$::jsonb where slug = 'engine-autoscaling';

update learning_labs set spec = spec || $j${
  "concepts": ["MISSING_ROLLBACK_PLAN"],
  "goals": [
    {"id": "no-rollback", "label": "롤백 없이 에러율 1% 미만",
     "hint": "결함이 있는 버전이라도 받는 트래픽이 아주 작다면?",
     "conditions": [{"metric": "errorRate", "op": "<", "value": 0.01}, {"trait": "rolledBack", "op": "==", "value": false}]},
    {"id": "full-availability", "label": "가용성 99.8% 이상",
     "conditions": [{"metric": "availability", "op": ">=", "value": 0.998}]}
  ]
}$j$::jsonb where slug = 'engine-canary-rollout';

insert into learning_labs (slug, kind, title, summary, spec, display_order) values
('capacity-coupon-open', 'CAPACITY', '선착순 쿠폰 오픈',
 '100만 명이 오픈 직후 몇 초에 몰립니다. 피크 RPS, 실제로 DB에 닿는 쓰기, 그 쓰기에 필요한 커넥션 수를 어림합니다. 커넥션은 "동시에 진행 중인 쿼리 수 = 처리율 × 쿼리 시간"(Little''s law)입니다.',
 '{
   "inputs": [
     {"key": "participants", "label": "오픈 대기 인원", "value": 1000000, "unit": "명"},
     {"key": "burstShare", "label": "첫 집중 구간에 몰리는 비율", "value": 0.8, "unit": ""},
     {"key": "burstSeconds", "label": "집중 구간 길이", "value": 10, "unit": "초"},
     {"key": "requestsPerUser", "label": "사용자당 요청(새로고침 포함)", "value": 5, "unit": "회"},
     {"key": "couponStock", "label": "쿠폰 수량", "value": 10000, "unit": "장"},
     {"key": "queriesPerIssue", "label": "발급 1건의 쓰기 쿼리", "value": 2, "unit": "개"},
     {"key": "queryMs", "label": "쓰기 쿼리 시간", "value": 5, "unit": "ms"}
   ],
   "asks": [
     {"key": "peakRps", "label": "집중 구간 요청 RPS", "unit": "req/s", "formula": "participants * burstShare * requestsPerUser / burstSeconds"},
     {"key": "writeQps", "label": "발급 쓰기 QPS (재고만큼만 성공)", "unit": "query/s", "formula": "couponStock * queriesPerIssue / burstSeconds"},
     {"key": "connections", "label": "쓰기에 필요한 동시 커넥션", "unit": "개", "formula": "writeQps * queryMs / 1000"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "집중 구간이 2초로 짧아지면?", "overrides": {"burstSeconds": 2}}
   ]
 }', 4),
('capacity-notification-blast', 'CAPACITY', '대량 알림 발송',
 '2천만 명에게 30분 안에 마케팅 푸시를 보냅니다. 초당 발송량, 외부 provider에 동시에 걸려 있는 호출 수, 그걸 감당할 컨슈머 수를 어림합니다.',
 '{
   "inputs": [
     {"key": "recipients", "label": "수신자", "value": 20000000, "unit": "명"},
     {"key": "windowMinutes", "label": "발송 창", "value": 30, "unit": "분"},
     {"key": "providerMs", "label": "provider 호출 시간", "value": 200, "unit": "ms"},
     {"key": "callsPerConsumer", "label": "컨슈머 하나의 동시 호출", "value": 10, "unit": "개"}
   ],
   "asks": [
     {"key": "sendRps", "label": "초당 발송", "unit": "msg/s", "formula": "recipients / (windowMinutes * 60)"},
     {"key": "inFlight", "label": "동시에 진행 중인 provider 호출", "unit": "개", "formula": "sendRps * providerMs / 1000"},
     {"key": "consumers", "label": "필요한 컨슈머 수", "unit": "개", "formula": "inFlight / callsPerConsumer"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "provider가 1초로 느려지면?", "overrides": {"providerMs": 1000}}
   ]
 }', 5),
('capacity-payment-peak', 'CAPACITY', '결제 피크',
 '점심 할인 이벤트로 주문이 평소의 10배로 몰립니다. 평균·피크 결제 TPS와, 느린 PG 때문에 동시에 붙잡혀 있는 결제 수를 어림합니다.',
 '{
   "inputs": [
     {"key": "dau", "label": "DAU", "value": 2000000, "unit": "명"},
     {"key": "ordersPerUser", "label": "사용자당 하루 주문", "value": 0.3, "unit": "건"},
     {"key": "peakFactor", "label": "피크 / 평균", "value": 10, "unit": "배"},
     {"key": "pgSeconds", "label": "PG 승인 시간", "value": 1, "unit": "초"}
   ],
   "asks": [
     {"key": "avgTps", "label": "평균 결제 TPS", "unit": "tx/s", "formula": "dau * ordersPerUser / 86400"},
     {"key": "peakTps", "label": "피크 결제 TPS", "unit": "tx/s", "formula": "avgTps * peakFactor"},
     {"key": "inFlight", "label": "피크에 동시에 진행 중인 결제", "unit": "건", "formula": "peakTps * pgSeconds"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "PG가 3초로 느려지면?", "overrides": {"pgSeconds": 3}}
   ]
 }', 6);
