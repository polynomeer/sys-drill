-- docs/LEARNING_DEEPENING_PLAN.md L12 (PLAN.md Round E34) — 개념·장애 패턴의 확장 콘텐츠 블록.
--
-- blocks: ContentBlock(learning/ContentBlocks.kt)의 JSON 배열 — text / diagram(Mermaid) / steps /
-- callout / compare / numbers / system. numbers·system 블록은 수치를 저장하지 않고 도메인·인시던트
-- 여부·DesignTraits 덮어쓰기만 담는다 — 값은 읽을 때 규칙 엔진이 계산한다. numbers 블록의 claims는
-- 본문이 주장하는 방향이고, ContentBlocksTest가 엔진 결과와 대조한다.
--
-- 첫 콘텐츠로 Single-flight에 모든 블록 종류를 한 번씩 넣는다(렌더러 확인용이자 L13 파일럿의 첫 개념).

alter table learning_concepts add column blocks jsonb not null default '[]'::jsonb;
alter table failure_patterns add column blocks jsonb not null default '[]'::jsonb;

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 순간 같은 키로 8개 요청이 몰렸을 때 — 차이는 DB까지 가는 조회 수다.",
    "before": {
      "label": "Single-flight 없음",
      "body": "캐시가 비는 순간 8개 요청이 모두 miss를 보고 각자 DB를 조회한다.",
      "alt": "동시 요청 8개가 캐시 miss를 거쳐 DB를 8번 조회한다",
      "mermaid": "flowchart LR\n  A[\"동시 요청 8개\"] --> C{\"캐시 miss\\n(hot key 만료)\"}\n  C -- \"조회 8번\" --> D[(\"DB\")]"
    },
    "after": {
      "label": "Single-flight",
      "body": "첫 요청만 DB에 가고 나머지 7개는 그 결과를 기다렸다가 함께 받는다.",
      "alt": "동시 요청 8개 중 첫 요청만 로더를 통해 DB를 1번 조회하고, 나머지는 결과를 공유받는다",
      "mermaid": "flowchart LR\n  A[\"동시 요청 8개\"] --> C{\"캐시 miss\"}\n  C -- \"첫 요청\" --> L[\"로더 1개\"]\n  C -. \"나머지 7개 대기\" .-> L\n  L -- \"조회 1번\" --> D[(\"DB\")]\n  L -- \"결과 공유 + 캐시 저장\" --> A"
    }
  },
  {
    "type": "steps",
    "title": "어떻게 동작하나",
    "items": [
      {
        "title": "첫 miss가 '로딩 중' 표시를 남긴다",
        "body": "키마다 진행 중인 로드(in-flight)를 기록하고, 그 요청만 원본(DB)을 조회한다."
      },
      {
        "title": "뒤따른 miss는 기다린다",
        "body": "같은 키에 표시가 있으면 DB로 가지 않고 진행 중인 로드가 끝나기를 기다린다."
      },
      {
        "title": "결과를 한 번에 나눈다",
        "body": "로드가 끝나면 값을 캐시에 넣고, 기다리던 요청 모두에게 같은 값을 돌려준다."
      },
      {
        "title": "실패는 굳히지 않는다",
        "body": "로드가 실패하면 표시를 지워 다음 요청이 다시 시도하게 한다 — 실패를 공유된 결과로 캐시하지 않는다."
      }
    ]
  },
  {
    "type": "system",
    "domain": "product-browsing",
    "incident": true,
    "traits": {},
    "caption": "상품 조회 — 트래픽 20배, 상위 상품에 몰림. Single-flight가 없으면 miss 하나가 DB 조회 여러 번으로 번진다."
  },
  {
    "type": "numbers",
    "title": "Single-flight만 켰을 때",
    "domain": "product-browsing",
    "incident": true,
    "base": {},
    "change": {
      "singleFlightEnabled": true
    },
    "changeLabel": "Single-flight 켜기",
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
    "title": "DB 부하는 줄었는데 지연·에러는 그대로다",
    "body": "중복 조회가 사라져 DB 읽기 사용률은 크게 떨어지지만, 여전히 100%를 넘는다 — 그래서 사용자가 겪는 지연과 에러는 변하지 않는다. Single-flight는 같은 키의 **중복**만 없앨 뿐, miss 자체를 줄이지는 않는다."
  },
  {
    "type": "numbers",
    "title": "miss 자체를 줄이고 읽기 용량을 더하면",
    "domain": "product-browsing",
    "incident": true,
    "base": {
      "singleFlightEnabled": true
    },
    "change": {
      "cachePolicySplit": true,
      "readReplicaCount": 1
    },
    "changeLabel": "캐시 정책 분리 + read replica 1대",
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
    ]
  },
  {
    "type": "text",
    "body": "Single-flight는 **캐시 정책 분리**(miss를 덜 나게)와 **Read Replica**(남은 miss를 받을 용량)와 함께 쓸 때 효과가 난다. 셋의 역할이 다르다 — 중복 제거, miss 감소, 용량 확장.\n\n- 서버가 여러 대면 Single-flight는 서버마다 따로 동작한다. 서버 N대면 같은 키의 조회도 최대 N번이다.\n- 그것도 막아야 하면 분산 락이나 캐시 앞단의 요청 병합(coalescing)이 필요하다 — 대신 락 서버가 새 의존성이 된다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "기다리는 요청의 지연이 로더 한 번의 지연에 묶인다. 로더가 느리거나 멈추면 그 키를 기다리는 모든 요청이 함께 느려지므로, 로드에는 반드시 타임아웃을 둔다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_SINGLE_FLIGHT';
