-- docs/LEARNING_DEEPENING_PLAN.md L14 (PLAN.md Round E35) — 도메인 설계 가이드. 파일럿: 대규모 상품 조회.
--
-- steps: 상황 → 신호 → 진단 → 조치(값 조정 TUNE / 설계 변경 REDESIGN) → 결과와 대가 → 다음 단계.
-- 단계는 시작 설계(traits)와 바꾸는 값(action.change)만 담는다 — 전/후 수치는 읽을 때 규칙 엔진이
-- 계산하고, claims는 DesignGuideTest가 엔진과 대조한다(ADR-0054). 단계 i+1의 traits는 단계 i의
-- traits + change와 같아야 한다(같은 테스트가 확인).
--
-- 단계 순서는 엔진 값으로 정했다: Single-flight(4000%→400%, 지연·에러 그대로) → replica 1대(→200%,
-- 여전히 그대로 — 용량만으로는 부족) → 캐시 정책 분리(hit 20%→80%, →50%, P95 480→60ms) → replica 3대
-- (→25%, 사용자 지표 그대로 — 수확 체감). 정책 분리를 2단계에 두면 DB 사용률이 정확히 100% 경계에
-- 걸려 지연 구간이 부동소수점 반올림에 좌우된다.

create table design_guides (
    domain        varchar(64) primary key,
    title         varchar(200) not null,
    summary       text not null,
    display_order integer not null default 0,
    requirements  jsonb not null default '[]'::jsonb,
    architecture  jsonb not null default '[]'::jsonb,
    steps         jsonb not null default '[]'::jsonb,
    checklist     jsonb not null default '[]'::jsonb
);

