-- docs/LEARNING_DEEPENING_PLAN.md L13 파일럿 (PLAN.md Round E37) — 캐싱·데이터 접근 카테고리 개념과
-- Cache Stampede 장애 패턴의 확장 블록.
--
-- 개념마다: 메커니즘 그림(compare) → 단계 설명(steps) → 엔진 수치(numbers, claims는 ContentBlocksTest가
-- 대조) → 다른 개념과의 관계(text) → 대가(callout). Single-flight는 V77에서 이미 같은 구성을 받았다.
-- Hot Key 분산은 엔진이 노드별 쏠림을 모델링하지 않아 수치 블록 없이 그림과 "엔진 밖" 안내만 둔다.
--
-- 장애 패턴에는 새 timeline 블록: 엔진 시계(TelemetrySampler)로 방치·틀린 대응(replica만)·올바른
-- 완화(Single-flight → replica → 정책 분리)를 샘플링하고, 경보 임계를 처음 넘는 시각을 계산한다.
-- 순서를 replica → 정책 분리로 둔 것은 정책 분리를 먼저 하면 DB 사용률이 정확히 1.0(지연 구간 경계)에
-- 머무는 구간이 생기기 때문이다. P95 경보 임계를 200ms가 아닌 150ms로 둔 것도 램프 30초 지점이 정확히
-- 200ms라 경계에 걸리기 때문이다.

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 이벤트, 같은 캐시 — 차이는 데이터마다 다른 정책을 쓰느냐다.",
    "before": {
      "label": "정책 하나(모든 키 TTL 10초)",
      "body": "가격 때문에 TTL을 짧게 맞추면, 거의 안 바뀌는 상품 설명·리뷰까지 10초마다 만료돼 miss가 난다.",
      "alt": "가격·설명·리뷰가 모두 TTL 10초 캐시 하나를 쓰고, 잦은 만료로 miss가 DB로 몰린다",
      "mermaid": "flowchart LR\n  P[\"가격\"] --> K[\"캐시\\nTTL 10초 하나\"]\n  S[\"상품 설명\"] --> K\n  R[\"리뷰\"] --> K\n  K -- \"miss 많음\" --> D[(\"DB\")]"
    },
    "after": {
      "label": "정책 분리",
      "body": "자주 바뀌는 가격만 짧게(또는 쓰기 시 무효화), 설명·리뷰는 길게 둔다. 잘 안 바뀌는 대부분의 조회가 캐시에서 끝난다.",
      "alt": "가격은 짧은 TTL과 쓰기 시 무효화, 설명은 긴 TTL, 리뷰는 stale-while-revalidate로 나뉘어 DB로 가는 miss가 줄어든다",
      "mermaid": "flowchart LR\n  P[\"가격\"] --> K1[\"짧은 TTL\\n+ 쓰기 시 무효화\"]\n  S[\"상품 설명\"] --> K2[\"긴 TTL\"]\n  R[\"리뷰\"] --> K3[\"stale-while-revalidate\"]\n  K1 -- \"miss 일부\" --> D[(\"DB\")]\n  K2 -. \"드묾\" .-> D\n  K3 -. \"뒤에서 갱신\" .-> D"
    }
  },
  {
    "type": "steps",
    "title": "정책을 나누는 순서",
    "items": [
      {
        "title": "데이터를 '얼마나 낡아도 되나'로 분류한다",
        "body": "가격·재고처럼 틀리면 돈이 걸리는 것, 설명처럼 몇 분 늦어도 되는 것, 리뷰 수처럼 대략이면 되는 것으로 나눈다."
      },
      {
        "title": "분류마다 무효화 방식을 고른다",
        "body": "짧은 TTL, 쓰기 시 무효화(write-through·삭제), 긴 TTL + 백그라운드 갱신(stale-while-revalidate) 중에서 고른다."
      },
      {
        "title": "키 이름에 정책을 드러낸다",
        "body": "`price:{id}`, `product:{id}`처럼 키 공간을 나누면 TTL·지표·무효화를 분류별로 따로 다룰 수 있다."
      },
      {
        "title": "분류별 hit ratio를 따로 본다",
        "body": "전체 평균은 한 분류의 붕괴를 가린다. 가격 키의 hit ratio가 낮은 것은 정상일 수 있고, 설명 키가 낮다면 문제다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "TTL만 늘리면",
    "domain": "product-browsing",
    "incident": true,
    "base": {},
    "change": {
      "cacheTtlSeconds": 300
    },
    "changeLabel": "모든 키 TTL 10초 → 300초",
    "metrics": [
      "cacheHitRatio",
      "dbReadLoad",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "cacheHitRatio",
        "direction": "SAME"
      },
      {
        "metric": "dbReadLoad",
        "direction": "SAME"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "SAME"
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "TTL 길이가 원인이 아니다",
    "body": "엔진의 상품 조회 모델에서 hit ratio를 떨어뜨리는 것은 **서로 다른 데이터가 한 정책을 쓰는 것**이지 TTL 숫자가 아니다 — TTL만 늘려도 아무것도 변하지 않는다. 실제 서비스에서도 TTL을 늘리면 가격이 낡아 보이는 대가만 생기기 쉽다."
  },
  {
    "type": "numbers",
    "title": "정책을 나누면 — 하지만 혼자서는",
    "domain": "product-browsing",
    "incident": true,
    "base": {},
    "change": {
      "cachePolicySplit": true
    },
    "changeLabel": "캐시 정책 분리",
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
    ]
  },
  {
    "type": "text",
    "body": "hit ratio는 20%에서 80%로 돌아오고 DB 읽기 사용률도 4분의 1로 줄지만, 여전히 100%를 훨씬 넘어 사용자가 겪는 지연·에러는 그대로다. 남은 miss 하나하나가 **동시 miss로 열 번씩 DB에 가기**(cache stampede) 때문이다.\n\n- 그래서 정책 분리는 **Single-flight**(miss의 중복 제거)와 짝을 이룬다. 둘을 함께 쓰고 **Read Replica** 한 대를 더하면 DB 사용률이 50%로 내려와 P95가 평시 수준으로 돌아온다 — 상품 조회 설계 가이드 3단계가 그 과정이다.\n- 정책 분리는 miss의 **수**를 줄이고, Single-flight는 miss 하나의 **비용**을 줄인다. 하나만으로는 이 장애가 풀리지 않는다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "분류가 늘어날수록 \"이 값은 언제 낡는가\"를 설명하기 어려워진다. 쓰기 시 무효화는 쓰기 경로마다 무효화를 빠뜨리지 않아야 해서, 새 쓰기 경로가 생길 때마다 버그의 통로가 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_CACHE_POLICY_SEPARATION';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "전체 평균은 여유로운데 한 노드만 100%인 상황 — 키가 고르게 퍼지지 않았기 때문이다.",
    "before": {
      "label": "Hot key 한 노드에 몰림",
      "body": "이벤트 상품 키 하나가 해시로 한 캐시 노드에만 놓인다. 그 노드만 포화되고 나머지는 논다.",
      "alt": "요청 대부분이 상품 42번 키를 가진 캐시 노드 B 하나로 몰리고, 노드 A와 C는 한가하다",
      "mermaid": "flowchart LR\n  U[\"요청\"] -- \"90%\" --> B[\"캐시 노드 B\\nproduct:42 (포화)\"]\n  U -- \"5%\" --> A[\"캐시 노드 A\"]\n  U -- \"5%\" --> C[\"캐시 노드 C\"]"
    },
    "after": {
      "label": "키 복제 + 로컬 캐시",
      "body": "hot key를 `product:42#0..N` 복제본으로 나눠 여러 노드에 두고, 가장 뜨거운 키는 서버 메모리에 한 겹 더 둔다.",
      "alt": "서버의 로컬 캐시가 먼저 받고, 남은 요청은 상품 42번 키의 복제본 세 개가 놓인 노드 A, B, C로 고르게 나뉜다",
      "mermaid": "flowchart LR\n  U[\"요청\"] --> L[\"로컬 캐시\\n(서버 메모리, 1초)\"]\n  L -- \"miss\" --> H{\"product:42#랜덤 0~2\"}\n  H --> A[\"노드 A\\n#0\"]\n  H --> B[\"노드 B\\n#1\"]\n  H --> C[\"노드 C\\n#2\"]"
    }
  },
  {
    "type": "steps",
    "title": "어떻게 푸나",
    "items": [
      {
        "title": "hot key를 찾는다",
        "body": "노드별 CPU·QPS 편차와 키별 접근 빈도(샘플링)를 본다. 평균만 보는 대시보드에서는 보이지 않는다."
      },
      {
        "title": "읽기는 복제로 흩는다",
        "body": "키에 접미사(`#0`~`#N`)를 붙여 N개 복사본을 두고, 읽을 때 무작위로 하나를 고른다. 쓰기는 N개 모두를 갱신하거나 지운다."
      },
      {
        "title": "가장 뜨거운 몇 개는 서버 메모리에 둔다",
        "body": "아주 짧은 TTL(1초 안팎)의 로컬 캐시 한 겹이면 그 키의 요청 대부분이 네트워크를 타지 않는다."
      },
      {
        "title": "노드 추가·제거에도 덜 흔들리게 배치한다",
        "body": "모듈러 해시(`hash % N`)는 노드 수가 바뀌면 거의 모든 키가 이사한다. Consistent Hashing은 바뀐 노드 몫만 옮긴다 — Build의 consistent-hashing 과제가 이것을 직접 만든다."
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "시뮬레이션 엔진 밖의 위험",
    "body": "Drill·랩의 엔진은 캐시를 노드 하나로 보고 hit ratio만 계산한다 — 노드별 쏠림은 모델링하지 않는다. 그래서 이 개념에는 엔진 수치 대신 그림만 있다. 상품 조회 장애에서 캐시 정책 분리·Single-flight·Read Replica로 DB를 살린 뒤에도 남는 다음 위험이 바로 이것이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **캐시 정책 분리**는 *어떤 데이터를 얼마나 오래* 둘지, Hot Key 분산은 *한 키를 어디에* 둘지를 정한다. 정책을 잘 나눠도 한 키가 한 노드에 몰리면 그 노드가 병목이다.\n- **Single-flight**는 같은 키의 동시 miss를 합쳐 DB를 보호하지만, 캐시 노드 자체의 쏠림은 그대로다.\n- **Read Replica**는 DB 쪽 읽기 용량을 늘린다. 캐시 노드 쏠림은 DB 앞에서 생기는 문제라 replica로는 풀리지 않는다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "복제본 N개를 두면 쓰기·무효화가 N배가 되고, 그 사이 잠깐 복제본끼리 값이 다를 수 있다. 로컬 캐시는 서버마다 값이 다를 수 있어 정확한 잔여 재고처럼 합계가 중요한 값에는 쓰지 않는다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_KEY_DISTRIBUTION';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "읽기를 복제본으로 보내면 주 DB는 쓰기에 집중한다 — 대신 복제본은 조금 늦다.",
    "before": {
      "label": "주 DB 하나",
      "body": "읽기와 쓰기가 같은 DB에서 경쟁한다. 읽기 폭주가 쓰기(주문·결제)까지 느리게 만든다.",
      "alt": "API의 읽기와 쓰기가 모두 주 DB 하나로 가서 경쟁한다",
      "mermaid": "flowchart LR\n  A[\"API\"] -- \"읽기 (대부분)\" --> P[(\"주 DB\")]\n  A -- \"쓰기\" --> P"
    },
    "after": {
      "label": "Read Replica",
      "body": "쓰기는 주 DB로, 읽기는 복제본으로. 방금 쓴 값을 읽어야 하는 경로만 주 DB로 보낸다.",
      "alt": "쓰기와 방금 쓴 값 읽기는 주 DB로, 나머지 읽기는 복제본 두 대로 가고, 주 DB가 복제본에 비동기로 복제한다",
      "mermaid": "flowchart LR\n  A[\"API\"] -- \"쓰기 + 방금 쓴 값 읽기\" --> P[(\"주 DB\")]\n  A -- \"일반 읽기\" --> R1[(\"replica 1\")]\n  A -- \"일반 읽기\" --> R2[(\"replica 2\")]\n  P -. \"비동기 복제 (지연)\" .-> R1\n  P -. \"비동기 복제 (지연)\" .-> R2"
    }
  },
  {
    "type": "steps",
    "title": "도입할 때 정할 것",
    "items": [
      {
        "title": "어떤 읽기가 최신이어야 하나",
        "body": "주문 직후 주문 내역처럼 방금 쓴 값을 보여 줘야 하는 화면은 주 DB로(read-your-writes). 상품 목록·리뷰는 복제본으로."
      },
      {
        "title": "라우팅을 한곳에서",
        "body": "읽기/쓰기 데이터소스를 나누는 지점을 하나로 둔다(트랜잭션의 read-only 여부 등). 코드 곳곳에서 고르면 빠뜨린다."
      },
      {
        "title": "복제 지연을 지표로",
        "body": "replica lag을 수집하고 임계를 넘으면 그 복제본을 읽기에서 빼거나 주 DB로 우회한다."
      },
      {
        "title": "원인을 먼저 본다",
        "body": "읽기가 왜 많은지(낮은 hit ratio, 중복 조회)를 먼저 확인한다. 원인이 그대로면 복제본도 같은 낭비를 받는다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "원인을 두고 replica만 더하면",
    "domain": "product-browsing",
    "incident": true,
    "base": {},
    "change": {
      "readReplicaCount": 1
    },
    "changeLabel": "Read Replica 0 → 1대",
    "metrics": [
      "dbReadLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
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
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "용량을 두 배로 늘려도 4000%가 2000%가 될 뿐이다",
    "body": "상품 조회 장애에서 DB 읽기는 hit ratio 하락(miss 증가)과 동시 miss의 중복 조회(×10)가 곱해진 결과다. replica는 그 곱을 받아 줄 그릇을 키울 뿐이라, 원인을 고치지 않으면 몇 대를 더해도 포화에서 벗어나지 못한다. 장애 패턴 사전의 '그럴듯하지만 틀린 대응'이 바로 이것이다."
  },
  {
    "type": "numbers",
    "title": "원인을 고친 뒤에 더 늘리면",
    "domain": "product-browsing",
    "incident": true,
    "base": {
      "singleFlightEnabled": true,
      "cachePolicySplit": true,
      "readReplicaCount": 1
    },
    "change": {
      "readReplicaCount": 3
    },
    "changeLabel": "Single-flight·정책 분리 상태에서 replica 1 → 3대",
    "metrics": [
      "dbReadLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
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
    ]
  },
  {
    "type": "text",
    "body": "Single-flight와 캐시 정책 분리로 원인을 고치면 replica 1대로 DB 읽기가 50%까지 내려와 P95가 평시로 돌아온다. 거기서 2대를 더하면 사용률은 25%로 더 떨어지지만 사용자 지표는 그대로다 — **수확 체감**. 이때부터 replica는 다음 폭주에 대비한 여유분이지 지금의 해결책이 아니다.\n\n- 엔진 밖의 위험: 복제 지연. replica가 늘수록 \"방금 바꾼 가격이 안 보인다\"는 문의가 늘 수 있다. 이것은 **캐시 정책 분리**에서 가격을 짧은 TTL·쓰기 시 무효화로 따로 다루는 이유와 같은 문제다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "복제본마다 비용이 들고, 복제 지연 때문에 어떤 읽기를 주 DB로 보낼지 정하는 규칙이 생긴다. 쓰기가 병목이라면 replica는 아무 도움이 되지 않는다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_READ_REPLICA';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 원인은 캐시 정책인데, 먼저 터지는 것은 DB다.",
    "alt": "트래픽 20배가 상위 상품에 몰리고, 한 정책을 쓰는 캐시의 hit ratio가 떨어져 miss가 늘고, 동시 miss가 열 번씩 DB를 조회해 DB 읽기가 포화되고, 그 결과 API 지연과 504 에러가 사용자에게 보인다",
    "mermaid": "flowchart LR\n  T[\"트래픽 20배\\n상위 상품 집중\"] --> C[\"캐시\\n정책 하나\"]\n  C -- \"hit 90%→20%\" --> M[\"miss 급증\"]\n  M -- \"동시 miss ×10\\n(stampede)\" --> D[(\"DB 읽기\\n포화\")]\n  D --> A[\"API\\nP95↑·504\"]\n  A --> U[\"사용자\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "product-browsing",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 2,
    "metrics": [
      "cacheHitRatio",
      "dbReadLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "alerts": [
      {
        "metric": "dbReadLoad",
        "op": "ABOVE",
        "threshold": 0.8,
        "label": "DB 읽기 ≥ 80%"
      },
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.01,
        "label": "에러율 ≥ 1%"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 150,
        "label": "P95 ≥ 150ms"
      },
      {
        "metric": "cacheHitRatio",
        "op": "BELOW",
        "threshold": 0.7,
        "label": "hit ratio < 70%"
      }
    ],
    "scenarios": [
      {
        "label": "Read Replica만 두 대 추가",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "ADD_READ_REPLICA"
          },
          {
            "second": 150,
            "action": "ADD_READ_REPLICA"
          }
        ],
        "claims": [
          {
            "metric": "cacheHitRatio",
            "direction": "SAME"
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
        ]
      },
      {
        "label": "Single-flight → replica 1대 → 정책 분리",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "ENABLE_SINGLE_FLIGHT"
          },
          {
            "second": 150,
            "action": "ADD_READ_REPLICA"
          },
          {
            "second": 180,
            "action": "SPLIT_CACHE_POLICY"
          }
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
            "direction": "DOWN"
          },
          {
            "metric": "errorRate",
            "direction": "DOWN"
          }
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤(장애가 최대치에 이른 다음)부터 시작한다. replica만 늘리면 DB 사용률 선은 내려가지만 지연·에러 선은 움직이지 않는다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "경보가 울리는 순서는 원인의 순서가 아니다",
    "body": "원인은 hit ratio 하락인데, 경보는 DB 읽기 → 에러율 → P95 → hit ratio 순으로 울린다. miss 하나가 열 번의 DB 조회로 번지기 때문에 결과 쪽 지표가 먼저, 더 크게 움직인다. **가장 먼저 울린 경보를 원인으로 단정하면** DB 증설(replica)로 손이 간다 — 위 그래프의 빨간 선이 그 결과다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **Single-flight 먼저** — 코드 경로 하나로 DB 읽기를 10분의 1로 줄인다. 아직 포화지만 다음 조치가 들을 여지를 만든다.\n- **replica 1대** — 남은 부하를 반으로 나눈다. 여기까지도 사용자 지표는 그대로다.\n- **캐시 정책 분리** — miss 자체를 줄여 DB 사용률이 50%로 내려오고 지연·에러가 평시로 돌아온다.\n\n정책 분리를 replica보다 먼저 하면 DB 사용률이 정확히 100% 근처에 머무는 구간이 생긴다. 어느 순서든 마지막 상태는 같지만, 회복 중에 지표가 오락가락하면 대응하는 사람이 판단을 잘못하기 쉽다 — 상품 조회 설계 가이드가 이 순서를 단계별로 따라간다."
  }
]$blocks$::jsonb
where domain = 'product-browsing';
