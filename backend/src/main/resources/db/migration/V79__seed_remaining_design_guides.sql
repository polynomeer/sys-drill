-- docs/LEARNING_DEEPENING_PLAN.md L14 (PLAN.md Round E36) — 나머지 7개 도메인의 설계 가이드.
--
-- V78의 파일럿(대규모 상품 조회)과 같은 형식: 단계는 시작 설계(traits)와 바꾸는 값(action.change)만
-- 담고, 전/후 수치는 읽을 때 규칙 엔진이 계산한다. claims(단계 claims와 단계 안 numbers 블록의 claims)는
-- DesignGuideTest가 엔진과 대조하고, 단계 i+1의 traits = 단계 i의 traits + change 도 같은 테스트가
-- 확인한다(ADR-0054). 단계는 사용률이 지연/에러 구간 경계(0.6/0.8/0.95/1.0) 근처에 걸리지 않게 골랐다 —
-- 예: 쿠폰에서 rate limit 없이 TTL 300 + 풀 150이면 DB 사용률이 정확히 0.6, 결제에서 멱등성 없이 풀을
-- 격리하면 정확히 0.8이 되어 그 경로는 피했다.

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'coupon',
    '선착순 쿠폰',
    '선착순 쿠폰 오픈 순간 트래픽 20배와 Redis 지연이 동시에 온다. 캐시부터 손대고 싶어지지만 진짜 병목은 어디인지, 유입 제한과 풀 증설이 각각 무엇을 고치고 어디서부터 효과가 끝나는지를 단계별로 따라갑니다.',
    1,
    $g$[
  {
    "type": "text",
    "body": "선착순 쿠폰은 정해진 수량(예: 1만 장)을 오픈 시각에 먼저 온 사람에게 나눠 준다. 요청은 두 종류다.\n\n- **잔여 수량 조회(읽기)** — 요청의 대부분. 몇 초 늦은 숫자를 보여 줘도 괜찮다.\n- **발급 요청(쓰기)** — 재고를 차감하고 발급 기록을 남긴다. **수량을 넘겨 발급하거나 한 사람에게 두 장 주면 안 된다.**\n- 평소에는 초당 수백 건이지만, 오픈 순간 트래픽이 **20배**로 뛰고 같은 이벤트 키 하나에 몰린다. 하필 그때 Redis 응답도 느려진다."
  },
  {
    "type": "system",
    "domain": "coupon",
    "incident": false,
    "traits": {},
    "caption": "평시 — 캐시가 조회를 거의 다 받고, DB 쓰기도 한가하다."
  },
  {
    "type": "system",
    "domain": "coupon",
    "incident": true,
    "traits": {},
    "caption": "오픈 순간 — 같은 설계 그대로. 캐시 적중률이 떨어지고, DB 쓰기는 감당할 수 있는 양을 훌쩍 넘는다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "수량과 1인 1장은 포기하지 않는다. 대신 **늦게 온 사람이 바로 '다음에 다시' 응답을 받는 것**과 잔여 수량 숫자의 신선도는 포기할 수 있다 — 이 선택이 2단계의 설계 변경으로 이어진다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "조회는 캐시를 먼저 보고, 발급은 DB 트랜잭션으로 재고 차감과 발급 기록을 함께 남긴다. 발급은 커넥션 풀을 거친다.",
    "alt": "사용자가 쿠폰 API를 호출한다. 잔여 수량 조회는 Redis 캐시를 먼저 보고 miss일 때 DB를 읽는다. 발급 요청은 커넥션 풀을 거쳐 DB에서 재고 차감과 발급 기록을 한 트랜잭션으로 처리한다.",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> API[\"쿠폰 API\"]\n  API -- \"조회\" --> C[(\"Redis\\n잔여 수량\")]\n  API -. \"miss일 때\" .-> DB[(\"DB\")]\n  API -- \"발급\" --> P[\"커넥션 풀\"]\n  P -- \"재고 차감 + 발급 기록\" --> DB"
  },
  {
    "type": "steps",
    "title": "요청 흐름",
    "items": [
      {
        "title": "잔여 수량 조회",
        "body": "이벤트 키로 Redis를 본다. 있으면 바로 응답 — 요청의 약 70%가 이 경로다."
      },
      {
        "title": "miss면 DB 조회",
        "body": "캐시에 없거나 만료됐으면 DB에서 읽어 다시 캐시에 넣는다."
      },
      {
        "title": "발급은 DB 트랜잭션",
        "body": "나머지 약 30%는 발급 요청이다. 커넥션을 하나 얻어 재고를 차감하고 발급 기록을 넣는다 — 캐시가 대신해 줄 수 없는 쓰기다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "오픈 직후 — Redis가 느려지자 캐시부터 손본다",
    "situation": "쿠폰 오픈과 함께 트래픽이 20배로 뛰었고, Redis 응답이 평소의 15배로 느려졌다. 대시보드에서 가장 먼저 눈에 띈 건 캐시 적중률 하락이다.",
    "incident": true,
    "traits": {},
    "signal": "캐시 적중률이 40% 아래로 떨어지고 DB 읽기 사용률이 오른다. 그런데 DB 쓰기 사용률은 그보다 훨씬 높은 180% 근처이고, 커넥션 풀은 꽉 차 있다. P95와 에러율이 함께 치솟는다.",
    "diagnosis": "눈에 띄는 증상(캐시)과 실제 병목(DB 쓰기)이 다르다. 발급 요청은 전부 DB 쓰기이고 캐시가 막아 줄 수 없다 — 쓰기 사용률이 100%를 넘는 한, 읽기를 아무리 덜어 내도 사용자가 겪는 지표는 쓰기 쪽이 정한다. 그래도 가장 먼저 손이 가는 건 캐시 TTL이다.",
    "concepts": [
      "MISSING_OBSERVABILITY",
      "MISSING_CACHE_POLICY_SEPARATION"
    ],
    "action": {
      "kind": "TUNE",
      "label": "캐시 TTL 10초 → 300초",
      "change": {
        "cacheTtlSeconds": 300
      },
      "why": "잔여 수량을 더 오래 캐시해 느려진 Redis를 덜 거치고, miss로 DB까지 가는 읽기를 줄인다."
    },
    "metrics": [
      "cacheHitRatio",
      "dbReadLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "cacheHitRatio",
        "direction": "UP"
      },
      {
        "metric": "dbReadLoad",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "적중률은 오르고 DB 읽기는 거의 절반이 됐지만 P95와 에러율은 그대로다 — 병목인 쓰기는 전혀 건드리지 않았기 때문이다. 게다가 잔여 수량이 최대 5분 묵은 값으로 보인다. '이미 끝났는데 남았다고 보이는' 화면이 발급 시도를 더 부른다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "무엇이 사용자 지표를 정하는가.",
        "before": {
          "label": "보이는 증상",
          "alt": "캐시 적중률이 떨어져 읽기가 DB로 새는 것만 보인다",
          "mermaid": "flowchart LR\n  R[\"조회 70%\"] --> C[(\"Redis 느림\")] -. \"miss\" .-> D[(\"DB 읽기\")]"
        },
        "after": {
          "label": "실제 병목",
          "alt": "발급 요청 30%가 전부 DB 쓰기로 가고, 쓰기 용량을 넘어 커넥션 풀이 고갈된다",
          "mermaid": "flowchart LR\n  W[\"발급 30%\"] --> P[\"커넥션 풀\\n고갈\"] --> D[(\"DB 쓰기\\n용량 초과\")]"
        }
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "지표는 가장 나쁜 자원을 따라간다",
        "body": "읽기와 쓰기 중 **더 포화된 쪽**이 지연과 에러를 정한다. 대시보드에 읽기·쓰기 사용률과 커넥션 풀 대기를 나란히 두지 않으면, 눈에 띄는 쪽을 고치느라 시간을 쓴다."
      }
    ],
    "links": {
      "labs": [
        "engine-coupon-bottleneck"
      ],
      "challenges": [
        "cache"
      ],
      "failurePattern": "coupon"
    }
  },
  {
    "title": "들어오는 양을 제한한다 — Rate Limit",
    "situation": "캐시는 좋아졌지만 장애는 그대로다. DB가 감당할 수 있는 것보다 두 배 가까운 발급 요청이 그대로 쏟아지고 있다.",
    "incident": true,
    "traits": {
      "cacheTtlSeconds": 300
    },
    "signal": "DB 쓰기 사용률 약 180%, 커넥션 풀 100%. 지연·에러는 1단계 이후 변화가 없다.",
    "diagnosis": "유입을 아무도 제한하지 않아 DB가 버스트를 그대로 받는다. 어차피 쿠폰은 수량이 정해져 있다 — 수십만 명의 요청을 모두 DB까지 보낼 이유가 없고, 늦게 온 요청은 앞단에서 빨리 거절하는 편이 모두에게 낫다.",
    "concepts": [
      "MISSING_RATE_LIMIT",
      "MISSING_ASYNC_BOUNDARY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "앞단 Rate Limit 도입",
      "change": {
        "rateLimitEnabled": true
      },
      "why": "API 앞에서 초당 받아들이는 요청에 상한을 두고, 넘치는 요청은 DB에 닿기 전에 '잠시 후 다시'로 돌려보낸다."
    },
    "metrics": [
      "trafficRps",
      "dbWriteLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "trafficRps",
        "direction": "DOWN"
      },
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "받아들인 요청이 절반으로 줄어 DB 쓰기가 100% 아래로 내려왔고 지연·에러도 크게 내려갔다. 대가는 돌려보낸 절반의 사용자다 — 이들은 에러 대신 빠른 거절을 받지만 경험은 나쁘다. 그리고 쓰기 사용률이 여전히 90% 근처라 지연은 평시의 세 배다.",
    "blocks": [
      {
        "type": "diagram",
        "caption": "DB가 감당할 만큼만 들여보내고 나머지는 앞단에서 끝낸다.",
        "alt": "사용자 요청이 Rate Limiter를 지나며 상한 이내는 쿠폰 API로, 초과분은 즉시 '잠시 후 다시' 응답을 받는다",
        "mermaid": "flowchart LR\n  U[\"사용자 요청\"] --> RL[\"Rate Limiter\"]\n  RL -- \"상한 이내\" --> API[\"쿠폰 API\"] --> DB[(\"DB\")]\n  RL -. \"초과\" .-> X[\"429 잠시 후 다시\"]"
      },
      {
        "type": "callout",
        "tone": "tradeoff",
        "title": "거절이냐 대기열이냐",
        "body": "초과 요청을 바로 거절하는 대신 **대기열(번호표)**에 세울 수도 있다. 사용자 경험은 낫지만 대기열 자체의 저장·순서 보장·만료 처리가 새로 생긴다. 어느 쪽이든 핵심은 같다 — DB 앞에서 들어오는 양을 정한다."
      }
    ],
    "links": {
      "labs": [
        "engine-coupon-bottleneck"
      ],
      "challenges": [
        "rate-limiter",
        "queue"
      ]
    }
  },
  {
    "title": "남은 쓰기 압박을 덜어 준다 — 커넥션 풀 50 → 150",
    "situation": "유입은 통제됐다. 남은 문제는 받아들인 발급 요청만으로도 DB 쓰기가 90% 가까이 찬다는 것이다.",
    "incident": true,
    "traits": {
      "cacheTtlSeconds": 300,
      "rateLimitEnabled": true
    },
    "signal": "DB 쓰기 사용률과 커넥션 풀 사용률이 90% 근처에 머문다. 에러는 줄었지만 P95가 평시의 세 배다.",
    "diagnosis": "유입을 제한한 지금은 '받기로 한 양'을 처리할 쓰기 용량이 빠듯한 상태다 — 이제야 용량을 늘리는 게 의미가 있다. 유입에 상한이 있으니 '얼마나 늘리면 충분한지'를 계산할 수 있다.",
    "concepts": [
      "MISSING_RESOURCE_LIMITS"
    ],
    "action": {
      "kind": "TUNE",
      "label": "DB 커넥션 풀 50 → 150",
      "change": {
        "dbPoolSize": 150
      },
      "why": "동시에 처리할 수 있는 발급 트랜잭션 수를 늘려 받아들인 쓰기를 여유 있게 소화한다."
    },
    "metrics": [
      "dbWriteLoad",
      "connectionPoolUsage",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "connectionPoolUsage",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "쓰기 사용률이 안정 구간으로 내려오고 지연·에러가 평시 수준으로 돌아왔다. 순서가 중요했다 — 상한 없이 풀만 키우면 트래픽이 더 커지는 순간 같은 포화가 반복된다. 풀은 용량이고, 상한은 Rate Limit이 정한다. 다만 커넥션을 늘리는 건 DB의 동시 연결·메모리 한계 안에서만 유효하다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 순서를 바꿔 보기",
        "body": "Rate Limit을 끈 채 풀만 늘리면 P95가 어디까지 내려가는지 비교해 보자. 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-coupon-bottleneck"
      ],
      "challenges": [
        "rate-limiter"
      ]
    }
  },
  {
    "title": "더 여유 있게? — 풀을 250으로",
    "situation": "장애는 진정됐다. 다음 이벤트에 대비해 커넥션 풀을 250까지 늘려 두자는 제안이 나왔다.",
    "incident": true,
    "traits": {
      "cacheTtlSeconds": 300,
      "rateLimitEnabled": true,
      "dbPoolSize": 150
    },
    "signal": "DB 쓰기 사용률은 이미 여유 구간이고, 지연·에러는 정상이다. 이제 가장 높은 사용률은 쓰기가 아니라 DB 읽기 쪽이다.",
    "diagnosis": "병목이 더 이상 DB 쓰기에 있지 않다 — 여기서 쓰기 용량을 더해도 사용자가 겪는 지표는 바뀌지 않는다. 그리고 지금부터의 진짜 위험은 숫자가 아니라 정합성이다.",
    "concepts": [
      "MISSING_CONCURRENCY_CONTROL",
      "MISSING_IDEMPOTENCY",
      "MISSING_INVENTORY_CONSISTENCY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "DB 커넥션 풀 150 → 250",
      "change": {
        "dbPoolSize": 250
      },
      "why": "다음 이벤트의 트래픽 증가분에 대비한 여유."
    },
    "metrics": [
      "dbWriteLoad",
      "dbReadLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "dbReadLoad",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "쓰기 사용률은 더 내려가지만 읽기 사용률·지연·에러는 그대로다 — 가장 바쁜 자원이 이미 읽기로 넘어갔다. 실제 DB에서는 같은 재고 행을 노리는 트랜잭션이 늘수록 락 대기가 길어져, 풀을 키우는 것이 오히려 경합을 키울 수 있다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "이 시뮬레이션은 처리량만 본다. 실제로는 **재고 한 행에 동시 차감이 몰리면** 확인과 차감 사이에 끼어든 요청이 수량을 넘겨 발급할 수 있고(초과 발급), 응답을 못 받은 클라이언트의 **재시도는 같은 사람에게 두 장을 줄 수 있다**(중복 발급). 재고 차감을 Redis 원자 연산이나 큐로 옮기고 DB는 확정만 기록하는 설계, 사용자별 멱등 키와 유니크 제약이 다음에 다룰 일이다."
      },
      {
        "type": "compare",
        "caption": "재고 차감을 어디서 원자적으로 할 것인가.",
        "before": {
          "label": "지금",
          "alt": "여러 발급 요청이 DB의 재고 행 하나를 동시에 읽고 차감해 경합과 초과 발급 위험이 생긴다",
          "mermaid": "flowchart LR\n  A[\"발급 요청들\"] -- \"읽고 차감\" --> R[(\"DB 재고 행 1개\")]"
        },
        "after": {
          "label": "원자 차감 + 멱등 기록",
          "alt": "발급 요청이 Redis 원자 연산으로 재고를 차감하고, 성공한 건만 사용자별 멱등 키로 DB에 기록된다",
          "mermaid": "flowchart LR\n  A[\"발급 요청들\"] -- \"원자 차감\" --> K[(\"Redis 카운터\")]\n  K -- \"성공분만\" --> D[(\"DB 발급 기록\\n유저별 유니크\")]"
        }
      }
    ],
    "links": {
      "labs": [
        "engine-coupon-bottleneck"
      ],
      "challenges": [
        "distributed-lock",
        "idempotency"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "고수준 아키텍처와 요청 흐름",
    "where": "architecture"
  },
  {
    "item": "저장소 선택과 읽기/쓰기 패턴",
    "where": "step-1"
  },
  {
    "item": "동시성·멱등성 처리 (중복 발급 방지)",
    "where": "step-4"
  },
  {
    "item": "캐시/락 전략과 실패 시 대응",
    "where": "step-1"
  },
  {
    "item": "Rate limit 등 트래픽 보호 전략",
    "where": "step-2"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-4"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'notification',
    '알림 이벤트 처리',
    '주문량이 10배로 뛴 날 SMS provider까지 느려진다. 컨슈머 증설, 차단기, 백오프 중 무엇이 실제로 lag을 줄이는지, 그리고 어디서부터는 더 해도 소용없는지를 단계별로 따라갑니다.',
    2,
    $g$[
  {
    "type": "text",
    "body": "주문이 완료되면 **주문 확인·배송 알림**을 SMS·푸시로 보낸다. 알림은 주문의 부산물이지만, 다루는 방식에 따라 주문 자체를 막을 수도 있다.\n\n- **주문 처리** — 알림 때문에 느려지거나 실패하면 안 된다. 알림 전송은 주문 트랜잭션 밖에서 한다.\n- **알림 전송** — 몇 초 늦어도 괜찮다. 대신 **유실**되면 안 되고, 같은 알림이 **두 번** 가는 것도 피해야 한다.\n- 평소에는 초당 수십 건이지만, 프로모션 날에는 주문량이 **10배**로 뛰고 외부 SMS provider의 응답도 평소의 **15배**로 느려진다."
  },
  {
    "type": "system",
    "domain": "notification",
    "incident": false,
    "traits": {},
    "caption": "평시 — 컨슈머 4개가 provider 응답을 기다려도 여유가 있고, 큐는 비어 있다."
  },
  {
    "type": "system",
    "domain": "notification",
    "incident": true,
    "traits": {},
    "caption": "프로모션 + provider 지연 — 같은 설계 그대로. 컨슈머 처리량이 바닥나고, 실패한 메시지의 즉시 재시도가 유입을 부풀려 lag이 쌓인다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "주문 처리와 알림의 유실 방지는 포기하지 않는다. 대신 알림의 **즉시성**은 포기할 수 있다 — 몇 초 늦게 가도 된다는 이 여유가 3단계의 백오프를 가능하게 한다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "주문 서비스는 이벤트만 발행하고 끝난다. 알림 컨슈머가 큐에서 꺼내 provider를 호출하고, 실패한 메시지는 재시도 큐로, 끝내 실패하면 DLQ로 간다.",
    "alt": "주문 서비스가 주문 완료 이벤트를 큐에 발행한다. 알림 컨슈머가 큐에서 메시지를 꺼내 SMS·푸시 provider를 호출한다. 실패한 메시지는 재시도 큐를 거쳐 다시 큐로 돌아오고, 재시도를 다 쓰면 DLQ에 격리된다.",
    "mermaid": "flowchart LR\n  O[\"주문 서비스\"] -- \"1. 이벤트 발행\" --> Q[(\"알림 큐\")]\n  Q --> W[\"알림 컨슈머\"]\n  W -- \"2. 전송\" --> P[\"SMS·푸시 provider\"]\n  W -. \"3. 실패\" .-> R[(\"재시도 큐\")]\n  R -. \"다시 시도\" .-> Q\n  R -. \"재시도 소진\" .-> DLQ[(\"DLQ\")]"
  },
  {
    "type": "steps",
    "title": "메시지 흐름",
    "items": [
      {
        "title": "이벤트 발행",
        "body": "주문이 확정되면 주문 서비스는 '주문 완료' 이벤트를 큐에 넣고 바로 응답한다 — 알림이 느려도 주문은 기다리지 않는다(비동기 경계)."
      },
      {
        "title": "컨슈머가 전송",
        "body": "알림 컨슈머가 큐에서 메시지를 꺼내 provider를 호출한다. 컨슈머 하나는 provider 응답을 기다리는 동안 다음 메시지를 처리하지 못한다."
      },
      {
        "title": "실패하면 재시도, 끝내 실패하면 DLQ",
        "body": "전송이 실패한 메시지는 재시도 큐로 돌아가 다시 시도된다. 정해진 횟수를 넘기면 DLQ에 격리해 큐 전체를 막지 않게 한다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "프로모션 시작 — lag이 눈덩이처럼 쌓인다",
    "situation": "프로모션이 시작되자 주문량이 10배로 뛰었고, 같은 시각 SMS provider의 응답이 크게 느려졌다. 가장 먼저 떠오르는 조치는 컨슈머를 늘리는 것이다.",
    "incident": true,
    "traits": {},
    "signal": "컨슈머 그룹 lag이 계속 늘어난다. provider 호출이 타임아웃 근처에서 끝나고, 같은 메시지의 재시도 로그가 반복된다. 알림 지연과 실패율이 함께 치솟는다.",
    "diagnosis": "컨슈머 하나가 느린 provider를 기다리느라 처리량이 바닥났다. 그 사이 실패한 메시지가 곧바로 다시 큐로 들어와 유입량이 몇 배로 부풀었다 — 재시도 폭풍이다. 그런데 일단은 처리할 손이 부족해 보인다.",
    "concepts": [
      "MISSING_ASYNC_BOUNDARY",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "컨슈머 4 → 12개",
      "change": {
        "consumerCount": 12
      },
      "why": "처리할 손을 세 배로 늘려 쌓인 메시지를 소화한다."
    },
    "metrics": [
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "queueLag",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리량은 세 배가 됐지만 lag은 거의 그대로다 — 재시도로 부푼 유입이 여전히 처리량의 수십 배이기 때문이다. 늘린 컨슈머만큼 죽어 가는 provider에 동시 호출만 늘었다. 다만 이 증설이 헛수고로 끝나지는 않는다 — 원인을 잡은 뒤에야 효과를 낸다(3단계).",
    "blocks": [
      {
        "type": "compare",
        "caption": "컨슈머를 늘려도 lag이 줄지 않는 이유.",
        "before": {
          "label": "컨슈머 4개",
          "alt": "느린 provider를 기다리는 컨슈머 4개 앞에, 재시도로 부푼 메시지가 쌓인다",
          "mermaid": "flowchart LR\n  IN[\"주문 이벤트 + 즉시 재시도\"] --> Q[(\"큐\")] --> W[\"컨슈머 4개\"] -- \"느린 응답 대기\" --> P[\"provider\"]"
        },
        "after": {
          "label": "컨슈머 12개",
          "alt": "컨슈머가 12개가 되어도 각자 느린 provider를 기다리고, 유입은 여전히 처리량을 크게 넘어 큐가 줄지 않는다",
          "mermaid": "flowchart LR\n  IN[\"주문 이벤트 + 즉시 재시도\"] --> Q[(\"큐\")] --> W[\"컨슈머 12개\"] -- \"느린 응답 대기 × 3\" --> P[\"provider\"]"
        }
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "무엇을 보고 알아챌까",
        "body": "lag 하나만 보면 '처리할 손이 부족하다'로 읽힌다. **provider 응답 시간**과 **재시도 비율**을 함께 걸어 두어야 원인이 컨슈머 수가 아니라 느린 의존성과 재시도라는 것이 보인다."
      }
    ],
    "links": {
      "labs": [
        "engine-retry-storm"
      ],
      "challenges": [
        "queue"
      ],
      "failurePattern": "notification"
    }
  },
  {
    "title": "죽은 provider를 기다리지 않는다 — Circuit Breaker",
    "situation": "컨슈머를 세 배로 늘렸는데도 lag이 줄지 않는다. 컨슈머가 시간을 쓰는 곳을 보니 대부분 provider 응답을 기다리고 있다.",
    "incident": true,
    "traits": {
      "consumerCount": 12
    },
    "signal": "provider 호출 지연이 평소의 15배다. 컨슈머는 바쁘지만 실제로 끝내는 메시지는 적다.",
    "diagnosis": "응답이 느린 provider를 끝까지 기다리는 호출에 타임아웃도 차단기도 없다. 컨슈머가 몇 개든 그 시간 동안 묶여 있다.",
    "concepts": [
      "MISSING_CIRCUIT_BREAKER",
      "MISSING_DLQ"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "Circuit Breaker 도입",
      "change": {
        "circuitBreakerEnabled": true
      },
      "why": "실패가 이어지면 차단기를 열어 provider를 호출하지 않고 바로 실패 처리한다 — 컨슈머가 기다림에서 풀려난다."
    },
    "metrics": [
      "externalDependencyLatencyMs",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "externalDependencyLatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "컨슈머가 풀려나 lag은 크게 줄었지만, 재시도로 부푼 유입이 아직 처리량을 넘는다 — 지연·에러가 그대로인 이유다. 차단기가 열린 동안 보낸 알림은 '빠르게 실패'했을 뿐 전달되지 않았다. 그 메시지들은 어딘가에 남아 다시 보내져야 한다.",
    "blocks": [
      {
        "type": "diagram",
        "caption": "차단기의 세 상태. 열린 동안에는 provider를 부르지 않고 바로 실패한다.",
        "alt": "닫힘 상태에서 실패가 이어지면 열림으로 바뀌어 호출을 바로 실패시키고, 일정 시간 뒤 반열림에서 시험 호출이 성공하면 닫힘으로, 실패하면 다시 열림으로 간다",
        "mermaid": "flowchart LR\n  C[\"닫힘\\n정상 호출\"] -- \"실패 누적\" --> O[\"열림\\n즉시 실패\"]\n  O -- \"대기 후\" --> H[\"반열림\\n시험 호출\"]\n  H -- \"성공\" --> C\n  H -- \"실패\" --> O"
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "빠르게 실패한 메시지는 어디로?",
        "body": "차단기는 실패를 **빨리** 만들 뿐 없애지 않는다. 실패한 메시지는 재시도 큐로 보내고, 재시도를 다 써도 안 되는 메시지(poison message, 잘못된 번호 등)는 **DLQ**에 격리해 큐 전체를 막지 않게 한다. 이 시뮬레이션은 DLQ를 따로 계산하지 않는다 — 엔진 밖의 설계다."
      }
    ],
    "links": {
      "labs": [
        "engine-retry-storm"
      ],
      "challenges": [
        "circuit-breaker"
      ]
    }
  },
  {
    "title": "재시도 폭풍을 끈다 — 백오프",
    "situation": "차단기 덕분에 컨슈머는 풀려났지만 큐는 여전히 처리량보다 빨리 찬다. 들어오는 메시지의 상당수가 새 주문이 아니라 재시도다.",
    "incident": true,
    "traits": {
      "consumerCount": 12,
      "circuitBreakerEnabled": true
    },
    "signal": "lag이 줄었지만 아직 쌓이고 있다. 알림 지연과 실패율은 1단계 그대로다. 재시도 비율이 새 이벤트보다 높다.",
    "diagnosis": "실패한 메시지가 간격 없이 곧바로 재시도되어, 한 건의 주문이 큐에 여러 번 들어온다. 처리량을 아무리 올려도 유입 자체가 부풀어 있으면 따라잡지 못한다.",
    "concepts": [
      "MISSING_RETRY_BACKOFF"
    ],
    "action": {
      "kind": "TUNE",
      "label": "재시도 백오프 배수 1 → 10",
      "change": {
        "retryBackoffMultiplier": 10
      },
      "why": "재시도 간격을 지수적으로 벌려, 같은 시간에 다시 들어오는 메시지를 줄인다. 알림은 몇 초 늦어도 된다는 요구사항이 이 선택을 허락한다."
    },
    "metrics": [
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "유입이 처리량 아래로 내려오며 lag이 사라지고 지연·에러가 정상으로 돌아왔다. 1단계에서 늘린 컨슈머가 이제야 제 몫을 한다 — 컨슈머 4개였다면 차단기와 백오프를 다 써도 처리량이 모자랐다. 대가로 실패한 알림이 다시 가기까지의 시간이 길어진다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "같은 실패가 큐에 몇 번 다시 들어오는가.",
        "before": {
          "label": "즉시 재시도",
          "alt": "실패한 메시지가 간격 없이 곧바로 큐로 돌아와 새 이벤트와 함께 유입을 부풀린다",
          "mermaid": "flowchart LR\n  F[\"실패\"] -- \"즉시\" --> Q[(\"큐\")]\n  N[\"새 이벤트\"] --> Q"
        },
        "after": {
          "label": "지수 백오프",
          "alt": "실패한 메시지가 1초, 2초, 4초처럼 점점 긴 간격을 두고 돌아와 같은 시간의 유입이 줄어든다",
          "mermaid": "flowchart LR\n  F[\"실패\"] -- \"1s → 2s → 4s …\" --> Q[(\"큐\")]\n  N[\"새 이벤트\"] --> Q"
        }
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "컨슈머를 4개로 되돌리면 백오프를 최대로 올려도 회복될까? 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-retry-storm"
      ],
      "challenges": [
        "retry-backoff"
      ]
    }
  },
  {
    "title": "더 느긋하게? — 백오프를 두 배로",
    "situation": "장애는 진정됐다. 다음 프로모션에 대비해 백오프 배수를 20으로 올려 재시도를 더 줄이자는 제안이 나왔다.",
    "incident": true,
    "traits": {
      "consumerCount": 12,
      "circuitBreakerEnabled": true,
      "retryBackoffMultiplier": 10
    },
    "signal": "lag은 0이고, 알림 지연과 실패율은 정상이다.",
    "diagnosis": "유입은 이미 처리량 아래에 있다 — 재시도를 더 줄여도 병목이 없는 곳을 넓히는 것이라 사용자가 겪는 지표는 바뀌지 않는다.",
    "concepts": [
      "MISSING_RETRY_BACKOFF",
      "MISSING_IDEMPOTENT_CONSUMER"
    ],
    "action": {
      "kind": "TUNE",
      "label": "재시도 백오프 배수 10 → 20",
      "change": {
        "retryBackoffMultiplier": 20
      },
      "why": "다음 프로모션의 증가분에 대비해 재시도 유입을 더 줄인다."
    },
    "metrics": [
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "지표는 그대로다 — 대신 실패한 알림이 다시 가기까지 두 배 더 기다린다. 이 시뮬레이션의 알림 지연은 첫 시도만 보므로 그 대가가 숫자에 드러나지 않는다. 인증번호처럼 몇 분 늦으면 쓸모없는 알림이라면 백오프 상한을 알림 종류별로 따로 정해야 한다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "재시도가 있는 한 메시지는 **최소 한 번** 전달된다 — provider가 실제로는 보냈는데 응답만 늦어 실패로 처리되면, 재시도가 **같은 알림을 두 번** 보낸다. 메시지 ID로 이미 보낸 알림을 걸러내는 **Idempotent Consumer**가 필요하다. 또 이 시뮬레이션은 provider를 하나로 본다. 실제로는 SMS·푸시·이메일 provider마다 따로 느려지므로, 차단기도 **provider별로** 두어야 하나가 열려도 나머지 채널은 계속 나간다."
      }
    ],
    "links": {
      "labs": [
        "engine-retry-storm"
      ],
      "challenges": [
        "idempotency"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "비동기 처리 경계 (이벤트 발행 vs 알림 전송)",
    "where": "architecture"
  },
  {
    "item": "idempotent consumer — 재시도로 인한 중복 발송 방지",
    "where": "step-4"
  },
  {
    "item": "retry/backoff 전략",
    "where": "step-3"
  },
  {
    "item": "DLQ(dead letter queue) — poison message 격리",
    "where": "step-2"
  },
  {
    "item": "provider별 circuit breaker",
    "where": "step-2"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-4"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'payment',
    '주문/결제',
    '외부 PG가 느려지자 결제 적체가 커넥션 풀을 타고 주문 처리까지 번지는 장애. 처리량을 늘리는 값 조정과 재시도·격리 같은 설계 변경 중 무엇이 실제로 듣는지를 단계별로 따라갑니다.',
    4,
    $g$[
  {
    "type": "text",
    "body": "주문 서비스는 주문을 만들고 외부 **PG(결제대행사)**에 승인을 요청한다. 두 가지는 절대 깨지면 안 된다.\n\n- **이중 결제 금지** — 같은 주문이 두 번 승인되면 안 된다. 재시도가 있어도 마찬가지다.\n- **주문과 결제 상태의 일치** — 결제는 됐는데 주문이 없거나, 주문은 확정인데 결제가 없으면 안 된다.\n- 반면 **결제 확정이 몇 초 늦게 반영되는 것**은 허용할 수 있다. 평시 주문은 초당 30건, PG 응답은 50ms 안팎이지만, PG가 저하되면 응답이 **20배** 느려지고 일부는 응답 없이 끊긴다."
  },
  {
    "type": "system",
    "domain": "payment",
    "incident": false,
    "traits": {},
    "caption": "평시 — 디스패처가 outbox를 바로바로 비우고, 커넥션 풀은 한가하다."
  },
  {
    "type": "system",
    "domain": "payment",
    "incident": true,
    "traits": {},
    "caption": "PG 저하 — 같은 설계 그대로. outbox가 쌓이고, 그 적체가 공유 커넥션 풀을 넘쳐 결제와 무관한 주문 API까지 막힌다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "이중 결제 금지와 상태 일치는 포기하지 않는다. 대신 결제 확정의 즉시성은 포기할 수 있다 — 그래서 PG 호출을 주문 트랜잭션 밖(outbox 뒤)으로 빼는 설계가 출발점이 된다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "주문과 outbox 행을 한 트랜잭션으로 쓰고, PG 호출은 디스패처가 트랜잭션 밖에서 한다. 주문 처리와 디스패처는 같은 DB 커넥션 풀을 쓴다.",
    "alt": "사용자가 주문 API를 호출하면 API가 하나의 DB 트랜잭션으로 주문과 outbox 행을 기록한다. 디스패처가 outbox를 읽어 외부 PG에 승인을 요청하고, 결과로 결제 상태를 갱신한다. 주문 API와 디스패처는 같은 커넥션 풀을 공유한다.",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> API[\"주문 API\"]\n  API -- \"1. 주문 + outbox\\n한 트랜잭션\" --> DB[(\"DB\\n공유 커넥션 풀\")]\n  DSP[\"outbox 디스패처\"] -- \"2. 미처리 이벤트 조회\" --> DB\n  DSP -- \"3. 승인 요청\" --> PG[\"외부 PG\"]\n  DSP -- \"4. 결제 상태 갱신\" --> DB"
  },
  {
    "type": "steps",
    "title": "요청 흐름",
    "items": [
      {
        "title": "주문과 outbox를 함께 기록",
        "body": "주문 행과 '결제 요청' outbox 행을 같은 트랜잭션으로 쓴다 — 둘 중 하나만 남는 일이 없다. 외부 호출은 이 트랜잭션 안에 넣지 않는다."
      },
      {
        "title": "디스패처가 PG 호출",
        "body": "디스패처 워커가 outbox의 미처리 이벤트를 꺼내 PG에 승인을 요청한다. 워커 하나는 PG 응답을 기다리는 동안 다른 일을 못 한다."
      },
      {
        "title": "결과 반영",
        "body": "승인 결과로 결제·주문 상태를 갱신하고 outbox 이벤트를 처리 완료로 표시한다. 응답이 유실되면 같은 이벤트를 다시 시도한다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "PG 저하 — 적체가 주문 처리까지 번진다",
    "situation": "PG 응답이 50ms에서 1초로 느려졌고, 일부 승인 요청은 응답 없이 끊긴다.",
    "incident": true,
    "traits": {},
    "signal": "외부 의존성 지연이 1초로 뛰고, outbox 적체가 계속 늘어난다. 커넥션 풀 사용률이 100%를 크게 넘고, 결제와 무관한 주문 API까지 지연·에러가 치솟는다.",
    "diagnosis": "워커 4개가 각자 1초씩 PG를 기다리니 초당 4건밖에 못 보낸다. 쌓인 적체가 같은 풀을 쓰는 주문 처리의 커넥션까지 잡아먹고 있다. 가장 먼저 떠오르는 손은 처리량, 곧 디스패처 워커를 늘리는 것이다.",
    "concepts": [
      "MISSING_OBSERVABILITY",
      "MISSING_PG_RETRY_BACKOFF"
    ],
    "action": {
      "kind": "TUNE",
      "label": "디스패처 워커 4 → 16개",
      "change": {
        "dispatcherWorkers": 16
      },
      "why": "PG가 느린 만큼 동시에 더 많이 보내 outbox 적체를 줄인다."
    },
    "metrics": [
      "connectionPoolUsage",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "connectionPoolUsage",
        "direction": "DOWN"
      },
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리량은 네 배가 됐지만 적체는 조금 줄었을 뿐이고, 풀은 여전히 포화다 — 사용자가 겪는 지연·에러는 그대로다. 들어오는 일 자체가 평소보다 훨씬 많다는 뜻이다. 워커를 늘린 만큼 PG에 동시 호출이 늘어나는 것도 대가다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "엔진 밖의 위험 — 느린 PG를 더 세게 두드리기",
        "body": "이 시뮬레이션은 PG 지연을 고정값으로 본다. 실제로는 이미 저하된 PG에 동시 호출을 늘리면 **PG가 더 느려지거나 우리를 제한(throttling)**할 수 있다. 워커 증설은 적체를 줄이는 처방이지, 적체가 왜 생겼는지에 대한 답이 아니다."
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "이 상태에서 워커만 늘려 지연을 정상으로 돌릴 수 있을까? 최대 64개까지 올려 보자. 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-payment-idempotency"
      ],
      "challenges": [
        "retry-backoff"
      ],
      "failurePattern": "payment"
    }
  },
  {
    "title": "일을 부풀리는 원인을 없앤다 — 멱등성 키를 붙인 재시도",
    "situation": "워커를 늘려도 적체가 거의 줄지 않는다. 들어오는 결제 요청이 실제 주문보다 훨씬 많다.",
    "incident": true,
    "traits": {
      "dispatcherWorkers": 16
    },
    "signal": "주문은 평소처럼 초당 30건인데, outbox로 들어오는 결제 시도는 그 네 배다. 같은 주문 ID의 승인 요청이 반복해서 보인다.",
    "diagnosis": "PG 응답이 끊기면 결제가 됐는지 모른 채 처음부터 다시 시도한다 — 주문 하나가 시도 네 번이 된다. 멱등성 키가 없으니 앞선 시도의 결과를 재사용하지 못하고, 매번 새 결제로 처리하며 커넥션도 그만큼 오래 붙잡는다. 이 재시도가 **이중 결제**의 통로이기도 하다.",
    "concepts": [
      "MISSING_PAYMENT_IDEMPOTENCY",
      "MISSING_IDEMPOTENCY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "멱등성 키를 붙인 PG 재시도",
      "change": {
        "idempotentPgRetryEnabled": true
      },
      "why": "주문마다 고정된 멱등성 키로 재시도해, 이미 처리된 승인은 결과만 돌려받는다 — 같은 결제를 다시 하지 않는다."
    },
    "metrics": [
      "connectionPoolUsage",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "connectionPoolUsage",
        "direction": "DOWN"
      },
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "부풀려진 일이 사라지자 풀이 여유 구간으로 내려오고 지연·에러가 정상으로 돌아온다. 대가는 멱등성 키를 저장하고 조회하는 비용, 그리고 PG가 키를 지원하는지에 대한 의존이다. 다만 적체는 아직 0이 아니다 — 결제 확정이 그만큼 늦게 반영된다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "응답이 유실된 주문 하나가 PG 승인 몇 번이 되는가.",
        "before": {
          "label": "지금",
          "alt": "응답이 끊길 때마다 새 결제로 재시도해 같은 주문이 여러 번 승인될 수 있다",
          "mermaid": "flowchart LR\n  O[\"주문 42\"] -- \"시도 1 (응답 유실)\" --> PG[\"PG\"]\n  O -- \"시도 2 · 새 결제\" --> PG\n  O -- \"시도 3 · 새 결제\" --> PG"
        },
        "after": {
          "label": "멱등성 키",
          "alt": "같은 멱등성 키로 재시도하면 PG가 첫 승인 결과를 돌려주어 결제는 한 번만 일어난다",
          "mermaid": "flowchart LR\n  O[\"주문 42\\nkey=ord-42\"] -- \"시도 1 (응답 유실)\" --> PG[\"PG\"]\n  O -- \"재시도 · 같은 key\" --> PG\n  PG -- \"첫 승인 결과 반환\" --> O"
        }
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "재시도 간격도 함께",
        "body": "멱등성은 재시도를 **안전하게** 만들 뿐, **덜 하게** 만들지는 않는다. 저하된 PG에 즉시 재시도를 반복하지 않도록 지수 백오프와 지터, 최대 횟수를 함께 둔다."
      }
    ],
    "links": {
      "labs": [
        "engine-payment-idempotency"
      ],
      "challenges": [
        "idempotency",
        "retry-backoff"
      ]
    }
  },
  {
    "title": "번지지 않게 막는다 — 결제 커넥션 풀 격리",
    "situation": "지표는 정상으로 돌아왔다. 하지만 이번 장애의 핵심은 결제 쪽 문제가 주문 API 전체를 멈췄다는 것이었다.",
    "incident": true,
    "traits": {
      "dispatcherWorkers": 16,
      "idempotentPgRetryEnabled": true
    },
    "signal": "지연·에러는 정상이지만, 커넥션 풀 사용량의 상당 부분을 여전히 outbox 적체가 차지하고 있다.",
    "diagnosis": "결제와 주문이 커넥션 풀 하나를 나눠 쓴다. 지금은 적체가 작아 괜찮지만, PG가 더 나빠지거나 재시도가 다시 늘면 적체가 그대로 주문 처리의 커넥션을 잡아먹는다 — 격벽(bulkhead)이 없다.",
    "concepts": [
      "MISSING_TRANSACTION_BOUNDARY",
      "MISSING_CIRCUIT_BREAKER"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "결제·outbox 커넥션 풀 분리 (bulkhead)",
      "change": {
        "paymentPoolIsolated": true
      },
      "why": "결제 쪽이 아무리 밀려도 주문 처리용 커넥션은 건드리지 못하게 풀을 나눈다."
    },
    "metrics": [
      "connectionPoolUsage",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "connectionPoolUsage",
        "direction": "DOWN"
      },
      {
        "metric": "queueLag",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "주문 풀 사용률은 평시 수준으로 내려갔지만 사용자가 겪는 지연·에러는 그대로다 — 이미 정상이었기 때문이다. 이 변경의 가치는 지금의 숫자가 아니라 **다음 장애의 폭발 반경**에 있다. 적체 자체는 줄지 않았고, 풀이 둘로 나뉘어 각각의 크기를 따로 정하고 관리해야 한다.",
    "blocks": [
      {
        "type": "numbers",
        "title": "적체가 더 컸다면 — 워커 4개일 때의 격리",
        "domain": "payment",
        "incident": true,
        "base": {
          "idempotentPgRetryEnabled": true
        },
        "change": {
          "paymentPoolIsolated": true
        },
        "changeLabel": "결제 풀 격리 (워커 4개 기준)",
        "metrics": [
          "connectionPoolUsage",
          "p95LatencyMs",
          "errorRate"
        ],
        "claims": [
          {
            "metric": "connectionPoolUsage",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          },
          {
            "metric": "errorRate",
            "direction": "DOWN"
          }
        ]
      },
      {
        "type": "callout",
        "tone": "tradeoff",
        "title": "격리는 증상을 가두지, 원인을 고치지 않는다",
        "body": "격벽은 결제 쪽 장애가 주문으로 번지는 것을 막을 뿐, 결제 적체를 줄여 주지는 않는다. PG가 완전히 멈추는 경우까지 대비하려면 **circuit breaker**로 PG 호출을 잠시 끊고 적체를 나중에 흘려보내는 경로가 필요하다 — 이 시뮬레이션이 다루지 않는 영역이다."
      }
    ],
    "links": {
      "labs": [
        "engine-payment-idempotency"
      ],
      "challenges": [
        "circuit-breaker"
      ]
    }
  },
  {
    "title": "남은 적체를 비운다 — 디스패처 워커 16 → 32개",
    "situation": "주문은 보호됐고 지표도 정상이다. 남은 것은 outbox에 쌓여 있는 결제 요청이다.",
    "incident": true,
    "traits": {
      "dispatcherWorkers": 16,
      "idempotentPgRetryEnabled": true,
      "paymentPoolIsolated": true
    },
    "signal": "outbox 적체가 줄지 않고 머물러 있다 — 결제 확정이 주문보다 계속 늦게 반영된다.",
    "diagnosis": "PG가 1초씩 걸리는 동안 워커 16개로는 초당 16건밖에 못 보내는데, 주문은 초당 30건씩 들어온다. 이제는 일이 부풀려진 게 아니라 순수하게 처리량이 모자라다 — 1단계에서 듣지 않던 워커 증설이 이제야 맞는 처방이 된다.",
    "concepts": [
      "MISSING_ASYNC_BOUNDARY",
      "MISSING_RECONCILIATION"
    ],
    "action": {
      "kind": "TUNE",
      "label": "디스패처 워커 16 → 32개",
      "change": {
        "dispatcherWorkers": 32
      },
      "why": "PG 지연을 감안한 처리량이 들어오는 주문 속도를 넘도록 워커를 늘린다."
    },
    "metrics": [
      "queueLag",
      "consumerThroughput",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      }
    ],
    "tradeoff": "적체가 0이 되어 결제 확정이 다시 제때 반영된다. 사용자 지연은 이미 정상이라 그대로다. 같은 처방이 1단계에서는 효과가 없고 지금은 듣는 이유는 순서다 — 일을 부풀리는 원인(멱등성 없는 재시도)을 먼저 없애야 처리량 증설이 의미를 갖는다. 워커가 늘어난 만큼 PG 동시 호출도 늘어난다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "적체가 비었다고 상태가 맞는 것은 아니다. 장애 동안 응답이 유실된 승인 중에는 **PG에서는 승인됐는데 우리 쪽은 실패로 남은 건**이 있을 수 있다. PG 거래 내역과 주문·결제 상태를 주기적으로 맞춰 보는 **대사(reconciliation)**가 마지막 안전망이다. 그리고 워커를 더 늘려도 적체가 이미 0인 이상 비용만 늘어난다."
      }
    ],
    "links": {
      "labs": [
        "engine-payment-idempotency"
      ],
      "challenges": [
        "outbox"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "트랜잭션 경계 — DB 트랜잭션과 외부 PG 호출을 어떻게 분리했는지 (outbox/saga)",
    "where": "architecture"
  },
  {
    "item": "결제 멱등성 — 재시도 시 이중 결제 방지",
    "where": "step-2"
  },
  {
    "item": "PG 장애 시 retry/backoff 전략과 partial failure 대응",
    "where": "step-2"
  },
  {
    "item": "결제 상태와 주문 상태의 일관성 보장 방법",
    "where": "step-4"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-3"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'reservation',
    '예약 시스템',
    '인기 공연 오픈에 예약 요청이 15배로 몰린다. 락의 범위, 홀드 타임아웃, 재고 확인의 원자성 — 세 가지 중 무엇이 실제로 듣고, 어디서부터는 숫자가 아니라 정합성의 문제인지를 단계별로 따라갑니다.',
    5,
    $g$[
  {
    "type": "text",
    "body": "좌석 예약은 **선택 → 홀드 → 결제 → 확정**으로 흐른다. 좌석을 고르면 결제하는 동안 잠시 다른 사람이 못 가져가게 잡아 두고(홀드), 결제가 끝나면 확정한다.\n\n- **중복 예약(overbooking)** — 한 좌석이 두 사람에게 팔리면 안 된다. 절대 포기하지 않는다.\n- **팔 수 있는 좌석을 못 파는 것** — 결제하지 않고 떠난 사람의 홀드가 오래 남으면 빈 좌석이 '매진'처럼 보인다. 매출과 사용자 경험의 손실이다.\n- **응답 속도** — 몇 백 ms 늦는 건 견딜 수 있지만, 요청이 실패로 끝나면 사용자는 다시 누르고, 그 재시도가 부하를 더한다.\n- 평소에는 초당 수십 건이지만, 인기 공연이 열리는 순간 요청이 **15배**로 뛰고 같은 공연의 좌석에 몰린다."
  },
  {
    "type": "system",
    "domain": "reservation",
    "incident": false,
    "traits": {},
    "caption": "평시 — 공연 전체를 하나의 락으로 묶어도 요청이 적어 줄이 생기지 않는다."
  },
  {
    "type": "system",
    "domain": "reservation",
    "incident": true,
    "traits": {},
    "caption": "예매 오픈 — 같은 설계 그대로. 요청은 락 하나 앞에 줄을 서고, 떠난 사용자의 홀드가 처리 용량을 깎고, 경쟁에서 진 요청의 재시도가 부하를 몇 배로 키운다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "중복 예약 방지는 포기하지 않는다. 대신 결제하지 않는 사용자에게 좌석을 오래 쥐여 주는 관대함은 포기할 수 있다 — 이 차이가 2~3단계의 홀드 타임아웃 조정으로 이어진다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "예약 API가 좌석에 락을 잡고 재고를 확인한 뒤 홀드를 만든다. 결제가 끝나면 확정하고, 시간 안에 결제가 없으면 만료 작업이 홀드를 푼다.",
    "alt": "사용자가 예약 API를 호출하면 API가 락을 잡고 DB의 좌석 재고를 확인해 홀드를 기록한다. 결제가 완료되면 예약을 확정하고, 만료 작업이 오래된 홀드를 해제한다.",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> API[\"예약 API\"]\n  API -- \"1. 락\" --> L[\"락\"]\n  API -- \"2. 재고 확인 · 홀드\" --> DB[(\"좌석 DB\")]\n  PAY[\"결제\"] -- \"3. 완료 시 확정\" --> DB\n  EXP[\"만료 작업\"] -. \"시간 초과 홀드 해제\" .-> DB"
  },
  {
    "type": "steps",
    "title": "예약 흐름",
    "items": [
      {
        "title": "락 획득",
        "body": "고른 좌석에 대한 락을 잡는다. 락의 범위(공연 전체인지, 좌석 하나인지)가 동시에 처리할 수 있는 요청 수를 정한다."
      },
      {
        "title": "재고 확인과 홀드",
        "body": "좌석이 비어 있는지 확인하고 홀드를 기록한다. 확인과 기록 사이에 다른 요청이 끼어들면 안 된다."
      },
      {
        "title": "결제 후 확정, 아니면 만료",
        "body": "결제가 완료되면 홀드를 확정 예약으로 바꾼다. 홀드 타임아웃 안에 결제가 없으면 만료 작업이 홀드를 풀어 다시 팔 수 있게 한다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "예매 오픈 — 락 하나 앞에 모두가 줄을 선다",
    "situation": "인기 공연 예매가 열리자 예약 요청이 평소의 15배로 몰렸다. 대부분이 같은 공연의 좌석을 노린다.",
    "incident": true,
    "traits": {},
    "signal": "DB 쓰기 부하(락 처리 사용률)가 감당량의 수십 배로 치솟고, 예약 요청이 lock wait timeout으로 실패한다. 서로 다른 좌석을 고른 사용자들도 똑같이 기다린다.",
    "diagnosis": "공연 전체를 하나의 락으로 묶고 있다. C-12를 고른 요청과 A-3을 고른 요청은 서로 충돌하지 않는데도 같은 줄에 선다 — 동시에 처리할 수 있는 요청이 사실상 하나뿐이다.",
    "concepts": [
      "MISSING_RESERVATION_LOCKING",
      "MISSING_CONCURRENCY_CONTROL",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "락을 좌석 단위로 세분화",
      "change": {
        "fineGrainedLockingEnabled": true
      },
      "why": "서로 다른 좌석에 대한 요청이 병렬로 처리되게 해, 같은 좌석을 두고 다툴 때만 기다리게 한다."
    },
    "metrics": [
      "dbWriteLoad",
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리 용량은 크게 늘었지만 여전히 들어오는 요청을 감당하지 못한다 — 지연과 에러가 그대로인 이유다. 늘어난 용량의 대부분을 결제하지 않고 떠난 사용자의 홀드가 붙잡고 있다. 좌석 단위 락은 락 개수와 관리 비용을 늘리고, 여러 좌석을 한 번에 예약할 때는 락 순서를 정해 교착을 막아야 한다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "서로 다른 좌석을 고른 두 요청이 서로를 기다리는가.",
        "before": {
          "label": "지금 — 공연 단위 락",
          "alt": "좌석 C-12 요청과 좌석 A-3 요청이 공연 전체 락 하나를 두고 줄을 선다",
          "mermaid": "flowchart LR\n  A[\"C-12 예약\"] --> L[\"공연 전체 락\"]\n  B[\"A-3 예약\"] --> L"
        },
        "after": {
          "label": "좌석 단위 락",
          "alt": "좌석 C-12 요청과 좌석 A-3 요청이 각자 다른 좌석 락을 잡아 동시에 처리된다",
          "mermaid": "flowchart LR\n  A[\"C-12 예약\"] --> L1[\"C-12 락\"]\n  B[\"A-3 예약\"] --> L2[\"A-3 락\"]"
        }
      }
    ],
    "links": {
      "labs": [
        "engine-reservation-hold"
      ],
      "challenges": [
        "distributed-lock"
      ],
      "failurePattern": "reservation"
    }
  },
  {
    "title": "홀드를 조금 줄여 본다 — 5분에서 3분으로",
    "situation": "락은 세분화했지만 처리 용량의 대부분을 떠난 사용자의 홀드가 붙잡고 있다. 홀드 타임아웃을 조금 줄이자는 의견이 나왔다.",
    "incident": true,
    "traits": {
      "fineGrainedLockingEnabled": true
    },
    "signal": "홀드 만료 건수가 쌓이고, 빈 좌석이 있는데도 '매진'에 가까운 실패가 난다. 처리 용량은 1단계 이후 그대로다.",
    "diagnosis": "결제하지 않고 떠난 사용자의 '유령 홀드'가 타임아웃이 지날 때까지 좌석을 붙잡는다. 홀드가 몇 분씩 남는 한, 몰린 요청 속에서 쌓인 유령 홀드는 이미 용량의 거의 전부를 차지하고 있다.",
    "concepts": [
      "MISSING_RESERVATION_TIMEOUT"
    ],
    "action": {
      "kind": "TUNE",
      "label": "홀드 타임아웃 300 → 180초",
      "change": {
        "holdTimeoutSeconds": 180
      },
      "why": "유령 홀드가 더 빨리 풀리게 해서 다시 팔 수 있는 좌석을 늘린다."
    },
    "metrics": [
      "dbWriteLoad",
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "SAME"
      },
      {
        "metric": "consumerThroughput",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "아무것도 바뀌지 않았다. 3분도 유령 홀드가 용량을 가득 채우기엔 충분히 길다 — 포화된 구간에서 조금 줄이는 건 효과가 없다. 값 조정이 들으려면 홀드가 실제 결제 시간에 가까워질 만큼 줄어야 한다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "홀드 타임아웃을 몇 초까지 줄여야 처리 용량이 늘기 시작할까? 아래 랩 링크는 지금 단계의 설정으로 열린다 — 타임아웃 슬라이더를 천천히 내려 보자."
      }
    ],
    "links": {
      "labs": [
        "engine-reservation-hold"
      ]
    }
  },
  {
    "title": "결제 시간에 맞춘다 — 홀드 2분",
    "situation": "조금 줄여서는 소용이 없었다. 결제 단계의 실제 소요 시간을 보니 대부분의 사용자가 2분 안에 결제를 끝낸다.",
    "incident": true,
    "traits": {
      "fineGrainedLockingEnabled": true,
      "holdTimeoutSeconds": 180
    },
    "signal": "결제 완료까지 걸린 시간 분포를 보면 2분을 넘기는 결제는 드물다. 그런데 홀드는 그보다 훨씬 오래 남아 있다.",
    "diagnosis": "홀드 타임아웃이 실제 결제 시간보다 훨씬 길다. 정상 사용자에게는 필요 없는 여유이고, 떠난 사용자에게는 좌석을 묶어 두는 시간이다.",
    "concepts": [
      "MISSING_RESERVATION_TIMEOUT"
    ],
    "action": {
      "kind": "TUNE",
      "label": "홀드 타임아웃 180 → 120초",
      "change": {
        "holdTimeoutSeconds": 120
      },
      "why": "홀드를 실제 결제 시간에 맞춰, 떠난 사용자의 좌석이 빨리 다시 풀리게 한다."
    },
    "metrics": [
      "dbWriteLoad",
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "용량이 되살아나 지연·에러가 정상으로 돌아왔다. 대가는 결제가 느린 사용자다 — 2분을 넘기면 결제 도중 홀드가 풀려 다른 사람에게 팔릴 수 있다. 만료 직전 연장, 결제 진입 시 홀드 갱신 같은 규칙과, 시간이 지난 홀드를 실제로 정리하는 만료 작업이 함께 있어야 한다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "그럼 30초로 줄이면 더 좋지 않나?",
        "body": "용량 숫자는 더 오르지만, 정상 사용자의 결제 도중 홀드가 풀리기 시작한다 — 결제를 마쳤는데 좌석이 이미 다른 사람에게 가 있는 상황이다. 타임아웃은 '짧을수록 좋은 값'이 아니라 **실제 결제 시간에서 정하는 값**이다."
      }
    ],
    "links": {
      "labs": [
        "engine-reservation-hold"
      ]
    }
  },
  {
    "title": "숫자로는 안 보이는 구멍 — 재고 확인과 확정을 원자적으로",
    "situation": "지연과 에러는 정상이다. 그런데 로그를 보면 '확인할 땐 비어 있었는데 확정할 때 실패'한 요청과 그 재시도가 여전히 많다.",
    "incident": true,
    "traits": {
      "fineGrainedLockingEnabled": true,
      "holdTimeoutSeconds": 120
    },
    "signal": "같은 좌석에 대한 확정 실패와 즉시 재시도가 반복된다. DB 쓰기 부하의 상당 부분이 이 재시도다.",
    "diagnosis": "'남은 좌석이 있나?'를 읽고 나서 '홀드를 쓴다'를 따로 한다. 그 사이에 다른 요청이 같은 좌석을 가져가면 진 쪽은 실패하고 다시 시도한다 — 재시도가 부하를 키우고, 확인과 기록이 정말로 분리돼 있다면 두 사람이 모두 성공하는 중복 예약도 가능하다.",
    "concepts": [
      "MISSING_INVENTORY_CONSISTENCY",
      "MISSING_CONCURRENCY_CONTROL"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "재고 확인과 확정을 원자적으로",
      "change": {
        "atomicInventoryCheckEnabled": true
      },
      "why": "조건부 갱신('비어 있을 때만 홀드로 바꾼다') 한 번으로 확인과 기록을 합쳐, 경쟁에서 진 요청이 재시도 없이 바로 '이미 예약됨'을 받게 한다."
    },
    "metrics": [
      "dbWriteLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "dbWriteLoad",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "DB 쓰기 부하는 크게 줄었지만 사용자가 겪는 지연·에러는 이미 정상이라 그대로다. 그래도 이 변경은 '여유가 생겼으니 생략'할 수 있는 게 아니다 — 이 단계의 진짜 목적은 부하가 아니라 요구사항에서 절대 포기하지 않기로 한 중복 예약 방지다. 대가는 조건부 갱신이 DB 한 곳에 묶인다는 것, 그리고 여러 좌석을 한 번에 잡는 예약은 트랜잭션 경계를 따로 설계해야 한다는 것이다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "확인과 기록 사이에 다른 요청이 끼어들 틈이 있는가.",
        "before": {
          "label": "지금 — 확인 따로, 기록 따로",
          "alt": "요청이 좌석이 비었는지 읽은 뒤 따로 홀드를 쓰고, 그 사이 다른 요청이 같은 좌석을 가져가면 실패 후 재시도한다",
          "mermaid": "flowchart LR\n  R[\"재고 읽기\"] --> G[\"틈\"] --> W[\"홀드 쓰기\"]\n  W -. \"졌으면 재시도\" .-> R"
        },
        "after": {
          "label": "원자적 확인·확정",
          "alt": "비어 있을 때만 홀드로 바꾸는 조건부 갱신 한 번으로 성공 또는 이미 예약됨이 바로 결정된다",
          "mermaid": "flowchart LR\n  C[\"조건부 갱신\\n비어 있을 때만 홀드\"] --> OK[\"성공\"]\n  C --> NO[\"이미 예약됨\"]"
        }
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "중복 예약은 엔진 밖의 위험이다",
        "body": "이 시뮬레이션은 비원자적 재고 확인의 **재시도 낭비**만 부하로 계산하고, 실제로 한 좌석이 두 번 팔리는 사고는 숫자로 나타내지 않는다. 그래서 지표가 정상이어도 이 구멍은 남아 있을 수 있다. 확정 뒤 재고 정합성을 주기적으로 점검(대사)하고, '확정 건수 > 좌석 수' 같은 불변식 위반에 알림을 거는 것이 다음에 할 일이다."
      }
    ],
    "links": {
      "labs": [
        "engine-reservation-hold"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "동시 예약 요청에 대한 락 전략 (세분화 수준 포함)",
    "where": "step-1"
  },
  {
    "item": "재고 정합성 — 예약 가능 수량 확인과 확정의 원자성",
    "where": "step-4"
  },
  {
    "item": "예약 홀드(hold) 타임아웃과 자동 해제",
    "where": "step-3"
  },
  {
    "item": "중복 예약(overbooking) 방지 방법",
    "where": "step-4"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-2"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'batch-settlement',
    '배치/정산',
    '100만 건 정산 배치가 60% 지점에서 외부 정산 API 저하로 실패한다. 청크 크기만 줄이는 값 조정이 왜 듣지 않는지, 체크포인트와 멱등한 재처리가 무엇을 바꾸는지, 그다음 청크 크기를 어디까지 키울지를 단계별로 따라갑니다.',
    6,
    $g$[
  {
    "type": "text",
    "body": "매일 밤 정산 배치는 하루치 거래 **100만 건**을 읽어 가맹점별 정산액을 계산하고, 외부 정산 API를 거쳐 정산 테이블에 반영한다.\n\n- **정확성** — 한 건이라도 두 번 반영되면 가맹점에 돈이 두 번 나간다. 중복 정산은 절대 허용하지 않는다.\n- **마감 시간** — 아침 지급 전에 끝나야 한다. 실패해도 마감 안에 복구할 수 있어야 한다.\n- **응답 속도** — 사용자가 기다리는 API가 아니다. 청크 하나가 조금 느려지는 것은 감수할 수 있다."
  },
  {
    "type": "system",
    "domain": "batch-settlement",
    "incident": false,
    "traits": {},
    "caption": "평시 — 청크 단위로 차례차례 처리되고, 다시 처리할 레코드도 중복 정산도 없다."
  },
  {
    "type": "system",
    "domain": "batch-settlement",
    "incident": true,
    "traits": {},
    "caption": "진행 60% 지점에서 정산 API가 느려지며 배치가 실패 — 같은 설계 그대로. 처음부터 다시 돌면서 이미 반영된 레코드가 또 반영된다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "중복 정산 0건은 포기하지 않는다. 대신 청크 하나의 처리 시간은 포기할 수 있다 — 이 차이가 4단계에서 청크 크기를 다시 키우는 근거가 된다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "배치는 원천 거래를 청크 단위로 읽고(read) → 정산액을 계산하고(process) → 한 번에 반영한다(write). 청크가 커밋될 때마다 진행 위치를 Job 저장소에 남길 수 있다.",
    "alt": "스케줄러가 정산 배치를 시작한다. 배치는 거래 DB에서 청크 단위로 레코드를 읽고, 외부 정산 API를 호출해 정산액을 확정한 뒤 정산 테이블에 쓴다. 청크마다 진행 위치를 Job 저장소에 기록한다.",
    "mermaid": "flowchart LR\n  S[\"스케줄러\"] --> J[\"정산 배치 Job\"]\n  J -- \"1. 청크 읽기\" --> T[(\"거래 DB\")]\n  J -- \"2. 정산 확정\" --> API[\"외부 정산 API\"]\n  J -- \"3. 청크 반영\" --> R[(\"정산 테이블\")]\n  J -. \"4. 진행 위치\" .-> JR[(\"Job 저장소\")]"
  },
  {
    "type": "steps",
    "title": "청크 하나의 흐름",
    "items": [
      {
        "title": "청크 읽기",
        "body": "거래를 정해진 개수(청크 크기)만큼 읽는다. 기본 설정은 10,000건이다."
      },
      {
        "title": "정산 확정",
        "body": "레코드마다 정산액을 계산하고 외부 정산 API로 확정한다 — 청크 처리 시간의 대부분이 이 호출에서 나온다."
      },
      {
        "title": "반영과 커밋",
        "body": "청크 전체를 정산 테이블에 한 번에 쓰고 커밋한다. 커밋마다 고정 오버헤드가 붙는다. 처음 설계에는 '어디까지 했는지'를 남기는 단계가 없다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "배치가 60%에서 실패 — 청크부터 줄여 본다",
    "situation": "진행 60% 지점에서 외부 정산 API가 평소의 15배로 느려지고 청크가 실패했다. 배치는 처음부터 다시 돌고 있다. 첫 반응은 '청크가 커서 실패가 크다, 작게 쪼개자'다.",
    "incident": true,
    "traits": {},
    "signal": "재처리 대기 레코드가 60만 건이고, 그만큼이 중복 정산으로 잡힌다(에러율). 실제 처리량은 평시의 절반 아래다.",
    "diagnosis": "청크 크기는 '실패한 청크 하나'의 크기를 정할 뿐이다. 진행 위치를 기록하지 않으니 실패하면 지금까지 한 60만 건을 통째로 버리고 처음부터 다시 돈다 — 청크를 얼마나 잘게 쪼개든 버리는 양은 같다.",
    "concepts": [
      "MISSING_CHUNKING",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "청크 크기 10,000 → 1,000",
      "change": {
        "chunkSize": 1000
      },
      "why": "실패한 청크가 작으면 잃는 것도 작을 거라는 기대. 청크 하나의 처리 시간(=트랜잭션 길이)도 줄어든다."
    },
    "metrics": [
      "queueLag",
      "errorRate",
      "consumerThroughput",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      },
      {
        "metric": "consumerThroughput",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "재처리 대기와 중복 정산은 그대로다 — 재시작이 처음부터인 한 청크 크기는 상관없다. 얻은 것은 청크 하나가 짧아진 것뿐이고, 커밋이 열 배로 잦아져 고정 오버헤드 때문에 처리량은 오히려 떨어졌다. 마감 시간은 더 위험해졌다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "흔한 실수 — 청크 크기만 줄이기",
        "body": "작은 청크는 '실패한 청크 하나'를 작게 만들지, '다시 해야 하는 양'을 줄이지 않는다. 다시 해야 하는 양은 **어디서부터 다시 시작할 수 있는가**가 정한다 — 그게 다음 단계다."
      },
      {
        "type": "text",
        "body": "관측 포인트: 청크마다 `처리 건수 / 실패 건수 / 진행 위치`를 남기고, **재처리 대기 레코드 수**와 **중복 반영 건수**에 알림을 건다. 이 신호가 없으면 '배치가 오래 걸린다'는 사실만 알고 이유는 모른다."
      }
    ],
    "links": {
      "labs": [
        "engine-batch-chunk"
      ],
      "failurePattern": "batch-settlement"
    }
  },
  {
    "title": "실패한 청크부터 다시 — 체크포인트 재개",
    "situation": "청크를 줄여도 실패하면 60만 건을 다시 한다. 문제는 청크 크기가 아니라 재시작 지점이다.",
    "incident": true,
    "traits": {
      "chunkSize": 1000
    },
    "signal": "재처리 대기가 여전히 60만 건, 중복 정산 비율도 1단계 전과 같다. 처리량은 더 떨어졌다.",
    "diagnosis": "배치가 '어디까지 끝냈는지'를 모른다. 청크가 커밋될 때 진행 위치를 함께 남기면, 실패 뒤에는 그 위치부터 — 실패한 청크 하나만 다시 하면 된다.",
    "concepts": [
      "MISSING_RESTARTABILITY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "체크포인트 재개 도입",
      "change": {
        "checkpointingEnabled": true
      },
      "why": "청크 커밋과 같은 트랜잭션에 진행 위치를 기록해, 재시작이 처음이 아니라 마지막 성공 청크 다음에서 시작되게 한다."
    },
    "metrics": [
      "queueLag",
      "errorRate",
      "consumerThroughput",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      }
    ],
    "tradeoff": "다시 할 양이 실패한 청크 하나로 줄었고 처리량도 회복됐다. 하지만 중복 정산이 0이 된 것은 아니다 — 실패한 청크 안에서 이미 반영된 레코드는 다시 쓰이면 여전히 두 번 반영된다. 체크포인트를 쓰고 읽는 비용과, 진행 위치와 반영 결과가 반드시 함께 커밋돼야 한다는 제약도 생긴다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "실패 뒤 어디서부터 다시 하는가.",
        "before": {
          "label": "지금",
          "alt": "청크 1부터 600까지 처리하다 실패하면 진행 위치가 없어 청크 1부터 다시 시작한다",
          "mermaid": "flowchart LR\n  A[\"청크 1…600 완료\"] --> F[\"601에서 실패\"]\n  F -- \"처음부터\" --> A"
        },
        "after": {
          "label": "체크포인트",
          "alt": "청크마다 진행 위치를 기록하고, 실패하면 마지막으로 기록된 위치 다음 청크부터 다시 시작한다",
          "mermaid": "flowchart LR\n  A[\"청크 1…600 완료\\n위치=600 기록\"] --> F[\"601에서 실패\"]\n  F -- \"601부터\" --> N[\"청크 601…\"]"
        }
      }
    ],
    "links": {
      "labs": [
        "engine-batch-chunk"
      ]
    }
  },
  {
    "title": "남은 중복을 없앤다 — 멱등한 정산 재처리",
    "situation": "재처리 범위는 청크 하나로 줄었다. 그런데 그 청크 안에서 이미 반영된 레코드가 다시 쓰이며 여전히 중복 정산이 나온다.",
    "incident": true,
    "traits": {
      "chunkSize": 1000,
      "checkpointingEnabled": true
    },
    "signal": "에러율이 크게 줄었지만 0이 아니다 — 재시작 때마다 `duplicate settlement row … (already applied)`가 찍힌다.",
    "diagnosis": "재처리가 '이미 반영했는지'를 확인하지 않고 그냥 다시 쓴다. 체크포인트는 다시 할 범위를 줄일 뿐, 그 범위 안의 중복은 막지 못한다. 중복을 막는 것은 반영 자체를 멱등하게 만드는 것이다.",
    "concepts": [
      "MISSING_RECONCILIATION",
      "MISSING_IDEMPOTENT_CONSUMER"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "멱등한 정산 반영",
      "change": {
        "idempotentReconciliationEnabled": true
      },
      "why": "거래 ID 같은 자연 키로 '이미 반영됨'을 판별해(유니크 제약·upsert·처리 이력), 같은 레코드를 몇 번 다시 처리해도 결과가 한 번만 남게 한다."
    },
    "metrics": [
      "errorRate",
      "queueLag",
      "consumerThroughput"
    ],
    "claims": [
      {
        "metric": "errorRate",
        "direction": "DOWN"
      },
      {
        "metric": "queueLag",
        "direction": "SAME"
      },
      {
        "metric": "consumerThroughput",
        "direction": "SAME"
      }
    ],
    "tradeoff": "중복 정산이 0이 됐다. 다시 처리하는 양과 처리량은 그대로다 — 멱등성은 '다시 해도 안전하게' 만들 뿐 '덜 하게' 만들지 않는다. 대신 반영마다 키 확인이 붙고, 처리 이력 테이블은 보관 기간을 정해 정리해야 한다.",
    "blocks": [
      {
        "type": "diagram",
        "caption": "같은 레코드가 두 번 와도 결과는 한 번만 남는다.",
        "alt": "재처리된 레코드는 거래 ID로 처리 이력을 확인해, 이미 반영된 것이면 건너뛰고 아니면 정산 테이블에 반영한다",
        "mermaid": "flowchart LR\n  R[\"재처리된 레코드\"] --> K{\"거래 ID\\n이미 반영?\"}\n  K -- \"예\" --> S[\"건너뛰기\"]\n  K -- \"아니오\" --> W[(\"정산 테이블\")]"
      },
      {
        "type": "callout",
        "tone": "tradeoff",
        "title": "대사(reconciliation)는 따로 남긴다",
        "body": "멱등한 반영은 **이 배치가 만드는** 중복을 막는다. 외부 정산 API 쪽 결과와 우리 정산 테이블이 서로 맞는지는 별도의 대사 배치로 확인한다 — 이 시뮬레이션은 그 차이를 모델링하지 않는, 엔진 밖의 영역이다."
      }
    ],
    "links": {
      "labs": [
        "engine-batch-chunk"
      ],
      "challenges": [
        "idempotency"
      ]
    }
  },
  {
    "title": "이제 청크를 다시 키운다 — 1,000 → 10,000",
    "situation": "중복은 없고 재시작도 안전하다. 그런데 1단계에서 줄인 청크 탓에 처리량이 평시의 절반 수준이라 마감이 빠듯하다.",
    "incident": true,
    "traits": {
      "chunkSize": 1000,
      "checkpointingEnabled": true,
      "idempotentReconciliationEnabled": true
    },
    "signal": "중복 정산은 0, 재처리 범위는 청크 하나. 하지만 처리량이 낮고, 커밋 오버헤드가 처리 시간에서 큰 비중을 차지한다.",
    "diagnosis": "체크포인트와 멱등성이 들어온 뒤로는 청크 크기가 '실패 시 다시 할 양'과 '커밋 오버헤드' 사이의 순수한 트레이드오프가 됐다. 실패해도 다시 할 양은 청크 하나이고, 다시 해도 안전하니 청크를 키울 여유가 생겼다.",
    "concepts": [
      "MISSING_CHUNKING"
    ],
    "action": {
      "kind": "TUNE",
      "label": "청크 크기 1,000 → 10,000",
      "change": {
        "chunkSize": 10000
      },
      "why": "커밋 횟수를 열 배 줄여 고정 오버헤드를 덜어 낸다."
    },
    "metrics": [
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "queueLag",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "UP"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리량이 평시에 가까이 돌아왔고, 중복 정산은 여전히 0이다. 대가로 실패 시 다시 할 양이 열 배가 됐고 청크 하나가 길어졌다 — 1단계와 같은 값으로 돌아왔지만, 이번에는 그 값이 무엇을 대가로 무엇을 사는지 알고 고른 것이다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "긴 청크 = 긴 트랜잭션",
        "body": "청크 하나가 곧 트랜잭션 하나다. 청크가 길어지면 그동안 정산 테이블의 락과 DB 커넥션을 오래 붙잡고, 같은 테이블을 쓰는 다른 작업이 기다린다. 외부 정산 API 호출을 반영 트랜잭션 밖으로 빼서(확정 → 반영 순) 트랜잭션은 쓰기만 하도록 짧게 유지한다 — 락 경합은 이 엔진이 모델링하지 않는 위험이다."
      }
    ],
    "links": {
      "labs": [
        "engine-batch-chunk"
      ]
    }
  },
  {
    "title": "더 키우면 더 빨라질까? — 10,000 → 100,000",
    "situation": "청크를 키웠더니 빨라졌다. 그렇다면 열 배 더 키우면 더 빨라지지 않을까?",
    "incident": true,
    "traits": {
      "chunkSize": 10000,
      "checkpointingEnabled": true,
      "idempotentReconciliationEnabled": true
    },
    "signal": "처리량은 이미 평시에 가깝고, 커밋 오버헤드는 청크 처리 시간에서 작은 부분만 차지한다.",
    "diagnosis": "남은 오버헤드가 작아서 더 줄일 게 거의 없다. 반면 실패 시 다시 할 양은 청크 크기에 정비례해 커진다 — 커밋 절약분을 재처리 비용이 그대로 먹어 버린다.",
    "concepts": [
      "MISSING_CHUNKING",
      "MISSING_RESTARTABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "청크 크기 10,000 → 100,000",
      "change": {
        "chunkSize": 100000
      },
      "why": "커밋 오버헤드를 한 번 더 줄여 보려는 시도."
    },
    "metrics": [
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "SAME"
      },
      {
        "metric": "queueLag",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "UP"
      }
    ],
    "tradeoff": "처리량은 그대로인데, 실패 한 번에 다시 할 양이 열 배가 되고 청크 하나(=트랜잭션 하나)가 몇 배로 길어졌다. 얻은 것 없이 위험만 키운 조정이다 — 청크 크기에는 '클수록 좋은' 구간이 끝나는 지점이 있고, 그 지점은 커밋 오버헤드와 실패 시 재처리 비용이 만나는 곳이다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "체크포인트와 멱등성을 켠 채로 청크 크기만 바꿔 가며 처리량이 가장 높은 지점을 찾아보자. 아래 랩 링크는 지금 단계의 설정으로 열린다."
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "이 시뮬레이션에서 외부 정산 API의 저하는 어떤 조치로도 바뀌지 않는다. 실제로는 저하된 API를 계속 두드리는 재시도가 저하를 더 키울 수 있다 — 청크 실패 시 **백오프를 둔 재시도**와, API가 회복될 때까지 배치를 멈추는 판단이 다음에 다룰 위험이다."
      }
    ],
    "links": {
      "labs": [
        "engine-batch-chunk"
      ],
      "challenges": [
        "retry-backoff"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "청킹(chunking) — 대용량 레코드를 어떤 단위로 나누어 처리하는지",
    "where": "step-4"
  },
  {
    "item": "재시작성(restartability) — 중간 실패 시 처음부터 재실행 vs 체크포인트 재개",
    "where": "step-2"
  },
  {
    "item": "정산 정합성(reconciliation) — 재처리 시 중복 반영 방지",
    "where": "step-3"
  },
  {
    "item": "장시간 실행(long transaction)에 대한 대응",
    "where": "step-4"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-5"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'autoscaling',
    '실시간 추천 API',
    '바이럴로 트래픽이 10배가 된 순간 롤링 배포가 겹친다. Pod를 늘려도 용량이 안 늘어나는 이유와, 무엇을 먼저 고쳐야 확장이 비로소 듣는지를 단계별로 따라갑니다.',
    7,
    $g$[
  {
    "type": "text",
    "body": "추천 API는 홈 화면이 열릴 때마다 호출된다. 응답이 늦거나 실패하면 화면이 비어 보인다 — 사용자가 바로 느낀다.\n\n- **응답 지연과 에러율** — 홈 화면을 막으므로 지켜야 한다.\n- **추천의 정밀도** — 잠깐 덜 개인화된 결과(인기 목록)를 보여 줘도 괜찮다.\n- 평소에는 초당 100건 정도를 Pod 4개가 여유 있게 받지만, 콘텐츠가 바이럴되면 트래픽이 **10배**로 뛴다. 그리고 하필 그 시간에도 배포는 나간다."
  },
  {
    "type": "system",
    "domain": "autoscaling",
    "incident": false,
    "traits": {},
    "caption": "평시 — Pod 4개가 절반 정도의 부하로 여유 있게 받는다."
  },
  {
    "type": "system",
    "domain": "autoscaling",
    "incident": true,
    "traits": {},
    "caption": "바이럴 + 롤링 배포 — 같은 설계 그대로. 일부 Pod는 OOM으로 재시작을 반복하고, 배포가 남은 용량까지 깎아 처리 용량이 수요의 몇 분의 일로 떨어진다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "홈 화면의 응답성은 포기하지 않는다. 대신 추천의 정밀도는 잠시 포기할 수 있다. 그리고 하나 더 — **Pod 수는 용량이 아니다.** 떠 있는 Pod 중 실제로 요청을 받을 수 있는 것만 용량이다. 이 구분이 이 가이드 전체를 관통한다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "로드밸런서는 readiness를 통과한 Pod에만 요청을 보낸다. HPA가 지표를 보고 Pod 수를 조절하고, 배포는 Pod를 하나씩 새 버전으로 바꾼다.",
    "alt": "사용자의 요청이 로드밸런서를 거쳐 추천 API Pod들로 간다. HPA가 지표를 보고 Pod 수를 늘리거나 줄이고, 롤링 배포가 Pod를 새 버전으로 교체한다. Pod는 피처 저장소와 모델을 읽는다.",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> LB[\"로드밸런서\"]\n  LB -- \"ready인 Pod에만\" --> P[\"추천 API Pod × N\"]\n  P --> F[(\"피처 저장소\")]\n  HPA[\"HPA\"] -. \"Pod 수 조절\" .-> P\n  D[\"롤링 배포\"] -. \"Pod 교체\" .-> P\n  M[\"지표\"] --> HPA"
  },
  {
    "type": "steps",
    "title": "요청과 확장의 흐름",
    "items": [
      {
        "title": "요청 분배",
        "body": "로드밸런서가 요청을 Pod들에 나눈다. readiness probe를 통과하지 못한 Pod는 받지 않는다."
      },
      {
        "title": "추천 계산",
        "body": "Pod는 피처와 모델을 메모리에 올려 추천을 계산한다 — 그래서 요청이 몰리면 메모리 사용량이 함께 뛴다."
      },
      {
        "title": "확장과 교체",
        "body": "HPA는 지표를 보고 Pod를 늘리거나 줄이고, 배포는 Pod를 하나씩 새 버전으로 바꾼다. 둘 다 '요청을 받을 수 있는 Pod 수'를 순간적으로 바꾼다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "바이럴 — 일단 Pod를 10배로 늘린다",
    "situation": "추천 콘텐츠가 바이럴되며 트래픽이 10배로 뛰었다. 마침 롤링 배포도 진행 중이다. 가장 먼저 떠오르는 조치는 Pod를 늘리는 것이다.",
    "incident": true,
    "traits": {},
    "signal": "P95 지연과 503 에러가 함께 치솟는다. Pod 일부가 OOMKilled(exit 137)로 재시작을 반복하고, 처리 용량은 수요의 몇 분의 일이다.",
    "diagnosis": "수요가 용량을 크게 넘은 것은 맞다. 그런데 용량이 작은 이유가 Pod 수만이 아니다 — 떠 있는 Pod 중 상당수가 OOM으로 죽어 있거나 배포로 빠져 있다. 이 상태에서 Pod를 늘리면 무슨 일이 생기는지 보자.",
    "concepts": [
      "MISSING_AUTOSCALING",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "Pod 4 → 40개",
      "change": {
        "podReplicas": 40
      },
      "why": "수요가 10배니 Pod도 10배로 — 가장 직관적인 대응."
    },
    "metrics": [
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "queueLag",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리 용량은 10배가 됐지만 여전히 수요에 못 미친다 — 지연과 에러는 그대로다. 그리고 재시작을 반복하는 Pod(이 도메인에서 '적체 / 대기' 지표)도 10배로 늘었다. 새로 뜬 Pod도 같은 메모리 limit으로 똑같이 죽고, 배포에도 똑같이 휩쓸리기 때문이다. Pod 비용은 10배를 내고, 그중 실제로 일하는 것은 일부다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "Pod 10개를 띄우면 실제로 요청을 받는 Pod는 몇 개인가.",
        "before": {
          "label": "Pod 수",
          "alt": "Pod를 10개 띄운 것으로 보이지만 그중 일부만 요청을 받는다",
          "mermaid": "flowchart LR\n  S[\"Pod 10개 요청\"] --> R[\"떠 있는 Pod 10개\"]"
        },
        "after": {
          "label": "실제 용량",
          "alt": "Pod 10개 중 6개는 OOM으로 재시작 중이고, 남은 4개 중 일부는 배포로 빠져 3개 정도만 요청을 받는다",
          "mermaid": "flowchart LR\n  R[\"Pod 10개\"] -- \"OOM 재시작\" --> X[\"6개 죽음\"]\n  R --> A[\"4개 살아 있음\"]\n  A -- \"배포로 빠짐\" --> Y[\"약 3개만 요청 처리\"]"
        }
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "두 손실은 곱해진다",
        "body": "OOM으로 남는 비율과 배포 중 남는 비율은 서로 독립이라 **곱해진다** — 둘이 겹치면 떠 있는 Pod의 3할도 일하지 못한다. Pod를 늘리는 것은 이 비율에 곱하는 수를 키울 뿐, 비율 자체는 바꾸지 못한다."
      }
    ],
    "links": {
      "labs": [
        "engine-autoscaling"
      ],
      "failurePattern": "autoscaling"
    }
  },
  {
    "title": "Pod가 죽는 이유를 고친다 — 리소스 request/limit 조정",
    "situation": "Pod를 40개로 늘렸지만 지연·에러는 그대로다. 재시작을 반복하는 Pod가 24개로 늘었다.",
    "incident": true,
    "traits": {
      "podReplicas": 40
    },
    "signal": "Pod 이벤트에 OOMKilled(exit 137)가 반복된다. 재시작 중인 Pod가 전체의 6할이다.",
    "diagnosis": "메모리 limit이 실제 사용량보다 낮다. 트래픽이 몰려 추천 계산에 메모리가 더 필요해지는 순간 limit을 넘어 커널에 kill되고, 재시작해 다시 요청을 받으면 또 죽는다. 용량 손실 중 가장 큰 쪽이 여기다.",
    "concepts": [
      "MISSING_RESOURCE_LIMITS"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "리소스 request/limit 재산정",
      "change": {
        "resourceLimitsTuned": true
      },
      "why": "부하 테스트로 피크 메모리 사용량을 재고, 그에 맞춰 request/limit을 다시 정한다 — 죽지 않는 Pod여야 용량이 된다."
    },
    "metrics": [
      "queueLag",
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "queueLag",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "crash loop이 사라지고 처리 용량이 수요를 넘어섰다 — 지연과 에러가 크게 내려간다. 다만 아직 정상은 아니다. 사용률이 높은 구간이라 지연이 평시보다 길고 에러도 남아 있다. 그리고 limit을 올린 만큼 Pod 하나가 노드에서 차지하는 자리가 커져, 같은 노드에 들어가는 Pod 수가 줄고 노드 비용이 오른다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tradeoff",
        "title": "limit을 아예 없애면?",
        "body": "OOM kill은 사라지지만, 메모리를 많이 먹는 Pod 하나가 노드 전체를 잡아먹어 같은 노드의 다른 Pod까지 쫓겨난다. limit은 없애는 게 아니라 **측정해서** 맞추는 것이다 — 감으로 정한 값은 안 정한 것보다 나쁠 수 있다."
      }
    ],
    "links": {
      "labs": [
        "engine-autoscaling"
      ]
    }
  },
  {
    "title": "배포가 용량을 깎지 않게 — readiness probe와 PDB",
    "situation": "Pod는 더 이상 죽지 않는다. 그런데 배포가 진행되는 동안에는 여전히 지연과 에러가 평시보다 높다.",
    "incident": true,
    "traits": {
      "podReplicas": 40,
      "resourceLimitsTuned": true
    },
    "signal": "배포 중 '503 no healthy upstream'이 간헐적으로 찍힌다. 처리 용량이 떠 있는 Pod 수에서 기대하는 값의 7할 정도다.",
    "diagnosis": "롤링 배포가 기존 Pod를 내리고, 새 Pod는 모델을 다 올리기 전에 요청을 받기 시작한다. 한꺼번에 빠지는 Pod 수에도 상한이 없다 — 배포 동안 용량의 3할 정도가 비어 있다.",
    "concepts": [
      "MISSING_ROLLOUT_SAFETY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "무중단 배포 안전장치",
      "change": {
        "rolloutSafeguardEnabled": true
      },
      "why": "readiness probe로 준비된 Pod에만 트래픽을 보내고, PodDisruptionBudget과 maxUnavailable로 한꺼번에 빠지는 Pod 수를 제한한다."
    },
    "metrics": [
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "이제 떠 있는 Pod가 모두 용량이 되고, 지연·에러가 평시 수준으로 돌아왔다. 대가는 배포 속도다 — 준비된 Pod만 받고 한 번에 조금씩만 교체하니 배포가 길어지고, 그동안 새 Pod를 미리 띄울 여분(surge)만큼 자원이 더 든다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "배포 중 Pod 하나가 교체되는 동안 요청은 어디로 가는가.",
        "before": {
          "label": "안전장치 없음",
          "alt": "기존 Pod가 먼저 내려가고, 아직 모델을 올리는 중인 새 Pod로 요청이 가서 실패한다",
          "mermaid": "flowchart LR\n  LB[\"로드밸런서\"] --> N[\"새 Pod\\n(모델 로딩 중)\"]\n  N -- \"실패\" --> E[\"503\"]"
        },
        "after": {
          "label": "readiness + PDB",
          "alt": "새 Pod가 준비를 마칠 때까지 요청은 기존 Pod로만 가고, 준비가 끝난 뒤에야 기존 Pod가 내려간다",
          "mermaid": "flowchart LR\n  LB[\"로드밸런서\"] --> O[\"기존 Pod\\n(ready)\"]\n  N[\"새 Pod\\n(준비 중)\"] -. \"ready 후 투입\" .-> LB"
        }
      }
    ],
    "links": {
      "labs": [
        "engine-autoscaling"
      ]
    }
  },
  {
    "title": "다음 바이럴에 대비해? — Pod를 76개로",
    "situation": "장애는 진정됐다. 다음 바이럴에 대비해 Pod를 더 늘려 두자는 제안이 나왔다.",
    "incident": true,
    "traits": {
      "podReplicas": 40,
      "resourceLimitsTuned": true,
      "rolloutSafeguardEnabled": true
    },
    "signal": "사용률은 이미 여유 구간이고, 지연·에러는 평시 수준이다.",
    "diagnosis": "병목이 더 이상 용량에 있지 않다 — 여기서 Pod를 더해도 사용자가 겪는 지표는 바뀌지 않는다. 1단계에서는 '늘려도 안 늘어났고', 지금은 '늘어나도 쓸 데가 없다'.",
    "concepts": [
      "MISSING_AUTOSCALING"
    ],
    "action": {
      "kind": "TUNE",
      "label": "Pod 40 → 76개",
      "change": {
        "podReplicas": 76
      },
      "why": "다음 트래픽 폭증분을 미리 받아 둘 여유."
    },
    "metrics": [
      "consumerThroughput",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "errorRate",
        "direction": "SAME"
      }
    ],
    "tradeoff": "처리 용량은 거의 두 배가 됐지만 지연·에러는 그대로다 — Pod 비용만 늘었다. 고정된 여유를 늘 들고 있기보다, 지표를 보고 늘리고 줄이는 HPA의 최소·최대값과 확장 기준을 정하는 쪽이 같은 대비를 더 싸게 한다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "이 시뮬레이션은 Pod를 늘리는 즉시 용량이 된다고 본다. 실제로는 HPA가 지표를 모으고 결정하는 데 시간이 걸리고, 새 Pod는 이미지를 받고 모델을 메모리에 올리는 동안 요청을 받지 못한다(**콜드 스타트**). 노드가 모자라면 노드부터 새로 떠야 한다. 몇 초 만에 10배가 되는 폭증은 반응형 확장이 따라가지 못하므로 — 최소 Pod 수를 넉넉히 두거나, 예고된 이벤트는 미리 확장하거나, 넘치는 요청은 인기 목록 같은 가벼운 응답으로 대신하는(degradation) 설계가 다음에 다룰 문제다."
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "리소스 제한과 배포 안전장치를 켠 상태에서, 지연이 평시 수준을 유지하는 최소 Pod 수는 몇 개일까? 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-autoscaling"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "고수준 아키텍처와 요청 흐름",
    "where": "architecture"
  },
  {
    "item": "Pod 오토스케일링(HPA) 전략",
    "where": "step-4"
  },
  {
    "item": "리소스 request/limit 설정 기준",
    "where": "step-2"
  },
  {
    "item": "무중단 배포(readiness probe/PodDisruptionBudget) 전략",
    "where": "step-3"
  },
  {
    "item": "트래픽 급증 대응 전략",
    "where": "step-1"
  },
  {
    "item": "관측(metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 병목과 트레이드오프",
    "where": "step-4"
  }
]$g$::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'deployment',
    '카나리 배포',
    '결함 있는 새 버전이 카나리로 나간 뒤 실패가 트래픽 비율을 따라 커지는 장애. 지켜보는 동안 무엇이 커지는지, 롤백이 무엇을 되돌리고 무엇을 못 되돌리는지, 카나리를 작게 시작하면 무엇을 얻고 무엇이 안 보이게 되는지를 단계별로 따라갑니다.',
    8,
    $g$[
  {
    "type": "text",
    "body": "주문 결제(checkout) API는 평시 초당 **800건**을 받고, 여러 팀이 **하루 한 번 이상** 새 버전을 배포한다.\n\n- **에러 예산** — 에러율이 1%를 넘으면 장애다. 결제 실패는 곧바로 매출 손실이다.\n- **되돌리기** — 이미지 교체는 1분 안에 끝나지만, DB 마이그레이션이 섞이면 되돌릴 수 없는 경우가 있다.\n- 스테이징 테스트를 통과했다고 운영에서 안전하다는 보장은 없다. 결함은 실제 트래픽에서 드러나는 경우가 많다."
  },
  {
    "type": "system",
    "domain": "deployment",
    "incident": false,
    "traits": {},
    "caption": "평시 배포 — 새 버전이 정상이라면 카나리가 트래픽 일부를 받아도 지표는 평소와 같다."
  },
  {
    "type": "system",
    "domain": "deployment",
    "incident": true,
    "traits": {},
    "caption": "결함 버전이 카나리로 나갔다 — 같은 설계 그대로. 카나리가 받는 요청 중 다수가 실패해 전체 에러율이 에러 예산을 훌쩍 넘는다. 트래픽·DB는 평소와 같다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "모든 결함을 배포 전에 막는 것은 포기한다. 대신 결함이 나갔을 때 **피해를 받는 사용자 비율**과 **피해가 이어지는 시간**을 작게 묶는 것을 보장한다 — 이 두 가지가 2·3단계의 조치로 이어진다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "라우터가 가중치로 트래픽을 나눠 기존 버전과 카나리에 보낸다. 롤아웃 컨트롤러가 버전별 지표를 보고 승격·중단·롤백을 정한다.",
    "alt": "사용자 요청이 라우터를 거쳐 대부분은 기존 버전(stable)으로, 일부는 새 버전(canary)으로 간다. 두 버전의 지표가 버전별로 모니터링에 모이고, 롤아웃 컨트롤러가 이를 보고 라우터의 가중치를 올리거나(승격) 0으로 되돌린다(롤백).",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> LB[\"라우터\\n(가중치 분배)\"]\n  LB -- \"대부분\" --> S[\"checkout 2.13\\nstable\"]\n  LB -- \"카나리 비율\" --> C[\"checkout 2.14\\ncanary\"]\n  S --> M[(\"버전별 지표\")]\n  C --> M\n  M --> RC[\"롤아웃 컨트롤러\"]\n  RC -. \"승격 / 롤백\" .-> LB"
  },
  {
    "type": "steps",
    "title": "배포 흐름",
    "items": [
      {
        "title": "전략 고르기",
        "body": "Rolling은 단순하지만 결함 버전이 Pod 단위로 계속 늘어난다. Blue-Green은 순간 전환이라 되돌리기는 쉽지만 결함이 한 번에 100%로 나간다. 하루 여러 번 배포하고 결제 실패가 곧 손실인 서비스라, 피해 비율을 조절할 수 있는 Canary를 고른다."
      },
      {
        "title": "카나리 시작",
        "body": "새 버전에 트래픽 일부만 보낸다. 첫 비율이 결함이 있을 때 첫 단계의 피해 크기다."
      },
      {
        "title": "단계마다 관찰 후 승격",
        "body": "정해진 관찰 시간이 지나면 비율을 두 배로 올린다(예: 10 → 20 → 40 → 80 → 100%). 단계마다 카나리 구간의 에러율·지연을 기존 버전과 비교한다."
      },
      {
        "title": "이상하면 중단·롤백",
        "body": "임계를 넘으면 승격을 멈추고 카나리 비율을 0으로 되돌린다. 원인 분석은 되돌린 다음에 한다."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "카나리 직후 결제 실패 — 지켜보는 사이 비율이 두 배로",
    "situation": "checkout 2.14.0이 카나리로 나간 뒤 결제 실패가 늘기 시작했다. 카나리는 단계마다 관찰 시간이 지나면 자동으로 비율을 두 배로 올린다. 팀은 원인을 조금 더 확인하고 판단하기로 했다.",
    "incident": true,
    "traits": {},
    "signal": "전체 에러율이 평소의 수십 배로 뛰고 P95도 오른다(클라이언트 재시도). 트래픽·DB 사용률은 평소와 같다. 실패 로그에는 version=2.14.0-canary만 찍힌다.",
    "diagnosis": "원인은 인프라가 아니라 방금 나간 변경이다. 실패는 결함 버전이 받는 트래픽 비율에 비례한다 — 비율이 커지면 피해도 그대로 커진다. 버전별로 지표를 나눠 보지 않았다면 이 단서도 늦게 찾았을 것이다.",
    "concepts": [
      "MISSING_CANARY_ANALYSIS",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "조치 없이 관찰 — 자동 승격 1단계",
      "change": {
        "rolloutPromotions": 1
      },
      "why": "에러율이 아직 감당할 만해 보이니 원인을 더 확인하고 움직이겠다는 판단. 그 사이 카나리 시계는 멈추지 않고 다음 단계로 넘어간다."
    },
    "metrics": [
      "errorRate",
      "p95LatencyMs",
      "availability"
    ],
    "claims": [
      {
        "metric": "errorRate",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "UP"
      },
      {
        "metric": "availability",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "카나리 비율이 두 배가 되자 에러율도 거의 두 배가 됐다. 원인 분석에 쓴 시간이 그대로 피해가 됐다 — 지금 먼저 할 일은 원인 분석이 아니라 확산을 멈추는 것이다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "결함 버전이 받는 비율이 곧 피해의 크기다.",
        "before": {
          "label": "카나리 시작",
          "alt": "라우터가 트래픽 일부만 결함 있는 카나리로 보내고 나머지는 기존 버전으로 보낸다",
          "mermaid": "flowchart LR\n  LB[\"라우터\"] -- \"대부분\" --> S[\"stable\"]\n  LB -- \"일부\" --> C[\"canary (결함)\"]"
        },
        "after": {
          "label": "자동 승격 후",
          "alt": "관찰 시간이 지나 카나리 비율이 두 배가 되어 결함 버전으로 가는 트래픽도 두 배가 된다",
          "mermaid": "flowchart LR\n  LB[\"라우터\"] -- \"줄어듦\" --> S[\"stable\"]\n  LB -- \"두 배\" --> C[\"canary (결함)\"]"
        }
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "서버를 늘리면 나아지지 않을까?",
        "body": "트래픽·DB 사용률은 평소와 같다. 실패는 용량이 아니라 코드 결함에서 나오므로, 서버를 늘려도 결함 버전으로 가는 요청은 똑같이 실패한다."
      }
    ],
    "links": {
      "labs": [
        "engine-canary-rollout"
      ],
      "failurePattern": "deployment"
    }
  },
  {
    "title": "확산을 멈춘다 — 롤백",
    "situation": "카나리가 다음 단계로 넘어가 피해가 커졌다. 이번에는 원인보다 확산부터 멈춘다.",
    "incident": true,
    "traits": {
      "rolloutPromotions": 1
    },
    "signal": "에러율이 단계가 바뀔 때마다 계단처럼 오른다. 다음 승격까지 남은 시간이 피해가 더 커지기까지 남은 시간이다.",
    "diagnosis": "결함 버전이 트래픽을 받는 한 실패는 계속된다. 실패를 0으로 만드는 방법은 결함 버전으로 가는 트래픽을 0으로 만드는 것뿐이다.",
    "concepts": [
      "MISSING_ROLLBACK_PLAN"
    ],
    "action": {
      "kind": "TUNE",
      "label": "롤백 — 카나리 비율을 0으로",
      "change": {
        "rolledBack": true
      },
      "why": "결함 버전으로 가는 트래픽을 끊는다. 어떤 변경이 원인인지는 되돌린 다음에 찾는다."
    },
    "metrics": [
      "errorRate",
      "p95LatencyMs",
      "availability"
    ],
    "claims": [
      {
        "metric": "errorRate",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "availability",
        "direction": "UP"
      }
    ],
    "tradeoff": "지표는 평소로 돌아왔다. 하지만 롤백이 1분 안에 끝난 건 이번 배포가 이미지 교체뿐이었기 때문이다. 컬럼 삭제 같은 파괴적 마이그레이션이 섞였다면 이전 버전이 새 스키마에서 실패해, 롤백 버튼이 있어도 되돌릴 수 없다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tip",
        "title": "일시 중지로 충분하지 않은 이유",
        "body": "승격을 일시 중지하면 비율이 더 커지는 것은 막지만, 이미 카나리가 받고 있는 트래픽의 실패는 그대로 이어진다. 중지는 판단할 시간을 버는 조치이고, 실패를 멈추는 조치는 롤백이다."
      },
      {
        "type": "callout",
        "tone": "tradeoff",
        "title": "되돌릴 수 있는 배포를 만드는 비용",
        "body": "스키마는 **확장-축소(expand/contract)** 두 단계로 바꿔 이전 버전도 새 스키마에서 동작하게 하고, 위험한 기능은 **피처 플래그** 뒤에 두어 코드 배포와 기능 활성화를 분리한다. 마이그레이션이 두 번이 되고 플래그를 관리할 일이 늘지만, 그래야 롤백이 언제나 선택지로 남는다. (이 부분은 시뮬레이션이 다루지 않는 엔진 밖의 위험이다.)"
      }
    ],
    "links": {
      "labs": [
        "engine-canary-rollout"
      ]
    }
  },
  {
    "title": "롤아웃 계획을 다시 짠다 — 1%부터",
    "situation": "원인(PriceCalculator 교체)을 찾아 고쳤다. 다시 배포하기 전에 롤아웃 계획부터 다시 본다 — 이번에는 결함을 알아채기도 전에 첫 단계부터 많은 사용자가 실패를 겪었다.",
    "incident": true,
    "traits": {
      "rolloutPromotions": 1,
      "rolledBack": true
    },
    "signal": "롤백 뒤 에러율·지연은 평소 수준이다.",
    "diagnosis": "첫 카나리 비율이 곧 '결함이 있을 때 첫 단계의 피해'다. 판단 기준 없이 비율만 정해 두면, 첫 단계는 결함을 찾는 단계가 아니라 결함을 퍼뜨리는 단계가 된다.",
    "concepts": [
      "MISSING_CANARY_ANALYSIS",
      "MISSING_ROLLOUT_SAFETY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "롤아웃 계획 재설계 — 시작 비율 10% → 1%",
      "change": {
        "canaryStartPercent": 1,
        "rolloutPromotions": 0
      },
      "why": "첫 단계의 피해를 작게 묶고, 단계마다 카나리 구간의 에러율·지연을 기존 버전과 비교해 승격 여부를 정한다."
    },
    "metrics": [
      "errorRate",
      "p95LatencyMs",
      "availability"
    ],
    "claims": [
      {
        "metric": "errorRate",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      },
      {
        "metric": "availability",
        "direction": "SAME"
      }
    ],
    "tradeoff": "지금 지표는 하나도 바뀌지 않는다 — 계획의 효과는 다음 배포에서야 드러난다. 대신 1%에서 두 배씩 올리면 100%까지 단계가 일곱 번(1 → 2 → 4 → 8 → 16 → 32 → 64 → 100%)이고, 단계마다 관찰 시간을 두는 만큼 배포가 길어진다.",
    "blocks": [
      {
        "type": "numbers",
        "title": "같은 결함이 첫 단계에 섞였다면 — 10% 시작 vs 1% 시작",
        "domain": "deployment",
        "incident": true,
        "base": {},
        "change": {
          "canaryStartPercent": 1
        },
        "changeLabel": "시작 비율 10% → 1%",
        "metrics": [
          "errorRate",
          "p95LatencyMs",
          "availability"
        ],
        "claims": [
          {
            "metric": "errorRate",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          },
          {
            "metric": "availability",
            "direction": "UP"
          }
        ]
      },
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "시작 비율을 바꾸면 첫 단계의 피해는 어떻게 변할까? 같은 결함이 100%까지 번지려면 승격이 몇 번 필요할까? 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-canary-rollout"
      ]
    }
  },
  {
    "title": "수정 빌드를 1% 카나리로 — 작아진 만큼 안 보인다",
    "situation": "수정 빌드 2.14.1을 새 계획대로 1% 카나리로 내보낸다. 수정이 다른 결함을 남기지 않았다는 보장은 없다.",
    "incident": true,
    "traits": {
      "rolloutPromotions": 0,
      "rolledBack": true,
      "canaryStartPercent": 1
    },
    "signal": "배포 전 지표는 평소 수준이다. 스테이징 테스트는 통과했다.",
    "diagnosis": "스테이징 통과는 운영에서의 안전을 보장하지 않는다. 결함이 또 섞여 있을 수 있다는 전제로, 피해가 가장 작은 비율에서 시작한다.",
    "concepts": [
      "MISSING_CANARY_ANALYSIS",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "TUNE",
      "label": "재배포 — 수정 빌드를 1% 카나리로",
      "change": {
        "rolledBack": false
      },
      "why": "새 계획의 첫 단계. 결함이 남아 있어도 피해를 받는 사용자는 1%다."
    },
    "metrics": [
      "errorRate",
      "p95LatencyMs",
      "availability"
    ],
    "claims": [
      {
        "metric": "errorRate",
        "direction": "UP"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "UP"
      },
      {
        "metric": "availability",
        "direction": "SAME"
      }
    ],
    "tradeoff": "결함은 또 있었지만 피해는 1% 사용자로 묶였다 — 10%로 시작했다면 에러율이 몇 배였을 것이다(3단계 비교). 그런데 바로 그 작은 크기 때문에 전체 가용성은 거의 변하지 않았고, 전체 에러율은 에러 예산 1% 아래에 머문다. 전체 지표만 보는 자동 중단 기준은 이 결함을 놓친다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "카나리 구간을 따로 봐야 한다",
        "body": "전체 에러율에서는 묻히지만, **카나리 버전만 떼어 보면** 요청의 대다수가 실패하고 있다. 그래서 자동 중단·롤백 기준은 전체 지표가 아니라 **버전별 지표**(카나리 vs 기존 버전)에 걸어야 한다. Drill의 롤아웃 시계에서도 같다 — 전체 에러율 1%에 건 자동 롤백 기준은 1% 단계에서는 울리지 않고, 비율이 2%로 올라 전체 에러율이 1%를 넘은 뒤에야 동작한다."
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "카나리 트래픽이 작을수록 통계적으로 판단하기 어렵고, 임계를 너무 낮게 잡으면 정상 변동에도 배포가 멈춘다. 또 여러 팀이 같은 날 배포하면서 **한 배포에 여러 변경이 섞이면** 어떤 변경이 원인인지 찾는 시간이 길어진다 — 한 배포에 한 가지 변경, 배포 순서 조율이 다음에 다룰 위험이다."
      }
    ],
    "links": {
      "labs": [
        "engine-canary-rollout"
      ]
    }
  }
]$g$::jsonb,
    $g$[
  {
    "item": "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "where": "requirements"
  },
  {
    "item": "배포 전략 (Rolling / Blue-Green / Canary)과 고른 이유",
    "where": "architecture"
  },
  {
    "item": "카나리 비율·단계와 단계마다 무엇을 보고 판단하는지",
    "where": "step-3"
  },
  {
    "item": "자동 중단·롤백 기준(에러율·지연 임계)",
    "where": "step-4"
  },
  {
    "item": "되돌릴 수 있는 배포 — 마이그레이션·피처 플래그",
    "where": "step-2"
  },
  {
    "item": "관측(버전별 metrics/logs/alert) 계획",
    "where": "step-1"
  },
  {
    "item": "예상 위험과 트레이드오프",
    "where": "step-4"
  }
]$g$::jsonb
);