insert into design_guides (domain, title, summary, display_order, requirements, architecture, steps, checklist)
values (
    'product-browsing',
    '대규모 상품 조회',
    '트래픽 20배가 상위 상품에 몰리는 이벤트. 캐시가 무너질 때 무엇을 먼저 고치고, 값 조정과 설계 변경 중 무엇이 실제로 듣는지를 단계별로 따라갑니다.',
    3,
    $g$[
  {
    "type": "text",
    "body": "상품 상세 페이지는 **가격·재고·리뷰**를 보여 준다. 셋 다 읽기만 많고 쓰기는 드물지만, 신선도 요구가 다르다.\n\n- **가격·재고** — 틀리면 주문이 잘못된다. 오래된 값을 보여 주면 안 된다.\n- **리뷰** — 몇 분 늦게 보여도 문제없다.\n- 평소에는 초당 수백 건이지만, 이벤트가 시작되면 트래픽이 **20배**로 뛰고 그중 대부분이 상위 몇 개 상품에 몰린다."
  },
  {
    "type": "system",
    "domain": "product-browsing",
    "incident": false,
    "traits": {},
    "caption": "평시 — 캐시가 대부분을 받아 DB는 한가하다."
  },
  {
    "type": "system",
    "domain": "product-browsing",
    "incident": true,
    "traits": {},
    "caption": "이벤트 시작 — 같은 설계 그대로. 캐시 hit이 무너지고 DB가 감당할 수 있는 양의 수십 배가 몰린다."
  },
  {
    "type": "callout",
    "tone": "tip",
    "title": "무엇을 보장하고 무엇을 포기할 것인가",
    "body": "가격·재고의 정확성은 포기하지 않는다. 대신 리뷰의 신선도는 포기할 수 있다 — 이 차이가 3단계의 설계 변경으로 이어진다."
  }
]$g$::jsonb,
    $g$[
  {
    "type": "diagram",
    "caption": "읽기는 캐시를 먼저 보고(cache-aside), 없을 때만 DB로 간다. 쓰기는 primary에만, replica는 복제로 따라온다.",
    "alt": "사용자가 상품 API를 호출하면 API가 캐시를 먼저 조회하고, miss일 때 DB(primary 또는 read replica)를 읽는다. 상품 관리 시스템은 primary에 쓰고 replica로 복제된다.",
    "mermaid": "flowchart LR\n  U[\"사용자\"] --> API[\"상품 API\"]\n  API -- \"1. 조회\" --> C[(\"캐시\")]\n  API -. \"2. miss일 때\" .-> R[(\"read replica\")]\n  API -. \"2. miss일 때\" .-> P[(\"DB primary\")]\n  ADM[\"상품 관리\"] -- \"쓰기\" --> P\n  P -- \"복제\" --> R"
  },
  {
    "type": "steps",
    "title": "요청 흐름",
    "items": [
      {
        "title": "캐시 조회",
        "body": "상품 ID로 캐시를 본다. 있으면(hit) 바로 응답한다 — 평시에는 대부분이 여기서 끝난다."
      },
      {
        "title": "miss면 DB 조회",
        "body": "없으면 DB에서 읽어 캐시에 넣고 응답한다. 읽기 전용 조회는 replica가 받을 수 있다."
      },
      {
        "title": "쓰기는 primary로",
        "body": "가격 변경 같은 쓰기는 primary에만 하고, 캐시의 해당 키를 지운다(무효화)."
      }
    ]
  }
]$g$::jsonb,
    $g$[
  {
    "title": "이벤트 시작 — DB가 쏟아지는 조회에 무너진다",
    "situation": "이벤트가 시작되자 트래픽이 20배로 뛰었고, 대부분이 상위 몇 개 상품에 몰린다.",
    "incident": true,
    "traits": {},
    "signal": "캐시 hit ratio가 급락하고, DB 읽기 사용률이 감당 가능한 양의 수십 배로 치솟는다. 지연과 에러가 함께 오른다.",
    "diagnosis": "hot key가 만료되는 순간 같은 상품에 대한 miss가 동시에 몰리고, 그 miss 하나하나가 각자 DB를 조회한다 — Cache Stampede다. miss가 DB 조회 여러 번으로 증폭되고 있다.",
    "concepts": [
      "MISSING_SINGLE_FLIGHT",
      "MISSING_OBSERVABILITY"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "Single-flight 도입",
      "change": {
        "singleFlightEnabled": true
      },
      "why": "같은 키의 동시 miss를 한 번의 DB 조회로 합쳐 증폭을 없앤다."
    },
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
    ],
    "tradeoff": "증폭은 사라졌지만 DB는 여전히 감당량을 넘는다 — 지연·에러가 그대로인 이유다. Single-flight는 서버마다 따로 동작하고, 기다리는 요청의 지연이 로더 한 번에 묶인다.",
    "blocks": [
      {
        "type": "compare",
        "caption": "miss 하나가 DB 조회 몇 번이 되는가.",
        "before": {
          "label": "지금",
          "alt": "hot key의 동시 miss가 각자 DB를 조회해 조회 수가 몇 배로 늘어난다",
          "mermaid": "flowchart LR\n  M[\"같은 키의 동시 miss\"] -- \"각자 조회\" --> D[(\"DB\")]"
        },
        "after": {
          "label": "Single-flight",
          "alt": "동시 miss 중 하나만 DB를 조회하고 나머지는 결과를 기다린다",
          "mermaid": "flowchart LR\n  M[\"같은 키의 동시 miss\"] --> L[\"로더 1개\"] -- \"조회 1번\" --> D[(\"DB\")]"
        }
      }
    ],
    "links": {
      "labs": [
        "engine-cache-stampede"
      ],
      "challenges": [
        "cache"
      ],
      "failurePattern": "product-browsing"
    }
  },
  {
    "title": "용량을 늘려 본다 — read replica 1대",
    "situation": "증폭은 잡았지만 DB가 여전히 포화다. 가장 손쉬운 방법은 읽기 용량을 더하는 것이다.",
    "incident": true,
    "traits": {
      "singleFlightEnabled": true
    },
    "signal": "DB 읽기 사용률이 여전히 100%를 크게 넘는다. 지연·에러는 1단계 이후 그대로다.",
    "diagnosis": "miss 자체가 너무 많다 — 캐시 hit ratio가 낮은 채로는, 남은 miss를 받을 용량이 수요를 따라가지 못한다.",
    "concepts": [
      "MISSING_READ_REPLICA"
    ],
    "action": {
      "kind": "TUNE",
      "label": "read replica 0 → 1대",
      "change": {
        "readReplicaCount": 1
      },
      "why": "읽기 전용 조회를 replica로 나눠 DB 읽기 용량을 두 배로."
    },
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
    ],
    "tradeoff": "사용률은 절반이 됐지만 여전히 100%를 넘는다 — 사용자가 겪는 지연과 에러는 변하지 않는다. 용량만으로 정상 범위까지 끌어내리려면 replica가 몇 배로 필요하고, 그만큼 비용과 복제 지연 관리가 늘어난다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "tip",
        "title": "랩에서 직접 찾아보기",
        "body": "이 상태에서 replica만 늘려 P95를 정상으로 돌리려면 몇 대가 필요할까? 아래 랩 링크는 지금 단계의 설정으로 열린다."
      }
    ],
    "links": {
      "labs": [
        "engine-cache-stampede"
      ]
    }
  },
  {
    "title": "miss 자체를 줄인다 — 캐시 정책 분리",
    "situation": "용량을 더해도 부족하다. 문제의 근원은 hot key 집중에 캐시가 무너져 miss가 80%에 이른다는 것이다.",
    "incident": true,
    "traits": {
      "singleFlightEnabled": true,
      "readReplicaCount": 1
    },
    "signal": "캐시 hit ratio가 20% 근처에 머문다 — 요청 다섯 중 넷이 DB로 간다.",
    "diagnosis": "가격·재고·리뷰를 한 키, 한 정책으로 캐시하고 있다. 가격 때문에 짧게 잡은 정책이 리뷰까지 자주 비우고, 몰린 키 하나가 비면 모든 데이터가 함께 miss가 된다.",
    "concepts": [
      "MISSING_CACHE_POLICY_SEPARATION"
    ],
    "action": {
      "kind": "REDESIGN",
      "label": "데이터별 캐시 정책 분리",
      "change": {
        "cachePolicySplit": true
      },
      "why": "신선도 요구가 다른 데이터를 다른 키·다른 정책으로 — 가격·재고는 짧게(또는 쓰기 시 무효화), 리뷰는 길게."
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
        "direction": "DOWN"
      },
      {
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ],
    "tradeoff": "키가 둘로 나뉘어 조회가 두 번이 되고, 무효화 규칙도 데이터마다 따로 관리해야 한다. 그래도 가격의 정확성을 지키면서 hit ratio를 되찾는 유일한 길이다.",
    "blocks": [
      {
        "type": "diagram",
        "caption": "한 상품을 신선도가 다른 두 키로 나눈다.",
        "alt": "상품 키 하나를 가격·재고 키(짧은 TTL, 쓰기 시 무효화)와 리뷰 키(긴 TTL)로 나눈다",
        "mermaid": "flowchart LR\n  P[\"상품 42\"] --> K1[\"product:42:price\\n짧게 · 쓰기 시 무효화\"]\n  P --> K2[\"product:42:reviews\\n길게 (수 분)\"]"
      },
      {
        "type": "callout",
        "tone": "warning",
        "title": "TTL을 일괄로 늘리면 안 되나?",
        "body": "모든 데이터의 캐시를 오래 두면 hit ratio는 오르지만 가격이 틀린 채로 보인다 — 1단계에서 포기하지 않기로 한 것을 포기하게 된다. 그래서 정책을 나눈다."
      }
    ],
    "links": {
      "labs": [
        "engine-cache-stampede"
      ]
    }
  },
  {
    "title": "더 안전하게? — replica를 3대로",
    "situation": "장애는 진정됐다. 다음 이벤트에 대비해 replica를 3대로 늘리자는 제안이 나왔다.",
    "incident": true,
    "traits": {
      "singleFlightEnabled": true,
      "readReplicaCount": 1,
      "cachePolicySplit": true
    },
    "signal": "DB 읽기 사용률은 이미 여유 구간이고, 지연·에러는 정상이다.",
    "diagnosis": "병목이 더 이상 DB 읽기 용량에 있지 않다 — 여기서 용량을 더해도 사용자가 겪는 지표는 바뀌지 않는다.",
    "concepts": [
      "MISSING_READ_REPLICA",
      "MISSING_KEY_DISTRIBUTION"
    ],
    "action": {
      "kind": "TUNE",
      "label": "read replica 1 → 3대",
      "change": {
        "readReplicaCount": 3
      },
      "why": "다음 이벤트의 트래픽 증가분에 대비한 여유."
    },
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
    ],
    "tradeoff": "사용률은 더 내려가지만 지연·에러는 그대로다 — 비용과 복제 지연을 관리할 대상만 늘었다. 여유가 필요하다면 근거가 되는 숫자(다음 이벤트의 예상 배율)를 먼저 정한다.",
    "blocks": [
      {
        "type": "callout",
        "tone": "warning",
        "title": "다음 위험은 엔진 밖에 있다",
        "body": "이 시뮬레이션은 캐시를 하나로 본다. 실제로는 캐시도 여러 노드로 나뉘고, **hot key 하나는 늘 같은 노드로 간다** — 그 노드 하나가 먼저 포화된다. 키를 노드에 어떻게 나눌지(Hot Key 분산, Consistent Hashing)와 replica의 **복제 지연**(방금 바뀐 가격이 replica에는 아직 없음)이 다음에 다룰 위험이다."
      }
    ],
    "links": {
      "labs": [
        "engine-cache-stampede"
      ],
      "challenges": [
        "consistent-hashing"
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
    "item": "데이터별 캐시 정책 분리 (가격 vs 리뷰)",
    "where": "step-3"
  },
  {
    "item": "hot key 분산 전략",
    "where": "step-4"
  },
  {
    "item": "single-flight/lock — 동시 cache miss 중복 요청 방지",
    "where": "step-1"
  },
  {
    "item": "read replica를 통한 읽기 확장",
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
