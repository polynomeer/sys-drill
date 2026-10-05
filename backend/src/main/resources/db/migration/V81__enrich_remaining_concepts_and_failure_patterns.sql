-- docs/LEARNING_DEEPENING_PLAN.md L13 (PLAN.md Round E38) — 나머지 개념 23개와 장애 패턴 7개의 확장 블록.
--
-- 형식은 V80 파일럿 그대로. 개념: compare(문제 vs 해결 구조) → steps → numbers(엔진이 모델링하는 개념만,
-- claims는 ContentBlocksTest가 대조) 또는 "엔진 밖" 안내 → 다른 개념과의 관계 → 대가. 장애 패턴: 전파
-- 경로 diagram → timeline(엔진 시계로 방치·Bad Fix·올바른 완화, 경보가 처음 울리는 순서) → 교훈 callout →
-- 완화 순서의 이유. 초안은 실제 엔진으로 블록을 해석하는 검사기로 확인한 뒤 옮겼다.

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 요청이 두 번 도착하는 것은 막을 수 없다 — 막을 수 있는 것은 두 번 반영되는 것이다.",
    "before": {
      "label": "재요청마다 새로 처리",
      "body": "응답을 못 받은 클라이언트가 같은 요청을 다시 보내면, 서버는 그것을 새 요청으로 보고 한 번 더 쓴다.",
      "alt": "클라이언트의 첫 요청은 처리됐지만 응답이 유실되고, 재요청이 다시 처리되어 DB에 같은 기록이 두 번 남는다",
      "mermaid": "flowchart LR\n  C[\"클라이언트\"] -- \"요청 1 (응답 유실)\" --> S[\"서버\"]\n  C -- \"재요청\" --> S\n  S -- \"기록 ×2\" --> D[(\"DB\")]"
    },
    "after": {
      "label": "멱등키로 결과 재사용",
      "body": "요청에 멱등키를 싣고, 처음 처리할 때 키와 결과를 함께 저장한다. 같은 키가 다시 오면 처리하지 않고 저장된 결과를 돌려준다.",
      "alt": "재요청이 오면 서버가 멱등키 저장소를 먼저 확인하고, 이미 처리된 키면 저장된 결과를 반환해 DB에는 기록이 한 번만 남는다",
      "mermaid": "flowchart LR\n  C[\"클라이언트\\nkey=abc\"] -- \"요청·재요청\" --> S[\"서버\"]\n  S --> K{\"key=abc\\n처리했나?\"}\n  K -- \"처음\" --> D[(\"DB\\n기록 1번\")]\n  K -- \"이미 처리\" --> R[\"저장된 결과 반환\"]"
    }
  },
  {
    "type": "steps",
    "title": "멱등하게 만드는 순서",
    "items": [
      {
        "title": "무엇이 '같은 요청'인지 정한다",
        "body": "클라이언트가 만든 멱등키, 주문 ID 같은 비즈니스 키, 큐 메시지 ID 중 하나를 고른다. 요청마다 새로 만드는 키는 재시도를 구별하지 못해 쓸모가 없다."
      },
      {
        "title": "키 선점과 처리를 원자적으로",
        "body": "`INSERT ... ON CONFLICT DO NOTHING`이나 유니크 제약으로 키를 먼저 잡는다. '조회 후 없으면 저장'으로 나누면 동시에 온 두 요청이 모두 통과한다 — 동시성 제어 문제가 그대로 들어온다."
      },
      {
        "title": "처리 중인 키를 다룬다",
        "body": "첫 요청이 아직 끝나지 않았을 때 재요청이 오면 '처리 중'으로 응답하거나 기다리게 한다. 끝나면 결과를 키에 붙여 둔다."
      },
      {
        "title": "보존 기간을 재시도 기간보다 길게",
        "body": "키 TTL이 클라이언트·큐의 최대 재시도 기간보다 짧으면 늦게 온 재시도가 새 요청으로 통과한다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "재처리를 멱등하게 만들면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {},
    "change": {
      "idempotentReconciliationEnabled": true
    },
    "changeLabel": "정산 재처리를 멱등하게",
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
    ]
  },
  {
    "type": "text",
    "body": "정산 배치가 60% 지점에서 끊긴 뒤 처음부터 다시 돌면, 이미 반영한 60만 건이 한 번 더 반영된다 — 엔진의 정산 장애에서 에러율은 요청 실패가 아니라 **중복 반영된 레코드 비율**이고, 그것이 60%다. 재처리를 멱등하게 만들면 이 비율이 0이 된다.\n\n- 그런데 다시 처리해야 할 레코드 수와 처리량은 그대로다. 멱등성은 다시 하는 일을 **안전하게** 만들 뿐 **줄여 주지는** 않는다. 줄이는 것은 재시작 가능성(체크포인트)의 몫이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **Retry/Backoff**는 멱등성 위에서만 안전하다. 멱등하지 않은 작업에 백오프 재시도를 붙이면 중복을 더 정중하게 만들 뿐이다.\n- **결제 멱등성**과 **Idempotent Consumer**는 이 원리를 PG 호출과 큐 컨슈머에 적용한 것이다. 결제에서는 멱등성이 없을 때 중복뿐 아니라 일 자체가 부풀어, 엔진에서 커넥션 풀 포화로까지 나타난다.\n- **동시성 제어**와 짝이다. 키 선점을 원자적으로 하지 않으면 동시에 도착한 같은 요청 두 개가 모두 '처음'으로 판정된다.\n- **재시작 가능성**은 다시 할 양을 줄이고, 멱등성은 다시 해도 결과가 같게 한다. Build의 idempotency 과제가 키 저장소를 직접 만든다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "멱등키 저장소가 요청 경로의 새 의존성이 된다 — 그 저장소가 느리거나 죽으면 모든 쓰기가 영향을 받는다. 키를 언제 지울지 정해야 하고, 그 기간이 지나 도착한 재시도는 막지 못한다. 같은 키로 내용이 다른 요청이 오는 경우(클라이언트 버그)도 거절 규칙이 필요하다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_IDEMPOTENCY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "'남았나?'를 읽고 '가져간다'를 쓰는 사이가 경쟁 구간이다. 그 구간을 없애거나 좁힌다.",
    "before": {
      "label": "읽고-판단하고-쓰기",
      "body": "두 요청이 같은 순간 잔여 1을 읽고, 둘 다 '있다'고 판단해 각자 차감한다. 잔여가 -1이 되거나 한 좌석이 두 번 팔린다.",
      "alt": "요청 A와 B가 동시에 재고 1을 읽고, 둘 다 있다고 판단한 뒤 각자 차감해 재고가 음수가 된다",
      "mermaid": "flowchart LR\n  A[\"요청 A\"] -- \"읽기: 1\" --> S[(\"재고 = 1\")]\n  B[\"요청 B\"] -- \"읽기: 1\" --> S\n  A -- \"쓰기: 0\" --> S\n  B -- \"쓰기: -1\" --> S"
    },
    "after": {
      "label": "조건부 갱신 하나로",
      "body": "확인과 차감을 한 문장으로 합친다. DB가 그 행을 한 번에 하나씩 처리하므로 진 쪽은 '0행 갱신'을 받고 실패로 끝난다.",
      "alt": "요청 A와 B가 모두 재고가 0보다 클 때만 차감하는 조건부 UPDATE를 보내고, A만 1행 갱신에 성공하고 B는 0행 갱신으로 실패한다",
      "mermaid": "flowchart LR\n  A[\"요청 A\"] --> U[\"UPDATE stock = stock - 1\\nWHERE stock > 0\"]\n  B[\"요청 B\"] --> U\n  U -- \"A: 1행 성공\" --> S[(\"재고 = 0\")]\n  U -. \"B: 0행 → 매진\" .-> B"
    }
  },
  {
    "type": "steps",
    "title": "방법을 고르는 순서",
    "items": [
      {
        "title": "한 문장으로 끝낼 수 있나",
        "body": "조건부 UPDATE, Redis `DECR`·Lua처럼 확인과 쓰기를 원자적 연산 하나로 합칠 수 있으면 락이 필요 없다. 가장 먼저 시도한다."
      },
      {
        "title": "충돌이 드물면 낙관적 락",
        "body": "버전 컬럼을 읽어 두고 `WHERE version = ?`로 쓴다. 실패하면 다시 읽어 재시도한다. 충돌이 잦으면 재시도가 부하가 된다."
      },
      {
        "title": "충돌이 잦고 여러 단계면 비관적 락",
        "body": "`SELECT ... FOR UPDATE`나 분산 락으로 구간 전체를 잡는다. 이때 락의 **범위**가 곧 처리 용량이다 — 공연 전체가 아니라 좌석 하나를 잡는다."
      },
      {
        "title": "인스턴스가 여럿이면 락도 공유돼야 한다",
        "body": "애플리케이션 메모리 락(`synchronized`, mutex)은 그 프로세스 안에서만 유효하다. 서버가 두 대면 DB나 Redis 같은 공유 지점에서 잡아야 한다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "락만 좌석 단위로 쪼개면",
    "domain": "reservation",
    "incident": true,
    "base": {},
    "change": {
      "fineGrainedLockingEnabled": true
    },
    "changeLabel": "공연 단위 락 → 좌석 단위 락",
    "metrics": [
      "consumerThroughput",
      "dbWriteLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
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
    ]
  },
  {
    "type": "numbers",
    "title": "확인과 확정까지 원자적으로 하면",
    "domain": "reservation",
    "incident": true,
    "base": {
      "fineGrainedLockingEnabled": true
    },
    "change": {
      "atomicInventoryCheckEnabled": true
    },
    "changeLabel": "좌석 단위 락 상태에서 재고 확인·확정을 원자적으로",
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
    "body": "좌석 단위 락으로 처리 용량은 20배가 되지만 사용률은 여전히 100%를 넘는다. 확인과 확정이 따로라 경쟁에서 진 요청들이 재시도하면서 **들어오는 일 자체가 세 배**로 불어나 있기 때문이다. 원자적 확인을 더하면 그 낭비가 사라져 사용률이 75%로 내려오고, 지연·에러가 크게 준다.\n\n- 예약 설계 가이드는 홀드 타임아웃부터 줄이고 원자적 확인을 마지막에 둔다. 여기서 보듯 원자적 확인을 먼저 해도 숫자가 움직인다 — 동시성 제어는 정합성뿐 아니라 경쟁 낭비라는 **부하** 문제이기도 하다.\n- 엔진 밖의 위험: 엔진은 경쟁의 재시도 낭비만 계산하고, 실제로 재고가 음수가 되거나 한 좌석이 두 번 팔리는 사고는 숫자로 나타내지 않는다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **예약 락**은 이 개념에서 '락의 범위'만 떼어 낸 것이고, **재고 정합성**은 '확인과 확정의 원자성'을 떼어 낸 것이다. 위 두 수치가 각각에 해당한다.\n- **멱등성 처리**는 같은 요청의 반복을, 동시성 제어는 다른 요청끼리의 경쟁을 다룬다. 멱등키 선점 자체도 원자적이어야 해서 둘은 늘 함께 쓰인다.\n- **예약 타임아웃**은 락이 아니라 홀드가 용량을 붙잡는 문제다. 락을 잘게 쪼개도 유령 홀드가 남으면 용량은 돌아오지 않는다.\n- Build의 distributed-lock 과제가 여러 인스턴스가 공유하는 락과 그 만료를 직접 만든다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "락을 좁힐수록 동시 처리량은 오르지만, 여러 자원을 한 번에 잡는 요청에서는 획득 순서를 정하지 않으면 교착이 생긴다. 원자적 연산은 그 자원이 있는 한곳(DB 행, Redis 키)에 묶여 확장 방식이 제한된다. 낙관적 락은 충돌이 잦으면 재시도가 부하로 돌아온다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_CONCURRENCY_CONTROL';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "큐는 '최소 한 번' 배달한다. 정확히 한 번은 컨슈머가 만든다.",
    "before": {
      "label": "받으면 그냥 처리",
      "body": "컨슈머가 처리 후 ack를 보내기 전에 죽거나 타임아웃이 나면, 브로커는 같은 메시지를 다시 배달하고 컨슈머는 또 처리한다.",
      "alt": "브로커가 메시지 m-7을 배달하고 컨슈머가 처리했지만 ack가 유실되어, 브로커가 재배달하고 알림이 두 번 발송된다",
      "mermaid": "flowchart LR\n  Q[\"브로커\"] -- \"m-7\" --> W[\"컨슈머\"]\n  W -. \"ack 유실\" .-> Q\n  Q -- \"m-7 재배달\" --> W\n  W -- \"발송 ×2\" --> U[\"사용자\"]"
    },
    "after": {
      "label": "처리 기록으로 걸러내기",
      "body": "메시지 ID를 처리 기록 테이블에 유니크 제약으로 남기고, 그 기록과 비즈니스 쓰기를 같은 트랜잭션으로 한다. 재배달된 메시지는 기록 삽입에서 막혀 건너뛴다.",
      "alt": "컨슈머가 메시지 ID를 처리 기록에 넣으려 하고, 처음이면 비즈니스 처리와 함께 커밋하며, 이미 있으면 처리 없이 ack만 보낸다",
      "mermaid": "flowchart LR\n  Q[\"브로커\"] -- \"m-7 (재배달 포함)\" --> W[\"컨슈머\"]\n  W --> P{\"처리 기록에\\nm-7 삽입\"}\n  P -- \"성공\" --> B[(\"비즈니스 쓰기\\n같은 트랜잭션\")]\n  P -- \"중복\" --> A[\"건너뛰고 ack\"]"
    }
  },
  {
    "type": "steps",
    "title": "컨슈머를 멱등하게 만드는 방법",
    "items": [
      {
        "title": "처리 자체를 덮어쓰기로 만든다",
        "body": "'잔액 += 100' 대신 '정산 결과 = 100'처럼 upsert로 바꿀 수 있으면 별도 기록 없이 멱등해진다. 가장 싸다."
      },
      {
        "title": "안 되면 처리 기록을 남긴다",
        "body": "메시지 ID나 비즈니스 키를 유니크 제약이 걸린 테이블에 넣는다. 기록과 비즈니스 쓰기는 **같은 트랜잭션**이어야 한다 — 따로 하면 둘 사이에서 죽었을 때 다시 어긋난다."
      },
      {
        "title": "외부 호출은 기록만으로 못 막는다",
        "body": "알림 발송처럼 DB 밖으로 나가는 부작용은 트랜잭션에 묶이지 않는다. 받는 쪽(provider)에 멱등키를 넘기거나, 발송 상태를 먼저 기록하고 확인하는 단계를 둔다."
      },
      {
        "title": "기록의 보존 기간을 정한다",
        "body": "브로커의 최대 재배달 기간과 재처리(리플레이) 범위보다 길게 두고, 그보다 오래된 기록은 지운다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "재시작 지점만 남기면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {},
    "change": {
      "checkpointingEnabled": true
    },
    "changeLabel": "체크포인트로 실패한 청크부터 재개",
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
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      }
    ]
  },
  {
    "type": "numbers",
    "title": "마지막 청크까지 멱등하게",
    "domain": "batch-settlement",
    "incident": true,
    "base": {
      "checkpointingEnabled": true
    },
    "change": {
      "idempotentReconciliationEnabled": true
    },
    "changeLabel": "체크포인트 상태에서 재처리를 멱등하게",
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
    ]
  },
  {
    "type": "text",
    "body": "엔진의 정산 배치는 레코드를 청크 단위로 '받아 처리하는' 컨슈머와 같은 모양이다. 체크포인트로 실패한 청크부터 다시 돌면 중복 반영이 60%에서 1%(청크 하나, 1만 건)로 줄지만 0이 되지는 않는다 — 체크포인트는 '마지막으로 확인된 지점 이후를 다시 한다', 곧 **최소 한 번**이다. 그 마지막 청크까지 중복 없이 만드는 것이 멱등한 처리다.\n\n- 엔진 밖의 위험: 알림 장애의 엔진은 재시도와 backlog를 부하로만 계산하고, 같은 알림이 두 번 가는 중복은 모델링하지 않는다. 알림 컨슈머의 중복 발송은 숫자 없이 위 그림과 방법으로만 다룬다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **멱등성 처리**의 원리를 큐 컨슈머에 적용한 것이다. 키가 클라이언트의 멱등키 대신 메시지 ID나 비즈니스 키일 뿐이다.\n- **재시작 가능성**은 다시 할 양을 줄이고, Idempotent Consumer는 다시 해도 같은 결과를 보장한다. 위 두 수치처럼 둘 다 있어야 중복이 0이 된다.\n- **DLQ**로 빠졌던 메시지를 재처리하거나 **Retry/Backoff**로 다시 시도할 때, 컨슈머가 멱등하지 않으면 그 재처리가 새 사고가 된다.\n- **트랜잭션 경계 분리**의 Outbox는 발행 쪽을 '최소 한 번'으로 만든다. 받는 쪽이 멱등해야 그 구조가 완성된다 — Build의 outbox·queue 과제가 양쪽을 만든다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "처리 기록 테이블이 메시지마다 쓰기 한 번을 더하고, 보존 기간을 넘겨 도착한 중복은 막지 못한다. 기록과 비즈니스 쓰기를 같은 트랜잭션에 묶으려면 둘이 같은 DB에 있어야 해서, 컨슈머의 저장소 선택이 제약을 받는다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_IDEMPOTENT_CONSUMER';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "키를 누가, 언제 만드느냐가 핵심이다 — 시도마다가 아니라 주문마다 하나.",
    "before": {
      "label": "시도마다 새 요청",
      "body": "PG 응답이 끊기면 결제가 됐는지 모른 채 새 결제를 요청한다. 앞선 시도가 실제로 승인됐다면 이중 결제다.",
      "alt": "디스패처가 주문 42의 승인을 요청했으나 응답이 유실되고, 키 없이 다시 요청해 PG에서 승인이 두 건 생긴다",
      "mermaid": "flowchart LR\n  D[\"디스패처\\n주문 42\"] -- \"승인 요청 (응답 유실)\" --> PG[\"PG\"]\n  D -- \"새 승인 요청\" --> PG\n  PG --> X[\"승인 2건\\n이중 결제\"]"
    },
    "after": {
      "label": "주문 단위 멱등키",
      "body": "주문을 만들 때 키를 한 번 정해 outbox에 함께 저장하고, 모든 재시도에 같은 키를 쓴다. PG는 같은 키의 두 번째 요청에 첫 결과를 돌려준다.",
      "alt": "주문 생성 때 멱등키를 outbox에 저장하고, 디스패처가 재시도마다 같은 키를 PG에 보내 PG가 첫 승인 결과를 돌려주며, 결과에 따라 주문 상태를 조건부로 전이한다",
      "mermaid": "flowchart LR\n  O[(\"주문 + outbox\\nkey=ord-42\")] --> D[\"디스패처\"]\n  D -- \"재시도 · 같은 key\" --> PG[\"PG\"]\n  PG -- \"첫 승인 결과\" --> D\n  D -- \"WHERE status='PENDING'\" --> O"
    }
  },
  {
    "type": "steps",
    "title": "결제에 적용하는 순서",
    "items": [
      {
        "title": "키는 주문을 만들 때 정한다",
        "body": "주문 ID에서 유도하거나 주문 행과 함께 저장한다. 디스패처가 호출할 때마다 만들면 재시도가 모두 다른 키를 갖는다 — 장애 패턴 사전의 대표적인 틀린 대응이다."
      },
      {
        "title": "PG에 키를 그대로 넘긴다",
        "body": "PG가 멱등키를 지원하면 헤더로 넘긴다. 지원하지 않거나 보존 기간이 짧으면 재시도 전에 '이 주문의 승인이 있는가'를 먼저 조회한다."
      },
      {
        "title": "상태 전이를 조건부로",
        "body": "`UPDATE payment SET status = 'APPROVED' WHERE order_id = ? AND status = 'PENDING'`. 늦게 도착한 응답이나 중복 응답이 상태를 두 번 바꾸지 못한다."
      },
      {
        "title": "모르는 상태를 남겨 두지 않는다",
        "body": "끝내 응답을 못 받은 건은 '확인 필요'로 두고, PG 거래 내역과 대사해 확정하거나 취소한다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "멱등키 재시도 하나로",
    "domain": "payment",
    "incident": true,
    "base": {},
    "change": {
      "idempotentPgRetryEnabled": true
    },
    "changeLabel": "멱등키를 붙인 PG 재시도 (워커 4개 그대로)",
    "metrics": [
      "queueLag",
      "connectionPoolUsage",
      "p95LatencyMs",
      "errorRate"
    ],
    "claims": [
      {
        "metric": "queueLag",
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
    ]
  },
  {
    "type": "text",
    "body": "엔진의 결제 장애에서 멱등성 없는 재시도는 주문 하나를 시도 네 번으로 부풀린다. 그 부풀린 일이 outbox 적체와 공유 커넥션 풀을 채워, 결제와 무관한 주문 API까지 막는다. 워커를 하나도 늘리지 않고 키만 붙여도 풀이 포화에서 여유 구간으로 내려온다 — 결제에서 멱등성은 정합성 장치이면서 **용량** 장치다.\n\n- 결제 설계 가이드는 워커를 먼저 16개로 늘려 보고(효과 없음) 멱등키를 붙인다. 여기서는 워커 4개 그대로도 대부분이 돌아온다는 것을 보여 준다. 남은 적체를 비우는 것은 그 뒤의 워커 증설이다.\n- 엔진 밖의 위험: 엔진은 재시도를 부하로만 계산하고, 실제 이중 승인 건수는 숫자로 나타내지 않는다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **멱등성 처리**의 가장 비싼 적용처다. 원리는 같지만 키를 PG라는 외부 시스템과 공유해야 해서 PG의 지원 범위에 묶인다.\n- **PG 재시도/백오프**는 멱등성 위에서만 안전하다. 멱등성은 재시도를 안전하게, 백오프는 덜 하게 만든다 — 둘 다 필요하다.\n- **트랜잭션 경계 분리**(Outbox)가 키를 주문과 같은 트랜잭션에 저장할 자리를 준다. 키가 주문과 따로 저장되면 둘 사이에서 다시 어긋난다.\n- **정산 정합성**(대사)이 마지막 안전망이다. 응답이 끝내 유실된 건은 멱등성만으로 확정할 수 없다. Build의 idempotency 과제가 키 저장소를 직접 만든다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "우리 쪽에도 키와 결제 상태를 저장하는 테이블과 조회가 생기고, PG마다 키 지원 범위·보존 기간이 달라 연동마다 규칙을 따로 확인해야 한다. 응답을 못 받은 건을 '확인 필요'로 남기면 사용자에게 결제 결과를 바로 알려 주지 못하는 순간이 생긴다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_PAYMENT_IDEMPOTENCY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 주문·결제 — 차이는 PG 응답을 기다리는 동안 DB 커넥션을 쥐고 있느냐다.",
    "before": {
      "label": "트랜잭션 안에서 PG 호출",
      "body": "주문 저장과 PG 승인을 한 트랜잭션으로 묶으면, PG가 1초 걸리는 동안 커넥션 하나가 아무 일도 안 하고 묶인다. PG가 느려지면 풀이 먼저 바닥난다.",
      "alt": "주문 API가 트랜잭션을 열고 커넥션을 쥔 채 PG 승인을 기다린 뒤에야 커밋해, PG 지연이 그대로 커넥션 점유 시간이 된다",
      "mermaid": "flowchart LR\n  A[\"주문 API\"] --> T[\"트랜잭션 시작\\n커넥션 획득\"]\n  T --> PG[\"PG 승인\\n(느림)\"]\n  PG --> C[\"커밋\\n커넥션 반납\"]\n  T -. \"대기 내내 점유\" .-> P[(\"공유 커넥션 풀\")]"
    },
    "after": {
      "label": "커밋 뒤에 호출(Outbox)",
      "body": "주문과 '결제 요청' 이벤트를 같은 트랜잭션에 짧게 기록하고 바로 커밋한다. PG 호출은 디스패처가 트랜잭션 밖에서 한다.",
      "alt": "주문 API는 주문과 outbox 이벤트를 한 트랜잭션에 기록하고 바로 커밋하며, 디스패처가 outbox를 읽어 트랜잭션 밖에서 PG를 호출하고 결과를 짧은 트랜잭션으로 반영한다",
      "mermaid": "flowchart LR\n  A[\"주문 API\"] --> T[\"짧은 트랜잭션\\n주문 + outbox 기록\"]\n  T --> O[(\"outbox\")]\n  O --> W[\"디스패처\"]\n  W --> PG[\"PG 승인\\n(트랜잭션 밖)\"]\n  PG --> R[\"결과 반영\\n(짧은 트랜잭션)\"]"
    }
  },
  {
    "type": "steps",
    "title": "경계를 긋는 순서",
    "items": [
      {
        "title": "한 트랜잭션에 묶을 수 없는 것을 찾는다",
        "body": "외부 API(PG·발송), 다른 서비스의 DB, 메시지 발행은 우리 DB의 커밋·롤백을 따르지 않는다. 이것들이 경계 바깥이다."
      },
      {
        "title": "트랜잭션은 우리 DB 쓰기만 짧게",
        "body": "트랜잭션 안에는 '무엇을 해야 하는지'만 기록한다(주문 + outbox 이벤트). 네트워크 대기는 커밋 뒤로 옮긴다."
      },
      {
        "title": "바깥 작업은 재시도 가능하게",
        "body": "커밋 뒤 외부 호출은 실패·유실될 수 있다. 같은 요청을 몇 번 보내도 한 번만 반영되도록 멱등성 키를 붙여 다시 보낸다."
      },
      {
        "title": "둘 중 하나만 성공한 창을 닫는다",
        "body": "주문은 있는데 결제가 없거나 그 반대인 상태를 보상 트랜잭션(Saga)으로 되돌리거나, 주기적인 대사로 찾아 맞춘다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "적체를 주문 경로에서 떼어 내면",
    "domain": "payment",
    "incident": true,
    "base": {
      "idempotentPgRetryEnabled": true
    },
    "change": {
      "paymentPoolIsolated": true
    },
    "changeLabel": "멱등 재시도 상태에서 결제 커넥션 풀 격리",
    "metrics": [
      "connectionPoolUsage",
      "p95LatencyMs",
      "errorRate",
      "queueLag"
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
      },
      {
        "metric": "queueLag",
        "direction": "SAME"
      }
    ]
  },
  {
    "type": "text",
    "body": "엔진은 트랜잭션 경계 자체가 아니라 그 **결과**를 계산한다 — PG를 기다리며 쌓인 outbox 적체가 주문 처리와 같은 커넥션 풀을 잡아먹는지 여부다. 결제 쪽 풀을 떼어 내면 주문 경로의 풀 사용률이 평시 수준으로 내려와 P95·에러율이 돌아오지만, outbox 적체 숫자는 그대로다. 경계를 나누는 일은 느린 외부 호출이 **번지는 범위**를 정할 뿐, 느린 호출을 빠르게 만들지는 않는다.\n\n- \"결제는 됐는데 주문이 없다\" 같은 반쪽 성공은 엔진이 세지 않는다. 그 창을 닫는 것은 아래 관계의 몫이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **결제 멱등성**은 경계 밖으로 나간 PG 호출을 안전하게 다시 보내게 해 준다. 경계를 나누면 재시도가 생기고, 멱등성 없는 재시도는 이중 결제가 된다 — 위 수치의 기준 상태가 멱등 재시도를 켠 상태인 이유다.\n- **Outbox**는 '커밋 뒤 발행'을 유실 없이 하는 방법이다. Build의 outbox 과제가 이 경계를 직접 만든다.\n- **정산 정합성**(대사)은 경계 사이에서 끝내 어긋난 건을 나중에 찾아 맞추는 마지막 그물이다 — 주문/결제 설계 가이드의 마지막 단계가 이것을 엔진 밖 위험으로 남긴다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "커밋과 외부 호출 사이에 시간 차가 생겨, 사용자는 '결제 처리 중' 같은 중간 상태를 보게 된다. outbox 테이블과 디스패처, 보상 로직을 직접 운영해야 하고, 실패 경로를 모두 설계하지 않으면 반쪽 성공이 조용히 쌓인다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_TRANSACTION_BOUNDARY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 공연, 같은 좌석 수 — 차이는 락 하나가 무엇을 막느냐다.",
    "before": {
      "label": "공연 단위 락 하나",
      "body": "C-12를 고르는 사람과 A-03을 고르는 사람이 서로 아무 관계가 없는데도 같은 락 앞에 줄을 선다. 처리량은 좌석 수와 무관하게 한 줄의 속도다.",
      "alt": "좌석 A-03, C-12, F-07에 대한 요청이 모두 공연 전체 락 하나 앞에 줄을 서서 차례로 처리된다",
      "mermaid": "flowchart LR\n  U1[\"A-03 요청\"] --> L[\"공연 전체 락\\n(한 번에 한 명)\"]\n  U2[\"C-12 요청\"] --> L\n  U3[\"F-07 요청\"] --> L\n  L --> D[(\"좌석 테이블\")]"
    },
    "after": {
      "label": "좌석 단위 락",
      "body": "락의 키를 좌석 ID로 좁히면 같은 좌석을 노리는 요청끼리만 경쟁한다. 서로 다른 좌석은 동시에 진행된다.",
      "alt": "좌석 A-03, C-12, F-07 요청이 각자 자기 좌석의 락만 잡고 동시에 좌석 테이블에 기록된다",
      "mermaid": "flowchart LR\n  U1[\"A-03 요청\"] --> L1[\"락 seat:A-03\"]\n  U2[\"C-12 요청\"] --> L2[\"락 seat:C-12\"]\n  U3[\"F-07 요청\"] --> L3[\"락 seat:F-07\"]\n  L1 --> D[(\"좌석 테이블\")]\n  L2 --> D\n  L3 --> D"
    }
  },
  {
    "type": "steps",
    "title": "락 단위를 정하는 법",
    "items": [
      {
        "title": "정합성이 지켜져야 하는 가장 작은 단위를 찾는다",
        "body": "'한 좌석은 한 명에게만'이 불변식이라면 지켜야 할 단위는 좌석이다. 공연 전체를 막는 것은 필요 이상이다."
      },
      {
        "title": "락 키를 그 단위로 만든다",
        "body": "`seat:{showId}:{seatId}`처럼 키에 단위를 드러낸다. 행 락이라면 그 좌석 행만 `SELECT ... FOR UPDATE`로 잡는다."
      },
      {
        "title": "여러 개를 잡아야 하면 순서를 고정한다",
        "body": "연석 예약처럼 좌석 여러 개를 함께 잡을 때는 항상 좌석 ID 순서로 잡아 교착(deadlock)을 피한다."
      },
      {
        "title": "락의 수명을 처리 시간에 맞춘다",
        "body": "분산 락의 만료가 처리 시간보다 짧으면 처리 도중 다른 요청이 같은 좌석을 잡는다. 만료와 함께 소유자 확인(fencing token)을 둔다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "락만 쪼개면 — 혼자서는",
    "domain": "reservation",
    "incident": true,
    "base": {},
    "change": {
      "fineGrainedLockingEnabled": true
    },
    "changeLabel": "공연 단위 락 → 좌석 단위 락",
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
    ]
  },
  {
    "type": "numbers",
    "title": "낭비성 재시도를 없앤 뒤 락을 쪼개면",
    "domain": "reservation",
    "incident": true,
    "base": {
      "atomicInventoryCheckEnabled": true
    },
    "change": {
      "fineGrainedLockingEnabled": true
    },
    "changeLabel": "원자적 재고 확인 상태에서 좌석 단위 락",
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
    "body": "엔진에서 좌석 단위 락은 락 용량을 20배로 키운다. 그래도 혼자서는 사용률이 4500%에서 225%로 내려올 뿐 포화에서 벗어나지 못한다 — 결제하지 않고 떠난 사용자의 홀드가 5분 동안 용량의 90%를 붙잡고, 재고 확인에서 진 요청들의 재시도가 들어오는 요청을 세 배로 부풀리기 때문이다. 재시도 낭비를 먼저 없앤 상태에서 락을 쪼개면 사용률이 75%로 내려와 에러가 거의 사라진다. 홀드까지 결제 시간에 맞추는 과정은 예약 시스템 설계 가이드가 단계별로 따라간다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **예약 타임아웃**은 락이 아니라 홀드가 용량을 얼마나 오래 잡는지를 정한다. 락을 아무리 쪼개도 유령 홀드가 용량의 대부분을 붙잡고 있으면 세분화의 이득이 그 안에 갇힌다.\n- **재고 정합성**은 락 없이 같은 결과를 내는 길이다. 조건부 UPDATE 한 줄로 확인과 확정을 묶으면 좌석 락 자체가 필요 없어지기도 한다.\n- **동시성 제어**의 일반 원칙(낙관적·비관적 락)을 예약이라는 구체적 자원에 적용한 것이 이 개념이다. Build의 distributed-lock 과제가 만료·소유자 확인까지 직접 만든다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "락이 잘게 쪼개질수록 여러 개를 함께 잡는 경로에서 교착 가능성이 생기고, 락 키와 만료를 관리할 대상이 좌석 수만큼 늘어난다. 분산 락을 쓰면 락 저장소가 새 장애 지점이 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RESERVATION_LOCKING';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "남은 1석을 두 사람이 동시에 본다 — 차이는 확인과 확정 사이에 틈이 있느냐다.",
    "before": {
      "label": "조회 후 확정(두 단계)",
      "body": "두 요청이 모두 '1석 남음'을 읽고 둘 다 확정한다. 한쪽은 초과 판매가 되거나, 뒤늦게 실패해 처음부터 다시 시도한다.",
      "alt": "요청 A와 B가 모두 재고 1을 읽은 뒤 각자 확정을 시도해, 둘 다 성공하면 초과 판매가 되고 한쪽이 실패하면 재시도가 늘어난다",
      "mermaid": "flowchart LR\n  A[\"요청 A\"] -- \"1 남음 읽기\" --> S[(\"재고\")]\n  B[\"요청 B\"] -- \"1 남음 읽기\" --> S\n  A -- \"확정\" --> S\n  B -- \"확정\" --> X[\"초과 판매\\n또는 실패 후 재시도\"]"
    },
    "after": {
      "label": "확인과 확정을 한 연산으로",
      "body": "`UPDATE ... SET stock = stock - 1 WHERE stock > 0` 한 문장이 확인과 차감을 함께 한다. 0행이 바뀌었으면 매진이다 — 다시 시도할 이유가 없다.",
      "alt": "요청 A와 B가 모두 조건부 UPDATE를 보내고, 데이터베이스가 하나만 성공시키며 다른 하나는 즉시 매진 응답을 받는다",
      "mermaid": "flowchart LR\n  A[\"요청 A\"] --> U[\"조건부 UPDATE\\nstock > 0 일 때만 -1\"]\n  B[\"요청 B\"] --> U\n  U -- \"1행 변경\" --> OK[\"A 확정\"]\n  U -- \"0행 변경\" --> NO[\"B 즉시 매진 응답\"]"
    }
  },
  {
    "type": "steps",
    "title": "틈을 닫는 방법",
    "items": [
      {
        "title": "확인을 확정 연산 안으로 넣는다",
        "body": "조건부 UPDATE(`WHERE stock > 0`), Redis라면 `DECR` 뒤 음수면 되돌리기, 또는 Lua 스크립트 한 번으로 확인과 차감을 원자적으로 만든다."
      },
      {
        "title": "실패를 '매진'으로 확정한다",
        "body": "원자 연산이 실패했다는 것은 재고가 없다는 뜻이다. 클라이언트에 재시도를 유도하지 말고 매진을 바로 알린다."
      },
      {
        "title": "최종 방어선을 둔다",
        "body": "확정 테이블에 `(show_id, seat_id)` 유니크 제약을 두면, 앞 단계에 버그가 있어도 같은 좌석이 두 번 확정되지는 않는다."
      },
      {
        "title": "불변식을 주기적으로 점검한다",
        "body": "'확정 건수 ≤ 총 재고', '카운터 = 총 재고 − 확정 수'를 배치로 확인한다. 어긋남은 늦게 발견될수록 비싸다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "원자적 확인만 하면 — 혼자서는",
    "domain": "reservation",
    "incident": true,
    "base": {},
    "change": {
      "atomicInventoryCheckEnabled": true
    },
    "changeLabel": "조회 후 확정 → 원자적 재고 확인",
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
    ]
  },
  {
    "type": "numbers",
    "title": "락을 쪼갠 상태에서 원자적 확인을 더하면",
    "domain": "reservation",
    "incident": true,
    "base": {
      "fineGrainedLockingEnabled": true
    },
    "change": {
      "atomicInventoryCheckEnabled": true
    },
    "changeLabel": "좌석 단위 락 상태에서 원자적 재고 확인",
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
    "tone": "warning",
    "title": "시뮬레이션 엔진 밖의 위험",
    "body": "엔진은 비원자적 확인의 **재시도 낭비**만 계산한다 — 경쟁에서 진 요청이 다시 들어와 부하를 세 배로 만드는 것이다. 정작 이 개념이 막으려는 **초과 판매**(한 좌석이 두 번 팔리는 것)는 숫자로 나타나지 않는다. 그래서 지표가 평시로 돌아와도 이 틈은 남아 있을 수 있다. 예약 시스템 설계 가이드에서 홀드를 결제 시간에 맞춘 뒤 원자적 확인을 더해도 수치가 거의 움직이지 않는 것이 이 때문이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **예약 락**과 짝을 이룬다. 위 두 수치처럼 원자적 확인만으로도, 락 세분화만으로도 포화에서 벗어나지 못하고, 둘이 함께일 때 풀린다 — 하나는 용량을, 하나는 낭비를 다룬다.\n- **Hot Key 분산**과 부딪힌다. 원자 연산을 쓰려면 재고를 한 키에 모아야 해서, 선착순 쿠폰처럼 모두가 한 수량을 노리면 그 키가 hot key가 된다.\n- **정산 정합성**의 대사는 여기서도 마지막 그물이다. 카운터와 확정 레코드 수가 어긋났는지를 배치로 찾아내는 것이 같은 원리다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "재고를 한곳에 모아야 원자 연산이 성립하므로, 그 지점이 처리량의 상한이자 hot key가 된다. 재고를 여러 조각으로 나누면 경합은 줄지만 '전체 남은 수량'을 정확히 말하기 어려워진다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_INVENTORY_CONSISTENCY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 재처리 — 차이는 '이미 반영했는가'를 확인하느냐다.",
    "before": {
      "label": "다시 돌리면 다시 더한다",
      "body": "60%까지 반영한 배치가 실패 뒤 다시 돌면, 이미 반영된 가맹점 정산에 같은 금액이 한 번 더 더해진다.",
      "alt": "정산 배치가 실패한 뒤 재실행되면서 이미 반영된 레코드를 다시 읽어 정산 테이블에 같은 금액을 한 번 더 더한다",
      "mermaid": "flowchart LR\n  B[\"정산 배치\"] -- \"1회차: 60% 반영 후 실패\" --> T[(\"정산 테이블\")]\n  B -- \"재실행: 같은 레코드 다시\" --> T\n  T --> X[\"같은 거래 두 번 집계\"]"
    },
    "after": {
      "label": "멱등한 반영 + 대사",
      "body": "처리 이력(거래 ID)을 확인해 이미 반영된 것은 건너뛰거나 upsert로 덮어쓴다. 외부 정산 결과와의 차이는 따로 대사한다.",
      "alt": "정산 배치가 처리 이력에서 거래 ID를 확인해 반영되지 않은 것만 정산 테이블에 upsert하고, 별도의 대사 배치가 외부 정산 결과와 정산 테이블을 비교한다",
      "mermaid": "flowchart LR\n  B[\"정산 배치\"] --> H{\"처리 이력에\\n거래 ID 있음?\"}\n  H -- \"없음\" --> T[(\"정산 테이블\\nupsert\")]\n  H -- \"있음\" --> S[\"건너뜀\"]\n  R[\"대사 배치\"] -. \"외부 결과와 비교\" .-> T"
    }
  },
  {
    "type": "steps",
    "title": "재처리를 안전하게 만드는 순서",
    "items": [
      {
        "title": "레코드마다 자연 키를 정한다",
        "body": "거래 ID, (가맹점, 정산일) 같은 키가 있어야 '이미 했는가'를 물을 수 있다. 키가 없으면 멱등성도 없다."
      },
      {
        "title": "반영을 덮어쓰기로 만든다",
        "body": "`amount = amount + x` 대신 `INSERT ... ON CONFLICT DO UPDATE`나 기간 단위 삭제 후 재삽입으로, 몇 번 돌려도 결과가 같게 한다."
      },
      {
        "title": "처리 완료를 반영과 같은 트랜잭션에 남긴다",
        "body": "반영과 '처리함' 마킹이 따로 커밋되면 그 사이 실패가 다시 중복을 만든다. 둘은 한 청크 트랜잭션 안에 있어야 한다."
      },
      {
        "title": "외부와의 차이는 대사로 따로 찾는다",
        "body": "멱등한 반영은 우리 배치가 만드는 중복만 막는다. 외부 정산 API·PG 거래 내역과 우리 원장을 비교하는 대사 배치를 두고, 차이는 자동 보정하거나 사람에게 넘긴다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "체크포인트만 붙이면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {},
    "change": {
      "checkpointingEnabled": true
    },
    "changeLabel": "처음부터 재시작 → 체크포인트 재개",
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
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      }
    ]
  },
  {
    "type": "numbers",
    "title": "남은 중복을 없애면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {
      "checkpointingEnabled": true
    },
    "change": {
      "idempotentReconciliationEnabled": true
    },
    "changeLabel": "체크포인트 상태에서 멱등한 정산 재처리",
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
    ]
  },
  {
    "type": "text",
    "body": "이 도메인의 에러율은 요청 실패가 아니라 **정산이 어긋난 레코드의 비율**이다. 체크포인트로 재개하면 다시 처리하는 범위가 60만 건에서 실패한 청크 하나(1만 건)로 줄어 처리량이 살아나지만, 그 1만 건은 여전히 두 번 반영된다 — 에러율 1%. 멱등한 재처리를 더하면 처리량과 재처리 범위는 그대로인 채 어긋난 레코드만 0이 된다. 체크포인트는 **얼마나 다시 하는가**를, 멱등성은 **다시 해도 괜찮은가**를 정한다.\n\n- 외부 정산 결과와 우리 테이블의 차이(진짜 대사)는 엔진이 모델링하지 않는다. 배치/정산 설계 가이드도 이것을 엔진 밖 영역으로 남긴다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **재시작 가능성**(체크포인트)과 짝이다. 체크포인트 없이 멱등성만 있으면 중복은 없지만 60%를 처음부터 다시 하고, 멱등성 없이 체크포인트만 있으면 빠르지만 실패한 청크가 중복된다.\n- **Idempotent Consumer**와 같은 원리를 배치에 적용한 것이다 — '이 메시지(레코드)를 이미 처리했는가'를 키로 묻는다. Build의 idempotency 과제가 그 키 저장소를 직접 만든다.\n- **트랜잭션 경계 분리**·**결제 멱등성**이 남기는 반쪽 성공(PG는 승인, 우리는 실패)도 결국 대사로 찾는다. 대사는 여러 개념이 놓친 것을 모으는 마지막 그물이다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "처리 이력을 남기면 레코드마다 쓰기가 하나 더 생기고, 그 테이블도 관리해야 한다. 덮어쓰기는 몇 번 돌려도 결과가 같은 대신 중간에 무엇이 바뀌었는지 추적하기 어렵다. 대사를 실시간 경로에 넣으면 처리 지연이 되므로 보통 별도 배치로 둔다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RECONCILIATION';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 폭주, 같은 DB — 차이는 몇 개를 들여보낼지 누가 정하느냐다.",
    "before": {
      "label": "상한 없음",
      "body": "들어온 요청이 전부 DB 트랜잭션이 된다. DB가 감당할 양을 정하는 것은 사용자 수다.",
      "alt": "20배로 늘어난 사용자 요청이 모두 쿠폰 API를 거쳐 DB 쓰기로 가고, 커넥션 풀이 고갈된다",
      "mermaid": "flowchart LR\n  U[\"사용자 요청\\n20배\"] --> A[\"쿠폰 API\"]\n  A --> P[\"커넥션 풀\\n고갈\"]\n  P --> D[(\"DB 쓰기\\n용량 초과\")]"
    },
    "after": {
      "label": "앞단 Rate Limit",
      "body": "상한 안쪽만 API로 보내고, 넘치는 요청은 DB에 닿기 전에 429와 '언제 다시 오라'는 안내로 끝낸다.",
      "alt": "사용자 요청이 Rate Limiter를 지나며 상한 이내는 쿠폰 API와 DB로, 초과분은 429와 Retry-After 응답을 즉시 받는다",
      "mermaid": "flowchart LR\n  U[\"사용자 요청\\n20배\"] --> RL{\"Rate Limiter\\n초당 상한\"}\n  RL -- \"상한 이내\" --> A[\"쿠폰 API\"]\n  A --> D[(\"DB\")]\n  RL -. \"초과\" .-> X[\"429\\n+ Retry-After\"]"
    }
  },
  {
    "type": "steps",
    "title": "상한을 거는 순서",
    "items": [
      {
        "title": "상한을 가장 좁은 자원에서 역산한다",
        "body": "API 서버가 아니라 DB 쓰기·커넥션 풀처럼 가장 먼저 포화되는 자원이 견디는 양에서 거꾸로 계산한다. 평시 측정 없이 감으로 정한 상한은 너무 낮거나 의미가 없다."
      },
      {
        "title": "알고리즘을 고른다",
        "body": "Token Bucket은 버킷 크기만큼의 순간 버스트를 허용하고, Sliding Window는 창 경계에 요청이 몰리는 왜곡을 줄인다. Build의 rate-limiter 과제가 둘을 직접 만든다."
      },
      {
        "title": "어떤 키로 셀지 정한다",
        "body": "전체 상한 하나만 두면 한 사용자·한 IP가 몫을 다 가져간다. 사용자별·IP별·엔드포인트별 상한을 겹쳐 두고, 가장 바깥에 전체 상한을 둔다."
      },
      {
        "title": "거절을 '다시 올 시각'으로 바꾼다",
        "body": "`429 Too Many Requests`에 `Retry-After`를 싣는다. 이 안내가 없으면 거절당한 클라이언트가 즉시 재시도해 입구에 같은 양이 다시 몰린다."
      },
      {
        "title": "카운터 저장소가 죽으면 어떻게 할지 정한다",
        "body": "여러 서버가 같은 상한을 공유하려면 Redis 같은 공유 카운터가 필요하다. 그 저장소가 느려질 때 모두 통과(fail-open)시킬지 모두 거절(fail-closed)할지를 미리 정해 둔다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "용량만 늘리면 — 병목이 옮겨 간다",
    "domain": "coupon",
    "incident": true,
    "base": {},
    "change": {
      "dbPoolSize": 150
    },
    "changeLabel": "DB 커넥션 풀 50 → 150 (상한 없음)",
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
    "tone": "warning",
    "title": "용량은 병목의 위치를 바꿀 뿐이다",
    "body": "풀을 세 배로 늘리면 쓰기 사용률은 안정 구간 경계까지 내려오지만, 이번에는 **DB 읽기가 가장 바쁜 자원**이 되어 P95는 여전히 평시의 세 배다. 들어오는 양이 그대로인 한 어느 한 자원을 늘리면 다음 자원이 그 양을 받는다. 트래픽이 더 커지면 같은 포화가 반복된다."
  },
  {
    "type": "numbers",
    "title": "입구에서 상한을 두면",
    "domain": "coupon",
    "incident": true,
    "base": {
      "dbPoolSize": 150
    },
    "change": {
      "rateLimitEnabled": true
    },
    "changeLabel": "풀 150 상태에서 앞단 Rate Limit 도입",
    "metrics": [
      "trafficRps",
      "dbWriteLoad",
      "dbReadLoad",
      "p95LatencyMs"
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
        "metric": "dbReadLoad",
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      }
    ]
  },
  {
    "type": "text",
    "body": "받아들이는 요청이 절반으로 줄자 쓰기와 읽기가 **함께** 내려가 P95가 평시로 돌아온다. 상한은 특정 자원이 아니라 입구에 걸리기 때문에, 뒤에 있는 모든 자원의 부하를 한꺼번에 정한다. 그리고 상한이 있어야 '풀이 얼마면 충분한가'를 계산할 수 있다 — 쿠폰 설계 가이드가 Rate Limit을 풀 증설보다 먼저 두는 이유다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **Retry/Backoff** — 거절은 끝이 아니라 재시도로 돌아온다. `Retry-After`와 클라이언트 쪽 백오프가 짝을 이루지 않으면 Rate Limit이 재시도 폭풍의 과녁이 된다.\n- **Circuit Breaker** — Rate Limit은 들어오는 쪽에서 *우리를* 지키고, Circuit Breaker는 나가는 쪽에서 *느린 의존성으로부터* 우리를 지킨다. 같은 '빠른 거절'이지만 방향이 반대다.\n- **비동기 경계** — 넘치는 요청을 거절하는 대신 대기열에 세울 수도 있다. 사용자 경험은 낫지만 대기열의 저장·순서·만료가 새로 생긴다.\n- **리소스 제한** — 위 두 수치처럼, 상한이 정해진 뒤에야 풀·인스턴스 크기가 의미 있는 숫자가 된다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "상한 바깥의 사용자는 에러 대신 빠른 거절을 받을 뿐, 원하는 것을 얻지 못한다. 상한을 서버마다 따로 세면 서버 수가 바뀔 때마다 전체 상한이 흔들리고, 공유 카운터로 세면 요청마다 네트워크 왕복이 하나 늘며 그 저장소가 새 단일 장애 지점이 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RATE_LIMIT';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 실패, 같은 재시도 횟수 — 차이는 재시도가 언제 돌아오느냐다.",
    "before": {
      "label": "즉시 재시도",
      "body": "실패한 요청이 간격 없이 곧바로 다시 들어온다. 실패가 많을수록 유입이 부풀어, 회복하려는 대상에 가장 큰 부하를 준다.",
      "alt": "느린 provider에 대한 호출이 실패하면 곧바로 큐로 돌아와 새 이벤트와 합쳐지고, 유입이 처리량보다 커진다",
      "mermaid": "flowchart LR\n  N[\"새 이벤트\"] --> Q[(\"큐\")]\n  Q --> W[\"컨슈머\"]\n  W --> P[\"느린 provider\"]\n  W -- \"실패 → 즉시\" --> Q"
    },
    "after": {
      "label": "지수 백오프 + 지터 + 상한",
      "body": "재시도 간격을 지수적으로 늘리고 무작위로 흩어, 같은 순간에 돌아오는 재시도를 줄인다. 상한을 넘기면 DLQ로 보낸다.",
      "alt": "실패한 메시지가 1초, 2초, 4초처럼 늘어나는 간격에 무작위 지터를 더해 큐로 돌아오고, 재시도 상한을 넘기면 DLQ로 격리된다",
      "mermaid": "flowchart LR\n  N[\"새 이벤트\"] --> Q[(\"큐\")]\n  Q --> W[\"컨슈머\"]\n  W -- \"실패\" --> B[\"대기\\n1s·2s·4s + 지터\"]\n  B --> Q\n  B -. \"상한 초과\" .-> D[(\"DLQ\")]"
    }
  },
  {
    "type": "steps",
    "title": "재시도를 설계하는 순서",
    "items": [
      {
        "title": "재시도할 오류만 고른다",
        "body": "타임아웃·503·연결 실패처럼 다시 하면 성공할 수 있는 오류만 재시도한다. 400·잔액 부족처럼 결과가 같을 오류는 즉시 포기한다."
      },
      {
        "title": "간격을 지수적으로 늘린다",
        "body": "`base × 2^n`으로 늘리되 최대 간격을 둔다. 대상이 회복할 시간을 주는 것이 목적이라, 실패가 계속될수록 덜 두드린다."
      },
      {
        "title": "지터로 흩는다",
        "body": "같은 순간 실패한 클라이언트들이 같은 간격으로 기다리면 같은 순간 다시 몰린다. `0 ~ 계산된 간격` 사이에서 무작위로 고르면(full jitter) 재시도가 시간축에 퍼진다 — Build의 retry-backoff 과제가 이것을 검증한다."
      },
      {
        "title": "총 횟수·총 시간에 상한을 둔다",
        "body": "상한을 넘긴 작업은 DLQ로 보내거나 사용자에게 실패를 알린다. 끝이 없는 재시도는 장애가 끝난 뒤에도 부하로 남는다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "백오프만 넣으면",
    "domain": "notification",
    "incident": true,
    "base": {},
    "change": {
      "retryBackoffMultiplier": 10
    },
    "changeLabel": "재시도 백오프 배수 1 → 10 (다른 조치 없음)",
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
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "재시도를 줄여도 처리량은 그대로다",
    "body": "엔진의 알림 모델에서 즉시 재시도는 유입을 세 배로 부풀리고, 백오프 배수 10은 그것을 1.2배로 줄인다. lag은 절반 넘게 줄지만, 느린 provider를 기다리는 컨슈머 4개의 처리량은 줄어든 유입에도 한참 못 미쳐 지연·에러는 그대로다. 백오프는 **유입**을 고치고, 처리량은 다른 조치가 고친다."
  },
  {
    "type": "numbers",
    "title": "처리량을 되찾은 뒤에 넣으면",
    "domain": "notification",
    "incident": true,
    "base": {
      "consumerCount": 12,
      "circuitBreakerEnabled": true
    },
    "change": {
      "retryBackoffMultiplier": 10
    },
    "changeLabel": "컨슈머 12개 + Circuit Breaker 상태에서 백오프 배수 1 → 10",
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
    ]
  },
  {
    "type": "text",
    "body": "차단기로 컨슈머가 풀려나고 컨슈머가 늘어난 상태에서는 같은 백오프가 유입을 처리량 아래로 끌어내려 lag이 사라지고 지연·에러가 평시로 돌아온다. 같은 조치가 혼자일 때와 짝을 이룰 때 전혀 다른 결과를 낸다 — 알림 설계 가이드 3단계가 이 지점이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **멱등성 처리** — 재시도는 같은 요청을 두 번 이상 보낸다는 뜻이다. 상대가 멱등하지 않으면 백오프는 중복을 늦출 뿐 막지 못한다. 결제에서는 이것이 **PG 재시도/백오프**와 **결제 멱등성**의 조합이 된다.\n- **Circuit Breaker** — 차단기가 열린 동안의 호출은 즉시 실패한다. 백오프가 없으면 그 즉시 실패가 곧바로 재시도로 돌아와 반열림 시험 호출을 방해한다.\n- **DLQ** — 재시도 상한의 끝이 어디인지를 정한다. 상한 없는 재시도와 DLQ 없는 상한은 둘 다 메시지를 잃거나 큐를 막는다.\n- **Rate Limit** — 서버가 보낸 `Retry-After`를 존중하는 것도 백오프의 일부다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "간격이 길수록 일시적 오류에서 회복한 작업이 늦게 끝난다 — 인증번호처럼 몇 분 늦으면 쓸모없는 작업에는 짧은 상한이 필요하다. 지터는 재시도 시점을 예측할 수 없게 만들어 장애 중 로그를 읽기 어렵게 하고, 재시도 상한을 낮추면 그만큼 많은 작업이 DLQ나 사용자 실패로 넘어간다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RETRY_BACKOFF';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "처리할 수 없는 메시지 하나 — 차이는 그것이 큐 맨 앞에 남느냐다.",
    "before": {
      "label": "DLQ 없음",
      "body": "잘못된 번호·깨진 페이로드 같은 poison message를 컨슈머가 계속 다시 시도한다. 순서대로 처리하는 큐에서는 그 뒤의 정상 메시지가 모두 멈춘다.",
      "alt": "큐 맨 앞의 poison message를 컨슈머가 실패하고 다시 꺼내기를 반복하며, 뒤의 정상 메시지들이 처리되지 못하고 쌓인다",
      "mermaid": "flowchart LR\n  Q[(\"큐\\npoison · 정상 · 정상 …\")] --> W[\"컨슈머\"]\n  W -- \"실패 → 다시 맨 앞\" --> Q\n  W -. \"정상 메시지 대기\" .-> X[\"lag 증가\"]"
    },
    "after": {
      "label": "N회 실패 후 DLQ",
      "body": "정해진 횟수만큼 실패한 메시지는 원인 정보와 함께 DLQ로 옮기고, 컨슈머는 다음 메시지로 넘어간다. 원인을 고친 뒤 다시 넣는다.",
      "alt": "컨슈머가 N회 실패한 메시지를 DLQ로 옮기고 다음 메시지를 처리한다. DLQ 적재량에 알람이 걸리고, 원인을 고친 뒤 메시지를 원래 큐로 재투입한다",
      "mermaid": "flowchart LR\n  Q[(\"큐\")] --> W[\"컨슈머\"]\n  W -- \"성공\" --> OK[\"전송 완료\"]\n  W -. \"N회 실패\" .-> D[(\"DLQ\")]\n  D --> A[\"알람·조사\"]\n  A -. \"수정 후 재투입\" .-> Q"
    }
  },
  {
    "type": "steps",
    "title": "DLQ를 운영하는 순서",
    "items": [
      {
        "title": "실패를 둘로 나눈다",
        "body": "provider 타임아웃처럼 시간이 지나면 풀릴 실패는 백오프 재시도로, 형식 오류·없는 수신자처럼 몇 번을 해도 같은 실패는 바로 DLQ로 보낸다. 일시적 오류까지 즉시 DLQ로 보내면 재처리 기회를 잃는다."
      },
      {
        "title": "옮길 때 원인을 함께 남긴다",
        "body": "원본 메시지에 마지막 예외, 시도 횟수, 원래 큐와 위치(offset), 처음 실패한 시각을 붙인다. 이 정보가 없으면 DLQ는 원인 모를 메시지 더미가 된다."
      },
      {
        "title": "적재량을 알람으로 건다",
        "body": "DLQ 크기와 유입 속도를 지표로 두고, 0이 아니면 누군가 보게 한다. DLQ는 자동 복구 장치가 아니라 사람이 봐야 하는 대기열이다."
      },
      {
        "title": "재투입 경로를 만든다",
        "body": "원인을 고친 뒤 DLQ의 메시지를 원래 큐로 다시 넣는 도구(redrive)를 미리 만들어 둔다. 재투입은 같은 메시지를 다시 배달하는 것이라 컨슈머가 중복을 견뎌야 한다."
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "시뮬레이션 엔진 밖의 위험",
    "body": "Drill·랩의 엔진은 메시지를 한 건씩 보지 않고 유입·처리량·재시도 배율만 계산한다 — 특정 메시지가 영원히 실패하는 경우(poison message)와 그로 인한 head-of-line blocking은 모델링하지 않는다. 그래서 이 개념에는 엔진 수치 대신 그림만 있다. 알림 장애에서 Circuit Breaker와 백오프로 lag을 없앤 뒤에도, '재시도를 다 쓴 메시지가 어디로 가는가'라는 질문이 남는다 — 그 답이 DLQ다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **Retry/Backoff** — DLQ는 재시도의 끝이다. 재시도 상한이 없으면 DLQ로 갈 시점이 없고, DLQ가 없으면 상한에 닿은 메시지는 버려지거나 큐를 막는다.\n- **Circuit Breaker** — 차단기가 열린 동안 빠르게 실패한 메시지는 provider 탓이지 메시지 탓이 아니다. 이런 메시지까지 DLQ로 보내면 provider가 돌아왔을 때 다시 넣을 일만 쌓인다 — 재시도 큐가 먼저다.\n- **Idempotent Consumer** — 재투입은 같은 메시지를 다시 배달한다. 처음 실패가 '실제로는 보냈는데 응답만 잃은' 경우였다면 중복 발송이 된다.\n- **관측 가능성** — 알람 없는 DLQ는 조용한 유실이다. Build의 queue·event-bus 과제에서 실패 메시지가 어디로 가는지 직접 다룬다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "메시지 하나를 옆으로 빼는 순간 처리 순서가 깨진다 — 같은 주문의 '발송'과 '취소' 알림처럼 순서가 중요한 흐름에서는 키 단위로 뒤따르는 메시지까지 함께 보류해야 한다. DLQ를 비우는 일은 원인 조사·수정·재투입이라는 사람의 시간을 요구하고, 이 절차가 없으면 DLQ는 유실을 늦게 알아채는 장소가 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_DLQ';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "느린 의존성 하나 — 차이는 그것을 끝까지 기다리느냐다.",
    "before": {
      "label": "끝까지 기다림",
      "body": "provider가 평소의 15배로 느려지면 컨슈머 하나가 끝내는 메시지도 15분의 1이 된다. 컨슈머를 늘려도 각자 같은 시간을 기다린다.",
      "alt": "큐에서 메시지를 꺼낸 컨슈머들이 모두 느린 provider의 응답을 기다리며 묶여 있고, 큐가 쌓인다",
      "mermaid": "flowchart LR\n  Q[(\"큐\")] --> W[\"컨슈머\\n응답 대기\"]\n  W -- \"호출마다 300ms\" --> P[\"느린 provider\"]"
    },
    "after": {
      "label": "Circuit Breaker",
      "body": "실패율이 임계를 넘으면 차단기가 열려 provider를 부르지 않고 즉시 실패한다. 컨슈머는 다음 메시지로 넘어가고, 실패한 메시지는 재시도 큐로 간다.",
      "alt": "컨슈머가 차단기를 거쳐 provider를 호출하는데, 차단기가 열려 있으면 호출 없이 즉시 실패해 메시지를 재시도 큐로 보내고 반열림 상태에서만 시험 호출이 provider로 간다",
      "mermaid": "flowchart LR\n  Q[(\"큐\")] --> W[\"컨슈머\"]\n  W --> CB{\"차단기\"}\n  CB -- \"닫힘·반열림\\n시험 호출\" --> P[\"provider\"]\n  CB -. \"열림: 즉시 실패\" .-> R[(\"재시도 큐\")]"
    }
  },
  {
    "type": "steps",
    "title": "차단기가 판단하는 방식",
    "items": [
      {
        "title": "닫힘 — 최근 호출을 센다",
        "body": "최근 N개 호출(또는 N초)의 실패율과 느린 호출 비율을 슬라이딩 윈도로 센다. 최소 호출 수를 두어야 호출 두 개 중 하나 실패로 열리는 일이 없다."
      },
      {
        "title": "열림 — 부르지 않고 실패한다",
        "body": "임계를 넘으면 정해진 시간 동안 호출 없이 즉시 실패한다. 이 시간이 provider에게는 회복할 틈이고, 우리에게는 스레드·커넥션을 되찾는 시간이다."
      },
      {
        "title": "반열림 — 소량만 시험한다",
        "body": "대기 시간이 지나면 몇 개의 호출만 통과시킨다. 성공하면 닫고, 실패하면 다시 연다. Build의 circuit-breaker 과제가 이 세 상태 전이를 직접 만든다."
      },
      {
        "title": "열린 동안 무엇을 줄지 정한다",
        "body": "캐시된 값·기본값 같은 대체 응답, 재시도 큐에 넣고 '접수됨' 응답, 또는 명확한 실패 중 하나를 고른다. 차단기는 실패를 빠르게 만들 뿐 없애지 않는다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "차단기만 넣으면",
    "domain": "notification",
    "incident": true,
    "base": {},
    "change": {
      "circuitBreakerEnabled": true
    },
    "changeLabel": "Circuit Breaker 도입 (다른 조치 없음)",
    "metrics": [
      "externalDependencyLatencyMs",
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs"
    ],
    "claims": [
      {
        "metric": "externalDependencyLatencyMs",
        "direction": "DOWN"
      },
      {
        "metric": "consumerThroughput",
        "direction": "UP"
      },
      {
        "metric": "queueLag",
        "direction": "DOWN"
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
    "title": "처리량이 30배가 돼도 부족한 이유",
    "body": "컨슈머가 기다림에서 풀려나 처리량은 30배가 되지만, 즉시 재시도로 세 배가 된 유입이 아직 그보다 크다 — 그래서 lag은 줄어도 지연은 그대로다. 차단기가 '빠르게 실패'시킨 메시지가 곧바로 재시도로 돌아오기 때문이기도 하다."
  },
  {
    "type": "numbers",
    "title": "컨슈머와 백오프는 이미 있는데 차단기가 없으면",
    "domain": "notification",
    "incident": true,
    "base": {
      "consumerCount": 12,
      "retryBackoffMultiplier": 10
    },
    "change": {
      "circuitBreakerEnabled": true
    },
    "changeLabel": "컨슈머 12개 + 백오프 배수 10 상태에서 Circuit Breaker 도입",
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
    "body": "컨슈머를 세 배로 늘리고 재시도까지 줄여도, 느린 provider를 기다리는 한 처리량은 줄어든 유입에도 미치지 못한다. 차단기 하나가 그 기다림을 끊어 lag이 사라지고 지연·에러가 평시로 돌아온다. 세 조치 중 무엇도 혼자서는 이 장애를 풀지 못한다 — 알림 설계 가이드가 이 셋을 순서대로 쌓는다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **Retry/Backoff** — 차단기가 만든 즉시 실패는 재시도로 돌아온다. 백오프가 없으면 차단기는 실패를 빨리 만들어 재시도 폭풍을 더 빨리 돌릴 뿐이다.\n- **DLQ** — 빠르게 실패한 메시지는 재시도 큐로, 끝내 안 되는 메시지는 DLQ로 간다. 차단기는 '어디로 보낼지'를 정하지 않는다.\n- **비동기 경계** — 반드시 성공해야 하는 호출이라면 빠른 실패보다 큐에 미뤄 두는 편이 낫다. 차단기와 큐는 함께 쓰인다.\n- **Rate Limit** — 같은 '빠른 거절'을 들어오는 쪽에 거는 것이다. 결제 설계 가이드의 **커넥션 풀 격리**(bulkhead)는 또 다른 격리 방식으로, 느린 의존성이 쓰는 자원을 따로 떼어 둔다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "열린 동안 보낸 요청은 전부 실패하거나 대체 응답을 받는다 — 일부만 느렸던 provider라도 차단기는 전부를 끊는다. 의존성마다(SMS·푸시 provider마다) 차단기를 따로 두어야 하나가 열려도 나머지가 산다. 임계·윈도·대기 시간 세 값은 평시 트래픽과 장애 기록을 보고 계속 맞춰야 하는 운영 부담이 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_CIRCUIT_BREAKER';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "응답이 끊긴 결제 하나를 어떻게 다시 시도하느냐 — 그 차이가 이중 결제와 풀 포화를 가른다.",
    "before": {
      "label": "타임아웃마다 즉시 새 결제로",
      "body": "응답이 끊기면 승인 여부를 모른 채 곧바로 새 결제를 요청한다. 저하된 PG에 시도가 몇 배로 몰리고, 시도마다 커넥션을 오래 붙잡는다.",
      "alt": "디스패처가 PG 타임아웃을 받을 때마다 즉시 새 결제로 다시 요청해 같은 주문의 승인 시도가 여러 번 PG에 쌓인다",
      "mermaid": "flowchart LR\n  D[\"디스패처\"] -- \"승인 요청\" --> PG[\"PG (저하)\"]\n  PG -- \"타임아웃\" --> D\n  D -- \"즉시 재시도\\n(새 결제)\" --> PG"
    },
    "after": {
      "label": "멱등성 키 + 상태 조회 + 백오프",
      "body": "같은 주문은 같은 멱등성 키로 다시 보내고, 재시도 전에 승인 상태부터 조회한다. 간격은 지수 백오프와 지터로 벌리고, 상한을 넘으면 보류 큐로 넘긴다.",
      "alt": "타임아웃 뒤 먼저 승인 상태를 조회하고, 미승인일 때만 같은 멱등성 키로 백오프 후 재시도하며, 최대 횟수를 넘으면 사람이 확인하는 보류 큐로 보낸다",
      "mermaid": "flowchart LR\n  D[\"디스패처\"] -- \"key=ord-42\" --> PG[\"PG\"]\n  PG -- \"타임아웃\" --> Q{\"승인 상태 조회\"}\n  Q -- \"승인됨\" --> OK[\"결과 반영\"]\n  Q -- \"미승인\" --> B[\"백오프+지터 후\\n같은 key로 재시도\"]\n  B --> PG\n  Q -- \"상한 초과·불명\" --> H[\"보류 큐\\n(사람 확인)\"]"
    }
  },
  {
    "type": "steps",
    "title": "재시도 정책을 정하는 순서",
    "items": [
      {
        "title": "오류를 '다시 해도 되는 것'과 아닌 것으로 나눈다",
        "body": "타임아웃·연결 끊김·5xx는 일시적일 수 있다. 한도 초과·카드 거절처럼 PG가 명시적으로 거절한 응답은 몇 번을 다시 해도 결과가 같으니 재시도하지 않는다."
      },
      {
        "title": "주문마다 고정된 멱등성 키를 붙인다",
        "body": "키는 시도마다 새로 만들지 않고 주문(또는 결제 시도) 단위로 고정한다. 그래야 재시도가 '같은 결제의 결과 다시 받기'가 된다."
      },
      {
        "title": "다시 보내기 전에 상태부터 묻는다",
        "body": "응답이 유실된 건은 이미 승인됐을 수 있다. 승인 조회 API로 확인한 뒤 미승인일 때만 재요청한다."
      },
      {
        "title": "간격을 벌리고 상한을 둔다",
        "body": "지수 백오프(예: 1초 → 2초 → 4초)에 지터를 섞어 재시도가 한 시점에 몰리지 않게 하고, 최대 횟수를 넘거나 상태가 끝내 불명이면 보류 큐로 넘겨 사람이 확인한다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "디스패처만 늘리면",
    "domain": "payment",
    "incident": true,
    "base": {},
    "change": {
      "dispatcherWorkers": 16
    },
    "changeLabel": "디스패처 워커 4 → 16개",
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
    ]
  },
  {
    "type": "numbers",
    "title": "재시도가 일을 부풀리지 않게 하면",
    "domain": "payment",
    "incident": true,
    "base": {},
    "change": {
      "idempotentPgRetryEnabled": true
    },
    "changeLabel": "멱등성 키를 붙인 PG 재시도",
    "metrics": [
      "connectionPoolUsage",
      "queueLag",
      "p95LatencyMs",
      "errorRate",
      "externalDependencyLatencyMs"
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
      },
      {
        "metric": "externalDependencyLatencyMs",
        "direction": "SAME"
      }
    ]
  },
  {
    "type": "text",
    "body": "워커를 네 배로 늘려도 적체는 조금 줄 뿐 풀은 여전히 100%를 훨씬 넘는다. 엔진의 결제 모델에서 멱등성 없는 재시도는 주문 하나를 **시도 네 번**으로 부풀리기 때문이다. 재시도를 멱등하게 만들면 그 부풀림이 사라져 풀이 72%로 내려오고, 포화 구간을 벗어나 지연·에러가 크게 떨어진다. 남은 적체는 디스패처 증설이 비울 몫이다.\n\n- PG 지연(1초)은 그대로다 — 재시도 정책은 PG를 빠르게 만들지 않고, 느린 PG를 **더 세게 두드리지 않게** 할 뿐이다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "백오프의 효과는 엔진 밖에 있다",
    "body": "엔진은 재시도를 '멱등한가, 아닌가'로만 본다. 재시도 간격·횟수·지터는 모델링하지 않아서, 위 수치는 백오프 없이도 같다. 실제로는 저하된 PG에 즉시 재시도를 반복하면 PG가 더 느려지거나 우리를 제한(throttling)할 수 있다 — 멱등성은 재시도를 **안전하게**, 백오프는 **덜 하게** 만든다. 둘 중 하나만으로는 부족하다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **결제 멱등성**은 이 개념의 전제다. 키 없이 백오프만 두면 재시도 간격이 벌어질 뿐, 응답이 유실된 결제를 다시 승인하는 이중 결제는 그대로다.\n- **Retry/Backoff**의 일반 원칙(지수 백오프·지터·상한)을 결제에 적용한 것이다 — Build의 retry-backoff 과제가 그 부품을 직접 만든다. 결제에서는 여기에 '재시도 전 상태 조회'가 더해진다.\n- **Circuit Breaker**는 PG가 계속 실패할 때 시도 자체를 멈춘다. 재시도는 일시적 오류를 넘기는 도구이고, 지속적 장애에서는 브레이커가 재시도를 끊어야 한다.\n- **트랜잭션 경계 분리**로 PG 호출을 주문 트랜잭션 밖(outbox 뒤)으로 빼야 재시도가 주문 커넥션을 붙잡지 않는다 — 주문/결제 설계 가이드가 이 순서를 따라간다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "멱등성 키를 저장·조회하는 비용이 들고, PG가 키와 상태 조회를 지원하는지에 기대게 된다. 백오프를 길게 잡을수록 결제 확정이 늦어지고, 상태 조회 API마저 장애일 때를 위한 보류 큐와 사람의 확인 절차가 결국 필요하다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_PG_RETRY_BACKOFF';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "배포 중에도 '떠 있는 Pod'가 아니라 '요청을 받을 수 있는 Pod'만 용량이다.",
    "before": {
      "label": "readiness·PDB 없는 롤링 배포",
      "body": "기존 Pod가 한꺼번에 내려가고, 새 Pod는 준비가 끝나기 전부터 요청을 받는다. 배포하는 동안 용량 일부가 비어 있다.",
      "alt": "로드밸런서가 아직 준비 중인 새 Pod에도 요청을 보내 실패하고, 동시에 여러 기존 Pod가 종료되어 남은 Pod에 부하가 몰린다",
      "mermaid": "flowchart LR\n  LB[\"로드밸런서\"] --> O[\"기존 Pod\\n(일부만 남음)\"]\n  LB --> N[\"새 Pod\\n(준비 중)\"]\n  N -- \"실패\" --> E[\"503\"]\n  X[\"기존 Pod 여러 개\\n동시 종료\"] -. \"용량 손실\" .-> O"
    },
    "after": {
      "label": "readiness probe + PDB + maxUnavailable",
      "body": "준비를 마친 Pod만 트래픽을 받고, 동시에 빠지는 Pod 수에 상한을 둔다. 새 Pod가 ready가 된 뒤에야 기존 Pod가 내려간다.",
      "alt": "새 Pod가 readiness probe를 통과한 뒤에만 로드밸런서에 들어가고, PDB가 동시에 내려가는 기존 Pod 수를 제한해 용량이 유지된다",
      "mermaid": "flowchart LR\n  LB[\"로드밸런서\"] --> O[\"기존 Pod\\n(PDB로 대부분 유지)\"]\n  N[\"새 Pod\"] -- \"readiness 통과\" --> R[\"ready\"]\n  R -. \"그때 투입\" .-> LB"
    }
  },
  {
    "type": "steps",
    "title": "무중단 배포를 거는 순서",
    "items": [
      {
        "title": "readiness probe를 '진짜 준비'에 맞춘다",
        "body": "프로세스가 떴는지가 아니라 모델 로딩·커넥션 풀 준비처럼 요청을 처리할 수 있는지를 확인하는 엔드포인트를 둔다. liveness와는 분리한다 — 준비 중인 Pod를 죽이면 안 된다."
      },
      {
        "title": "한 번에 빠지는 수를 제한한다",
        "body": "Deployment의 `maxUnavailable`·`maxSurge`로 교체 속도를, PodDisruptionBudget으로 노드 작업 같은 자발적 중단까지 동시 중단 수를 묶는다."
      },
      {
        "title": "종료를 우아하게 한다",
        "body": "로드밸런서에서 빠질 시간을 주는 preStop 대기와 처리 중 요청을 끝내는 graceful shutdown을 둔다. 그렇지 않으면 내려가는 Pod가 받던 요청이 끊긴다."
      },
      {
        "title": "배포 자체를 단계로 나눈다",
        "body": "트래픽 일부에만 먼저 내보내고 지표를 본 뒤 넓힌다 — 이 판단을 규칙으로 만드는 것이 카나리 분석·자동 중단이다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "Pod를 10배로 늘린 상태에서 안전장치만 걸면",
    "domain": "autoscaling",
    "incident": true,
    "base": {
      "podReplicas": 40
    },
    "change": {
      "rolloutSafeguardEnabled": true
    },
    "changeLabel": "Pod 40개, 리소스 제한 그대로 — 무중단 배포 안전장치 추가",
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
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "용량이 4할 늘어도 사용자는 모른다",
    "body": "안전장치는 배포가 깎던 용량(엔진에서 3할)을 돌려준다. 그런데 Pod의 6할이 OOM으로 재시작을 반복하는 동안에는 되찾은 용량도 수요에 한참 못 미쳐, 지연·에러는 포화 구간 그대로다. 두 손실은 곱해지므로 **먼저 Pod가 죽는 이유(리소스 제한)를 고쳐야** 안전장치의 효과가 보인다."
  },
  {
    "type": "numbers",
    "title": "리소스 제한을 고친 뒤에 걸면",
    "domain": "autoscaling",
    "incident": true,
    "base": {
      "podReplicas": 40,
      "resourceLimitsTuned": true
    },
    "change": {
      "rolloutSafeguardEnabled": true
    },
    "changeLabel": "Pod 40개, 리소스 제한 조정 후 — 무중단 배포 안전장치 추가",
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
    ]
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **리소스 제한**이 Pod가 *살아 있게* 하고, 무중단 배포는 살아 있는 Pod가 *배포 중에도 용량으로 남게* 한다. 순서가 바뀌면 위 첫 번째 수치처럼 효과가 보이지 않는다.\n- **오토스케일링**은 Pod를 늘리지만, 배포가 용량을 깎는 동안에는 늘린 만큼이 그대로 용량이 되지 않는다 — 실시간 추천 API 설계 가이드가 리소스 제한 → 안전장치 → 증설 순서를 따라간다.\n- **카나리 분석·자동 중단**은 무중단 배포의 다음 단계다. 무중단 배포가 '배포가 용량을 깎지 않게'라면, 카나리는 '결함 있는 버전이 전체로 번지지 않게'다.\n- **관측 가능성** — 가용 Pod 비율과 503을 배포 이벤트와 함께 보지 않으면 '배포 때문에 용량이 빠졌다'는 것을 알아채기 어렵다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "준비된 Pod만 받고 조금씩 교체하니 배포가 길어지고, 롤백도 같은 속도로 느려진다. `maxSurge`만큼 새 Pod를 미리 띄울 여분 자원이 들고, PDB를 너무 빡빡하게 잡으면 노드 교체·업그레이드가 멈춘다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_ROLLOUT_SAFETY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "카나리 비율을 정하는 것과 카나리 구간을 '보고 판단하는 것'은 다른 일이다.",
    "before": {
      "label": "비율만 있는 카나리",
      "body": "관찰 시간이 지나면 무조건 비율을 두 배로 올린다. 전체 에러율만 보므로 작은 카나리의 결함은 평균에 묻힌다.",
      "alt": "롤아웃 타이머가 관찰 시간마다 카나리 비율을 10에서 20, 40, 80, 100퍼센트로 올리고, 전체 지표 대시보드는 판단에 쓰이지 않는다",
      "mermaid": "flowchart LR\n  T[\"타이머\"] --> S1[\"10%\"]\n  S1 --> S2[\"20%\"]\n  S2 --> S3[\"40%\"]\n  S3 --> S4[\"…100%\"]\n  M[\"전체 에러율\\n(평균)\"] -. \"아무도 안 봄\" .-> S2"
    },
    "after": {
      "label": "버전별 비교 + 자동 중단",
      "body": "단계마다 카나리와 기존 버전의 에러율·지연을 나눠 비교한다. 기준을 넘으면 승격 대신 자동으로 롤백한다.",
      "alt": "각 단계에서 카나리 버전과 기존 버전의 지표를 비교하는 분석기가 기준 이내면 다음 단계로 승격하고, 기준을 넘으면 카나리 비율을 0으로 롤백한다",
      "mermaid": "flowchart LR\n  C[\"카나리 10%\"] --> A{\"카나리 vs 기존\\n에러율·지연 비교\"}\n  A -- \"기준 이내\" --> P[\"20%로 승격\"]\n  A -- \"기준 초과\" --> R[\"자동 롤백\\n(0%)\"]"
    }
  },
  {
    "type": "steps",
    "title": "카나리 판단을 규칙으로 만드는 법",
    "items": [
      {
        "title": "지표를 버전으로 나눈다",
        "body": "요청·에러·지연에 `version` 태그를 붙여 카나리와 기존 버전을 따로 집계한다. 전체 평균만 있으면 5% 카나리의 결함은 전체 에러율을 조금 올릴 뿐이다."
      },
      {
        "title": "비교 기준과 최소 표본을 정한다",
        "body": "'카나리 에러율이 기존 버전보다 n배 이상', 'P95가 x ms 이상 높음'처럼 상대 비교로 잡고, 판단에 필요한 최소 요청 수를 둔다 — 요청이 너무 적으면 우연과 결함을 구분할 수 없다."
      },
      {
        "title": "단계와 관찰 시간을 정한다",
        "body": "예: 10 → 20 → 40 → 80 → 100%, 단계마다 관찰 시간. 관찰 시간이 판단에 필요한 표본을 모을 만큼 길어야 한다."
      },
      {
        "title": "결과를 사람이 아니라 컨트롤러가 집행한다",
        "body": "기준을 넘으면 승격을 멈추고 롤백까지 자동으로 한다. 원인 분석은 되돌린 다음에 한다."
      }
    ]
  },
  {
    "type": "timeline",
    "title": "같은 롤백, 다른 시점 — 첫 단계에서 판단하느냐",
    "domain": "deployment",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 5,
    "metrics": [
      "errorRate",
      "p95LatencyMs"
    ],
    "alerts": [
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.01,
        "label": "에러율 ≥ 1% (에러 예산)"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 150,
        "label": "P95 ≥ 150ms"
      }
    ],
    "scenarios": [
      {
        "label": "한 단계 더 지켜본 뒤 롤백 (150초)",
        "tone": "bad",
        "actions": [
          {
            "second": 150,
            "action": "ROLLBACK"
          }
        ],
        "claims": [
          {
            "metric": "errorRate",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          }
        ]
      },
      {
        "label": "첫 단계에서 판단해 롤백 (45초)",
        "tone": "good",
        "actions": [
          {
            "second": 45,
            "action": "ROLLBACK"
          }
        ],
        "claims": [
          {
            "metric": "errorRate",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          }
        ]
      }
    ],
    "caption": "결함 버전이 10% 카나리로 나가고, 롤아웃 시계는 60초마다 비율을 두 배로 올린다. 두 대응의 마지막 값은 같다 — 차이는 그 사이 선이 얼마나 높이 올라갔느냐, 즉 실패를 겪은 사용자의 수다."
  },
  {
    "type": "text",
    "body": "45초 롤백은 에러 예산 경보가 울리고 30초 뒤다 — Drill의 롤아웃 시계에서 설계에 **자동 롤백 기준**을 넣으면 컨트롤러가 기준을 넘은 뒤 30초를 지켜보고 롤백하는데, 그 여유와 같다. 사람이 '조금 더 보고' 판단하면 그 사이 비율이 40%까지 올라 에러율이 20%를 넘는다.\n\n- 카나리의 가치는 비율이 아니라 **첫 단계에서 멈추는 판단**에 있다. 판단 기준이 없으면 카나리는 결함을 천천히 퍼뜨리는 장치일 뿐이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **관측 가능성**이 전제다. 버전별로 나뉜 지표가 없으면 비교할 대상이 없다.\n- **롤백 계획**이 카나리 판단의 짝이다. 카나리가 '언제 멈출지'를 정하면, 롤백 계획은 '멈춘 뒤 정말 되돌릴 수 있는지'를 보장한다 — 되돌릴 수 없는 마이그레이션이 섞이면 자동 롤백도 소용없다.\n- **무중단 배포**는 배포가 용량을 깎지 않게 하고, 카나리는 결함이 전체로 번지지 않게 한다. 둘은 서로 다른 실패를 막는다.\n- 카나리 배포 설계 가이드가 시작 비율(10% → 1%)과 '작아진 만큼 안 보이는' 문제를 단계별로 따라간다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "단계마다 관찰 시간을 두는 만큼 배포가 느려지고, 카나리가 작을수록 판단에 필요한 표본이 모이는 데 오래 걸린다. 기준을 낮게 잡으면 정상 변동에도 배포가 멈추고, 높게 잡으면 결함이 기준 아래에서 조용히 번진다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_CANARY_ANALYSIS';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "롤백 버튼이 있는 것과 되돌릴 수 있는 배포인 것은 다르다.",
    "before": {
      "label": "계획 없는 롤백",
      "body": "장애가 나서야 '롤백해도 되나'를 토론한다. 이미지를 되돌렸는데 이번 배포에 컬럼 삭제가 섞여 있어 이전 버전이 새 스키마에서 실패한다.",
      "alt": "장애 발생 뒤 롤백 여부를 토론하느라 시간이 흐르고, 이미지를 되돌려도 파괴적 마이그레이션 때문에 이전 버전이 실패해 장애가 이어진다",
      "mermaid": "flowchart LR\n  I[\"장애\"] --> D[\"롤백할까?\\n토론\"]\n  D --> R[\"이미지 롤백\"]\n  R --> F[\"이전 버전 실패\\n(컬럼이 없음)\"]"
    },
    "after": {
      "label": "배포 전에 정한 롤백",
      "body": "롤백 기준과 결정권자를 미리 정하고, 스키마는 확장-축소로 바꿔 이전 버전도 동작하게 둔다. 기준을 넘으면 토론 없이 되돌린다.",
      "alt": "배포 전에 롤백 기준과 결정권자를 정하고, 확장-축소 마이그레이션으로 이전 버전과 호환되게 두어, 장애 때 기준을 넘으면 바로 롤백해 이전 버전이 정상 동작한다",
      "mermaid": "flowchart LR\n  P[\"배포 전:\\n기준·결정권자·\\nexpand 마이그레이션\"] --> I[\"장애\"]\n  I -- \"기준 초과\" --> R[\"즉시 롤백\"]\n  R --> OK[\"이전 버전 정상\\n(호환 스키마)\"]"
    }
  },
  {
    "type": "steps",
    "title": "배포 전에 정해 둘 것",
    "items": [
      {
        "title": "기준과 결정권자",
        "body": "'에러율 1% 초과 5분' 같은 롤백 기준과, 그때 누가 버튼을 누르는지를 적어 둔다. 기준을 넘으면 원인 분석보다 롤백이 먼저다."
      },
      {
        "title": "되돌릴 수 있는 스키마 변경",
        "body": "컬럼 삭제·이름 변경은 확장-축소(expand/contract)로 나눈다: 새 컬럼 추가 → 양쪽에 쓰기 → 읽기 전환 → 옛 컬럼은 다음 배포에서 삭제. 어느 시점에 되돌려도 이전 버전이 동작한다."
      },
      {
        "title": "코드 배포와 기능 활성화를 분리",
        "body": "위험한 기능은 피처 플래그 뒤에 둔다. 이미지를 되돌리지 않고 플래그만 꺼서 기능을 되돌릴 수 있다."
      },
      {
        "title": "되돌릴 수 없는 것은 미리 표시",
        "body": "이미 외부로 나간 데이터 형식, 결제 상태 전이처럼 롤백이 오히려 위험한 변경은 roll-forward와 보상 처리 계획을 따로 적는다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "승격을 멈추기만 하면",
    "domain": "deployment",
    "incident": true,
    "base": {
      "rolloutPromotions": 2
    },
    "change": {
      "rolloutPaused": true
    },
    "changeLabel": "카나리 40%에서 승격 일시 중지",
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
    ]
  },
  {
    "type": "numbers",
    "title": "되돌리면",
    "domain": "deployment",
    "incident": true,
    "base": {
      "rolloutPromotions": 2
    },
    "change": {
      "rolledBack": true
    },
    "changeLabel": "카나리 40%에서 롤백",
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
    "tone": "warning",
    "title": "엔진의 롤백은 언제나 성공한다 — 현실은 아니다",
    "body": "일시 중지는 비율이 더 커지는 것만 막고, 이미 결함 버전으로 가는 트래픽의 실패는 그대로다 — 실패를 멈추는 것은 롤백이다. 그런데 Drill의 엔진에서 롤백은 늘 즉시, 완전히 성공한다. 파괴적 마이그레이션, 새 형식으로 이미 쓰인 데이터, 켜진 채 남은 플래그처럼 **롤백이 실패하는 이유는 모두 엔진 밖**에 있고, 롤백 계획이 다루는 것이 바로 그것이다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **카나리 분석·자동 중단**이 '언제 되돌릴지'를 정하면, 롤백 계획은 '되돌릴 수 있는지'를 보장한다. 자동 롤백 기준이 있어도 스키마가 호환되지 않으면 롤백은 장애를 하나 더 만든다.\n- **무중단 배포**는 롤백에도 적용된다. 롤백도 배포이므로, readiness·PDB 없이 되돌리면 되돌리는 동안 용량이 빠진다 — 그리고 안전장치를 걸수록 롤백도 그만큼 느려진다.\n- **멱등성 처리**·**결제 멱등성** — 결제 상태 전이처럼 되돌리기 위험한 변경은 롤백 대신 roll-forward와 보상 처리를 택하는데, 그 보상 처리가 두 번 실행돼도 안전해야 한다.\n- 카나리 배포 설계 가이드의 '확산을 멈춘다 — 롤백' 단계가 일시 중지와 롤백의 차이를 Drill 흐름으로 보여 준다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "확장-축소 마이그레이션은 한 번의 변경을 두세 번의 배포로 늘리고, 그 사이 양쪽 컬럼에 쓰는 코드가 남는다. 피처 플래그는 정리하지 않으면 쌓여서 어떤 조합이 실제로 켜져 있는지 아무도 모르게 된다. 모든 변경을 되돌릴 수 있게 만들 수는 없으니, 무엇을 감수할지는 배포 전에 정해야 한다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_ROLLBACK_PLAN';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 주문 — 차이는 사용자가 무엇을 기다리느냐다.",
    "before": {
      "label": "모두 요청 안에서",
      "body": "주문 저장, 알림 발송, 집계를 한 요청에서 차례로 부른다. 응답 시간은 가장 느린 호출을 따라가고, 알림 provider가 죽으면 주문이 실패한다.",
      "alt": "사용자의 주문 요청을 받은 주문 API가 DB 저장, 느린 알림 provider 호출, 집계 호출을 모두 동기로 끝낸 뒤에야 응답한다",
      "mermaid": "flowchart LR\n  U[\"사용자\"] --> A[\"주문 API\"]\n  A -- \"동기\" --> D[(\"주문 DB\")]\n  A -- \"동기 (느림)\" --> P[\"알림 provider\"]\n  A -- \"동기\" --> S[\"집계\"]"
    },
    "after": {
      "label": "경계 뒤로 미루기",
      "body": "요청은 주문과 '주문됨' 이벤트를 한 트랜잭션에 쓰고 바로 응답한다. 알림·집계는 큐 뒤의 컨슈머가 각자 속도로 처리한다.",
      "alt": "주문 API는 주문과 outbox 이벤트를 한 트랜잭션으로 DB에 쓰고 응답한다. 커밋 후 이벤트가 큐로 발행되고, 알림 컨슈머와 집계 컨슈머가 따로 가져가며, 알림 컨슈머만 provider를 호출한다",
      "mermaid": "flowchart LR\n  U[\"사용자\"] --> A[\"주문 API\"]\n  A -- \"주문 + 이벤트\\n(한 트랜잭션)\" --> D[(\"주문 DB\\n+ outbox\")]\n  D -. \"커밋 후 발행\" .-> Q[\"큐\"]\n  Q --> C1[\"알림 컨슈머\"]\n  C1 --> P[\"알림 provider\"]\n  Q --> C2[\"집계 컨슈머\"]"
    }
  },
  {
    "type": "steps",
    "title": "경계를 긋는 순서",
    "items": [
      {
        "title": "응답이 보장해야 하는 것만 남긴다",
        "body": "\"이 응답을 받은 사용자가 무엇을 믿어도 되나\"를 묻는다. 주문이 저장됐다는 사실은 동기로, 알림·포인트 적립·집계는 경계 뒤로 보낸다."
      },
      {
        "title": "경계를 넘는 것은 커밋된 사실이다",
        "body": "DB 커밋과 큐 발행을 따로 하면 둘 사이에서 죽을 때 '저장됐는데 발행 안 됨'이 생긴다. 같은 트랜잭션에 outbox 행을 쓰고, 커밋된 것만 발행한다."
      },
      {
        "title": "소비자는 다시 받아도 안전하게",
        "body": "큐는 보통 최소 한 번 전달한다. 같은 메시지가 두 번 와도 결과가 한 번만 남게 하고, 끝내 실패하는 메시지는 재시도 뒤 DLQ로 격리한다."
      },
      {
        "title": "경계 뒤를 따로 관측한다",
        "body": "응답이 빨라지는 대신 실패가 조용해진다. 큐 lag, 이벤트 발생부터 처리 완료까지의 지연, DLQ 건수에 경보를 건다."
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "시뮬레이션 엔진 밖의 위험",
    "body": "Drill·랩의 엔진에는 \"동기로 할까, 큐 뒤로 보낼까\"를 바꾸는 레버가 없다 — 알림·결제 도메인은 경계가 이미 그어진 뒤의 모습이다. 그래서 이 개념에는 엔진 수치 대신 그림만 있다. 대신 경계 뒤에서 무슨 일이 생기는지는 엔진이 보여 준다: 알림 장애는 큐 뒤에서 lag으로 쌓이고, 결제 장애는 outbox 뒤에 적체로 쌓이다가 **커넥션 풀을 함께 쓰면 경계를 넘어 주문 처리까지 번진다**(주문/결제 가이드의 풀 격리 단계)."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **Rate Limit**은 넘치는 요청을 거절하고, 비동기 경계는 받아서 줄을 세운다. 둘 중 무엇을 고를지는 선착순 쿠폰 가이드의 '거절이냐 대기열이냐'가 다룬다.\n- **트랜잭션 경계 분리**는 경계를 넘는 순간의 원자성이다 — 커밋과 발행을 함께 묶는 outbox 패턴을 Build의 outbox 과제가 직접 만든다.\n- **Idempotent Consumer**와 **DLQ**는 경계 뒤의 전제 조건이다. 최소 한 번 전달되는 메시지를 안전하게 받는 쪽을 Build의 queue·event-bus 과제에서 구현한다.\n- 경계를 그어도 컨슈머가 느린 의존성에 묶이는 문제는 남는다 — **Circuit Breaker**와 **Retry/Backoff**가 알림 이벤트 처리 가이드에서 그 lag을 푼다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "한 요청의 일이 여러 프로세스에 흩어져, 장애를 추적하려면 요청 ID를 메시지 헤더까지 전파해야 한다. 처리 순서도 보장되지 않는다 — 순서가 중요한 이벤트는 같은 파티션 키로 묶어야 하고, 그만큼 병렬성이 줄어든다. 큐 자체도 운영하고 감시해야 할 시스템 하나가 더 늘어난 것이다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_ASYNC_BOUNDARY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "청크는 커밋 단위다 — 실패했을 때 무엇을 잃는지가 여기서 정해진다.",
    "before": {
      "label": "한 트랜잭션으로 전부",
      "body": "100만 건을 한 트랜잭션에서 처리한다. 끝날 때까지 락과 커넥션을 붙잡고, 90%에서 실패하면 전부 롤백된다.",
      "alt": "100만 건을 트랜잭션 하나로 처리하다 90% 지점에서 실패하고, 전체가 롤백되어 처음 상태로 돌아간다",
      "mermaid": "flowchart LR\n  R[\"거래 100만 건\"] --> T[\"트랜잭션 하나\\n(락·커넥션 계속 점유)\"]\n  T --> F[\"90%에서 실패\"]\n  F -- \"전부 롤백\" --> R"
    },
    "after": {
      "label": "청크 단위 커밋",
      "body": "1만 건씩 읽고·처리하고·쓰고 커밋한다. 실패하면 그 청크만 롤백되고, 앞선 청크는 이미 반영돼 있다.",
      "alt": "거래를 1만 건 청크로 나눠 청크마다 커밋하고, 청크 N에서 실패하면 그 청크만 롤백된다",
      "mermaid": "flowchart LR\n  C1[\"청크 1\\n1만 건\"] -- \"커밋\" --> C2[\"청크 2\"]\n  C2 -- \"커밋\" --> CD[\"…\"]\n  CD -- \"커밋\" --> CN[\"청크 N 실패\"]\n  CN -- \"이 청크만 롤백\" --> CN"
    }
  },
  {
    "type": "steps",
    "title": "청크 크기를 정하는 법",
    "items": [
      {
        "title": "청크 하나 = 트랜잭션 하나로 둔다",
        "body": "읽기·처리·쓰기를 청크마다 끝내고 커밋한다. 메모리에 올라가는 양과 트랜잭션이 락을 잡는 시간이 청크 크기에 비례한다."
      },
      {
        "title": "느린 외부 호출은 반영 트랜잭션 밖으로",
        "body": "정산 API 확정을 먼저 하고, 트랜잭션은 결과를 쓰기만 하게 짧게 둔다. 그래야 청크 크기가 커져도 락 보유 시간이 덜 늘어난다."
      },
      {
        "title": "두 비용을 저울질한다",
        "body": "청크마다 붙는 고정 커밋 오버헤드는 청크가 작을수록 비싸고, 실패 시 다시 할 양은 청크가 클수록 많다. 둘 다 재 보고 정한다."
      },
      {
        "title": "재시작 지점과 함께 설계한다",
        "body": "청크 경계가 곧 체크포인트 경계다. 진행 위치를 남기지 않으면 청크 크기는 처리량만 바꿀 뿐 실패 비용을 바꾸지 못한다 — 아래 첫 수치가 그것이다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "재시작이 처음부터인데 청크만 줄이면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {},
    "change": {
      "chunkSize": 1000
    },
    "changeLabel": "청크 10,000 → 1,000건 (체크포인트 없음)",
    "metrics": [
      "queueLag",
      "errorRate",
      "consumerThroughput"
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
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "작은 청크는 다시 할 양을 줄이지 않는다",
    "body": "진행 위치가 없으면 실패한 순간까지 한 일을 통째로 버린다. 그래서 재처리 대기(60만 건)와 중복 정산 비율은 그대로이고, 커밋만 열 배로 잦아져 처리량은 떨어진다. 장애 패턴 사전의 '그럴듯하지만 틀린 대응'이 이것이다."
  },
  {
    "type": "numbers",
    "title": "재시작이 가능해진 뒤의 청크 크기",
    "domain": "batch-settlement",
    "incident": true,
    "base": {
      "checkpointingEnabled": true,
      "idempotentReconciliationEnabled": true
    },
    "change": {
      "chunkSize": 1000
    },
    "changeLabel": "체크포인트·멱등 재처리 상태에서 청크 10,000 → 1,000건",
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
        "direction": "DOWN"
      },
      {
        "metric": "p95LatencyMs",
        "direction": "DOWN"
      }
    ]
  },
  {
    "type": "text",
    "body": "체크포인트가 들어온 뒤에야 청크 크기가 실패 비용을 정한다. 다시 할 양은 청크 하나로 열 배 줄고 청크 하나도 짧아지지만, 커밋 오버헤드 때문에 처리량은 거의 반이 된다 — 이제는 **어느 쪽이 더 싼가를 고르는 진짜 트레이드오프**다. 반대로 크게 키우면 커밋 절약분은 금세 바닥나고 다시 할 양만 커진다(배치/정산 가이드 4·5단계).\n\n**다른 개념과의 관계**\n\n- **재시작 가능성**이 청킹의 짝이다. 청크는 실패의 단위를, 체크포인트는 재개의 단위를 정하고, 둘이 같은 경계를 써야 한다.\n- **정산 정합성**과 **Idempotent Consumer** — 실패한 청크 안에는 이미 반영된 레코드가 섞여 있을 수 있어, 청크를 다시 하는 것이 안전하려면 반영이 멱등해야 한다.\n- **비동기 경계** 뒤의 컨슈머도 같은 저울질을 한다. 메시지를 몇 개씩 가져와 한 번에 확인(ack)하느냐가 곧 청크 크기다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "청크 사이에는 일부만 반영된 중간 상태가 보인다 — 배치가 도는 동안 정산 합계를 조회하면 절반만 반영된 값이 나온다. 적절한 크기는 데이터 양과 외부 API 지연이 바뀌면 함께 바뀌므로, 한 번 정하고 끝나는 값이 아니라 처리량과 재처리 지표를 보며 다시 맞추는 값이다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_CHUNKING';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "재시작이 어디서 시작하느냐는 '어디까지 했는지'를 어디에, 무엇과 함께 적었느냐로 정해진다.",
    "before": {
      "label": "진행 위치 없음",
      "body": "청크는 커밋되지만 어디까지 했는지는 남지 않는다. 재실행은 처음부터이고, 이미 반영된 레코드도 다시 쓴다.",
      "alt": "배치가 청크를 정산 테이블에 반영하다 실패하면 진행 위치를 모르므로 처음부터 다시 돌며 이미 반영된 레코드까지 다시 쓴다",
      "mermaid": "flowchart LR\n  J[\"배치\"] -- \"청크 반영\" --> D[(\"정산 테이블\")]\n  J --> F[\"실패\"]\n  F -- \"위치 모름\" --> S[\"처음부터 재실행\"]\n  S -- \"이미 반영된 것도 다시\" --> D"
    },
    "after": {
      "label": "커밋과 함께 위치 기록",
      "body": "청크 반영과 진행 위치를 한 트랜잭션에 쓴다. 재시작은 마지막 위치를 읽고 그다음 청크부터, 반영 전에 '이미 했는지'를 확인한다.",
      "alt": "배치가 청크 반영과 진행 위치 기록을 한 트랜잭션으로 커밋하고, 재시작하면 마지막 위치를 읽어 다음 청크부터 이어서 처리하며 반영 전에 이미 반영됐는지 확인한다",
      "mermaid": "flowchart LR\n  J[\"배치\"] -- \"청크 반영 + 위치\\n(한 트랜잭션)\" --> D[(\"정산 테이블\\n+ Job 저장소\")]\n  R[\"재시작\"] -- \"마지막 위치 읽기\" --> D\n  R --> N[\"다음 청크부터\"]\n  N -- \"이미 반영? 확인 후 쓰기\" --> D"
    }
  },
  {
    "type": "steps",
    "title": "재시작 가능하게 만드는 순서",
    "items": [
      {
        "title": "위치는 청크 커밋과 같은 트랜잭션에",
        "body": "반영과 위치 기록을 따로 커밋하면 그 사이에서 죽을 때 둘이 어긋난다 — 위치가 앞서면 누락, 뒤처지면 중복이다."
      },
      {
        "title": "위치는 정렬된 키로 적는다",
        "body": "`마지막 거래 ID` 같은 정렬된 커서를 쓴다. `몇 번째 페이지` 같은 offset은 실행 중에 행이 추가·삭제되면 밀려서 건너뛰거나 겹친다."
      },
      {
        "title": "입력을 고정한다",
        "body": "재시작한 Job은 같은 파라미터(정산일 등)로 같은 범위를 읽어야 한다. 실행 중에 새로 들어온 거래는 다음 회차로 넘긴다."
      },
      {
        "title": "다시 하는 청크는 멱등하게",
        "body": "실패한 청크 안의 일부는 이미 반영됐을 수 있다. 위치는 '어디서부터'를 정할 뿐, 그 청크 안의 중복은 거래 ID 같은 자연 키로 막는다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "체크포인트만 넣으면",
    "domain": "batch-settlement",
    "incident": true,
    "base": {},
    "change": {
      "checkpointingEnabled": true
    },
    "changeLabel": "체크포인트 재개 도입",
    "metrics": [
      "queueLag",
      "consumerThroughput",
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
        "metric": "errorRate",
        "direction": "DOWN"
      }
    ]
  },
  {
    "type": "text",
    "body": "다시 할 양이 60만 건에서 실패한 청크 하나로 줄고 처리량이 평시에 가깝게 돌아온다. 하지만 중복 정산 비율은 0이 아니다 — 그 청크 안에서 이미 반영된 레코드가 다시 쓰이기 때문이다. '재시작 지점만 기록하고 중복은 신경 쓰지 않기'가 이 개념의 흔한 실수다."
  },
  {
    "type": "numbers",
    "title": "재처리까지 멱등하게",
    "domain": "batch-settlement",
    "incident": true,
    "base": {
      "checkpointingEnabled": true
    },
    "change": {
      "idempotentReconciliationEnabled": true
    },
    "changeLabel": "체크포인트 상태에서 멱등한 정산 반영 추가",
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
    ]
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **청킹**이 재시작의 단위를 정한다. 체크포인트가 생긴 뒤에야 청크 크기가 '실패 시 다시 할 양'이 된다 — 그 전에는 처리량만 바꾼다.\n- **정산 정합성**·**Idempotent Consumer** — 체크포인트는 다시 할 범위를 줄이고, 멱등성은 다시 해도 안전하게 만든다. 위 두 수치처럼 하나만으로는 중복 0도, 짧은 재처리도 얻지 못한다.\n- **비동기 경계** 뒤의 컨슈머 오프셋 커밋이 같은 문제다. 처리 후 커밋하면 죽었을 때 중복, 먼저 커밋하면 유실 — 그래서 큐 컨슈머도 멱등해야 한다.\n- 재시작도 재시도다. 저하된 정산 API를 곧장 다시 두드리면 같은 청크가 또 실패한다 — **Retry/Backoff**와 '회복될 때까지 멈추는 판단'은 배치/정산 가이드의 엔진 밖 위험으로 남아 있다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "입력을 고정해야 하므로 '돌 때마다 최신 데이터를 읽는' 편한 배치를 쓸 수 없다. Job 저장소의 실행 이력도 관리 대상이 된다 — 같은 Job이 두 번 동시에 돌지 않게 잠그고, 실패한 실행을 이어 갈지 버릴지 운영자가 판단할 수 있는 도구가 필요하다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RESTARTABILITY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "장애를 누가 먼저 아느냐 — 사용자인가, 경보인가.",
    "before": {
      "label": "원인 지표와 평균만",
      "body": "CPU와 평균 지연 대시보드는 있지만 아무도 보지 않는다. 장애는 사용자 신고로 알고, 로그는 서버마다 흩어져 요청 하나를 따라갈 수 없다.",
      "alt": "서비스의 CPU와 평균 지연 대시보드는 운영자에게 닿지 않고, 사용자가 신고해야 운영자가 장애를 안다",
      "mermaid": "flowchart LR\n  S[\"서비스\"] --> M[\"CPU·평균 지연\\n대시보드\"]\n  M -. \"아무도 안 봄\" .-> O[\"운영자\"]\n  U[\"사용자\"] -- \"신고\" --> O"
    },
    "after": {
      "label": "증상 경보 + 원인으로 좁히는 길",
      "body": "사용자가 느끼는 증상(에러율·P95)에 SLO 경보를 걸고, 경보를 받으면 같은 요청 ID로 로그와 트레이스를 따라 원인 구간까지 좁힌다.",
      "alt": "서비스가 지표, 요청 ID가 붙은 로그, 구간별 트레이스를 낸다. 에러율과 P95에 건 SLO 경보가 운영자를 부르고, 운영자는 요청 ID로 로그와 트레이스를 따라 원인을 찾는다",
      "mermaid": "flowchart LR\n  S[\"서비스\"] --> M[\"지표\\n처리량·P95·에러율·포화도\"]\n  S --> L[\"로그\\n(요청 ID)\"]\n  S --> T[\"트레이스\\n구간별 지연\"]\n  M -- \"SLO 경보 (증상)\" --> O[\"운영자\"]\n  O -- \"요청 ID로\" --> L\n  O --> T"
    }
  },
  {
    "type": "steps",
    "title": "무엇부터 갖추나",
    "items": [
      {
        "title": "경보는 증상에, 원인 지표는 진단에",
        "body": "에러율·P95처럼 사용자가 느끼는 것에 경보를 건다. 오류 예산이 얼마나 빨리 타는지(burn rate)를 짧은 창과 긴 창 두 개로 보면 급한 장애와 서서히 나빠지는 장애를 함께 잡는다. DB·CPU 사용률은 진단용 대시보드에 둔다."
      },
      {
        "title": "자원마다 포화도를 나란히",
        "body": "가장 포화된 자원이 사용자 지표를 정한다. 읽기·쓰기·커넥션 풀·큐 lag을 한 화면에 두어야 눈에 띄는 쪽이 아니라 병목인 쪽을 고친다."
      },
      {
        "title": "요청 하나를 끝까지 잇는다",
        "body": "요청 ID(trace id)를 로그와 메시지 헤더에 전파한다. 큐를 건너는 순간 끊기면 비동기 처리의 실패는 영영 원인과 이어지지 않는다."
      },
      {
        "title": "조치를 그래프 위에 표시한다",
        "body": "배포·설정 변경·장애 조치 시각을 그래프에 남겨(annotation) 조치 전후를 같은 그래프로 비교한다. 그래야 '나아졌는지'를 추측이 아니라 확인으로 답한다."
      }
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "시뮬레이션 엔진 밖의 위험",
    "body": "Drill·랩의 엔진에는 관측 수단의 유무를 바꾸는 레버가 없다 — Drill 화면에서는 모든 지표가 처음부터 보인다. 실제 장애에서 가장 비싼 것은 그 지표가 없는 순간이라, 이 개념에는 엔진 수치가 없다. 대신 엔진 위에서도 **무엇을 보느냐**의 문제는 드러난다: 상품 조회 장애 타임라인에서는 결과 지표(DB 읽기)의 경보가 원인 지표(hit ratio)보다 먼저 울리고, 선착순 쿠폰 가이드 1단계에서는 캐시 지표만 보면 TTL 조치가 효과 있어 보이지만 P95·에러율은 그대로다."
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **카나리 분석·자동 중단**은 관측 위에 서 있다. 지표를 버전별로 나눠 볼 수 없으면 5% 카나리의 결함은 전체 평균에 묻힌다.\n- **오토스케일링**과 **무중단 배포**는 지표를 보고 움직이는 자동화다. HPA의 확장 지표, readiness probe가 틀리면 자동화가 틀린 방향으로 빨리 움직인다.\n- **비동기 경계** 뒤에서는 실패가 조용하다. 큐 lag, 처리 지연, DLQ 건수를 따로 경보하지 않으면 사용자 응답은 정상인데 알림만 몇 시간째 안 나가는 장애를 놓친다.\n- **청킹**·**재시작 가능성** — 배치는 청크별 처리·실패 건수와 진행 위치를 남겨야 '오래 걸린다'에서 '어느 청크에서 왜'로 좁혀진다(배치/정산 가이드 1단계의 관측 포인트)."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "사용자 ID·주문 ID 같은 값을 지표 레이블에 넣으면 시계열 수가 폭발한다 — 그런 값은 로그와 트레이스의 몫이다. 트레이스는 전부 저장하기 어려워 샘플링하는데, 샘플링하면 드문 실패가 빠지고, 실패를 골라 남기는 방식(tail-based sampling)은 수집 쪽 비용과 복잡도가 늘어난다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_OBSERVABILITY';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "같은 이탈률, 같은 좌석 — 차이는 떠난 사용자의 홀드가 얼마나 오래 좌석을 붙잡느냐다.",
    "before": {
      "label": "홀드 5분, 결제 시간과 무관",
      "body": "결제하지 않고 떠난 사용자의 홀드도 5분 내내 좌석을 붙잡는다. 몰린 요청 속에서 이런 유령 홀드가 쌓이면 실제로는 빈 좌석이 '판매 불가'로 남는다.",
      "alt": "예약 요청이 5분 홀드를 만들고, 결제한 사용자는 확정되지만 떠난 사용자의 홀드는 5분간 좌석을 점유해 팔 수 있는 좌석이 줄어든다",
      "mermaid": "flowchart LR\n  R[\"예약 요청\"] --> H[\"홀드 5분\"]\n  H -- \"결제\" --> C[\"확정\"]\n  H -- \"이탈\" --> G[\"유령 홀드\\n5분간 좌석 점유\"]\n  G -.-> X[\"팔 수 있는 좌석 감소\"]"
    },
    "after": {
      "label": "결제 시간에 맞춘 홀드 + 만료 작업",
      "body": "홀드에 만료 시각을 기록하고, 실제 결제 시간에 맞춘 2분이 지나면 만료 작업이 풀어 다시 판다. 결제 화면에 들어간 사용자는 홀드를 한 번 연장한다.",
      "alt": "예약 요청이 만료 시각이 기록된 2분 홀드를 만들고, 결제 진입 시 홀드가 연장되며, 결제하면 확정되고 떠나면 만료 작업이 2분 뒤 풀어 좌석이 다시 판매된다",
      "mermaid": "flowchart LR\n  R[\"예약 요청\"] --> H[\"홀드 2분\\n(만료 시각 기록)\"]\n  P[\"결제 진입\"] -. \"한 번 연장\" .-> H\n  H -- \"결제\" --> C[\"확정\"]\n  H -- \"이탈\" --> E[\"만료 작업\\n2분 뒤 해제\"]\n  E --> S[\"다시 판매\"]"
    }
  },
  {
    "type": "steps",
    "title": "타임아웃을 정하고 지키는 순서",
    "items": [
      {
        "title": "실제 결제 시간을 잰다",
        "body": "홀드 생성부터 결제 완료까지 걸린 시간의 분포(p95·p99)를 본다. 타임아웃은 '짧을수록 좋은 값'이 아니라 이 분포에 여유를 더해 정하는 값이다."
      },
      {
        "title": "홀드에 만료 시각을 기록한다",
        "body": "타임아웃을 설정값으로만 두지 말고 홀드 행에 `expires_at`을 쓴다. 만료 시각이 데이터에 있어야 조회·회수·연장이 모두 같은 기준을 본다."
      },
      {
        "title": "두 겹으로 만료시킨다",
        "body": "조회할 때 `expires_at`이 지난 홀드는 없는 것으로 보고(지연 만료), 주기적인 만료 작업이 실제로 풀어 재고에 돌려놓는다. 회수 작업만 있으면 주기 사이에 좌석이 묶이고, 지연 만료만 있으면 집계가 틀어진다."
      },
      {
        "title": "정상 사용자를 위한 연장 규칙을 둔다",
        "body": "결제 화면에 진입하면 한 번 연장하거나 만료 직전에 알린다. 짧은 타임아웃의 대가를 떠난 사용자가 아니라 느린 사용자가 치르지 않게 하는 장치다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "락은 그대로 두고 타임아웃만 줄이면",
    "domain": "reservation",
    "incident": true,
    "base": {},
    "change": {
      "holdTimeoutSeconds": 30
    },
    "changeLabel": "홀드 타임아웃 300 → 30초 (공연 단위 락 그대로)",
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
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "용량을 되찾아도 그릇이 작으면 소용없다",
    "body": "극단적으로 줄인 타임아웃은 유령 홀드가 빼앗던 용량을 대부분 돌려준다 — 처리량이 몇 배로 오른다. 그래도 공연 전체를 락 하나로 묶은 그릇 자체가 작아 여전히 포화이고, 사용자가 겪는 지연·에러는 그대로다. 그 사이 30초 안에 결제를 못 끝낸 정상 사용자의 좌석은 남에게 넘어간다 — 장애 패턴 사전의 '그럴듯하지만 틀린 대응'이 이것이다."
  },
  {
    "type": "numbers",
    "title": "좌석 단위 락 위에서 결제 시간에 맞추면",
    "domain": "reservation",
    "incident": true,
    "base": {
      "fineGrainedLockingEnabled": true
    },
    "change": {
      "holdTimeoutSeconds": 120
    },
    "changeLabel": "좌석 단위 락 상태에서 홀드 300 → 120초",
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
    ]
  },
  {
    "type": "text",
    "body": "엔진의 예약 모델에서는 몰린 요청의 15%가 결제하지 않고 떠나고, 그들의 홀드가 타임아웃 동안 처리 용량을 깎는다. 손실은 타임아웃 30초마다 15%씩 커지다가 **90%에서 멈춘다** — 그래서 5분과 3분은 똑같이 용량의 90%를 잃고(설계 가이드 2단계가 아무것도 바꾸지 못한 이유), 2분이 돼서야 손실이 60%로 줄어 처리 용량이 수요를 넘어선다. 포화된 구간에서는 '조금 줄이기'가 듣지 않는 계단이 있는 셈이다.\n\n**다른 개념과의 관계**\n\n- **예약 락**은 동시에 처리할 수 있는 요청 수, 즉 그릇의 크기를 정하고, 예약 타임아웃은 그 그릇 중 유령 홀드에 빼앗기는 몫을 정한다. 위 두 수치 블록처럼 하나만으로는 이 장애가 풀리지 않는다.\n- **재고 정합성** — 만료 작업과 결제 확정이 같은 홀드를 동시에 건드릴 수 있다. 해제도 '아직 홀드 상태이고 만료 시각이 지났을 때만'이라는 조건부 갱신이어야 방금 결제된 좌석을 풀지 않는다. 이 경쟁은 엔진 밖의 위험이다.\n- Build의 `distributed-lock` 과제에서 다루는 **락의 만료(TTL)**도 같은 문제다 — 작업 시간보다 짧으면 남의 작업 도중 풀리고, 길면 죽은 소유자가 오래 붙잡는다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "만료 시각·연장 규칙·회수 작업이라는 상태 관리가 생긴다. 만료 뒤에 도착한 결제 성공 응답처럼 '이미 풀린 좌석에 대한 결제'를 환불하거나 재배정하는 경로도 함께 설계해야 한다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RESERVATION_TIMEOUT';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "Pod를 늘리는 것과 용량을 늘리는 것은 같은 일이 아니다 — 늘어난 Pod가 요청을 받을 수 있을 때만 같아진다.",
    "before": {
      "label": "Pod 수만 늘리는 확장",
      "body": "트래픽이 10배가 되자 Pod를 10배로 늘린다. 그런데 새 Pod도 같은 limit으로 OOM에 죽고 배포에 휩쓸려, 떠 있는 Pod 중 일부만 요청을 받는다.",
      "alt": "트래픽 10배에 HPA가 Pod를 4개에서 40개로 늘리지만, 일부는 OOM으로 재시작하고 일부는 배포로 빠져 실제 용량은 수요에 못 미친다",
      "mermaid": "flowchart LR\n  T[\"트래픽 10배\"] --> H[\"HPA\\nPod 4 → 40\"]\n  H --> P[\"떠 있는 Pod 40개\"]\n  P -- \"OOM 재시작\" --> X[\"죽은 Pod\"]\n  P -- \"배포로 빠짐\" --> Y[\"빠진 Pod\"]\n  P -- \"남은 일부\" --> C[\"실제 용량\\n수요에 못 미침\"]"
    },
    "after": {
      "label": "건강한 Pod를 지표로 늘리기",
      "body": "limit이 맞춰져 죽지 않고, readiness를 통과해야만 요청을 받는 Pod를 요청 수 지표로 늘린다. 떠 있는 Pod 수가 곧 용량이 된다.",
      "alt": "요청 수와 CPU 지표를 보고 HPA가 맞춘 limit을 가진 새 Pod를 띄우고, readiness를 통과한 Pod만 로드밸런서에 들어가 Pod 수가 곧 용량이 된다",
      "mermaid": "flowchart LR\n  M[\"요청 수·CPU 지표\"] --> H[\"HPA\\n최소·최대·쿨다운\"]\n  H --> N[\"새 Pod\\n(실측 limit)\"]\n  N -- \"readiness 통과\" --> LB[\"로드밸런서\"]\n  LB --> C[\"실제 용량 = Pod 수\"]"
    }
  },
  {
    "type": "steps",
    "title": "확장이 듣게 하려면",
    "items": [
      {
        "title": "확장 단위부터 건강하게",
        "body": "확장은 Pod 하나의 용량에 곱하는 수다. Pod가 OOM으로 죽거나 준비 전에 요청을 받는다면, 늘린 만큼 같은 비율로 버려진다."
      },
      {
        "title": "확장 지표를 고른다",
        "body": "CPU는 늦게 반응하거나 병목과 무관할 수 있다. 요청 수·동시 처리 수처럼 부하를 직접 나타내는 지표를 두고, 목표 사용률은 포화 구간보다 한참 아래에 잡는다."
      },
      {
        "title": "최소·최대를 정한다",
        "body": "최소는 새 Pod가 뜨는 동안(콜드 스타트) 버틸 만큼, 최대는 뒤에 있는 DB·외부 의존성이 감당할 만큼으로 정한다."
      },
      {
        "title": "줄일 때는 천천히",
        "body": "스케일 인에 안정화 창(쿨다운)을 두어 트래픽이 출렁일 때 늘렸다 줄였다를 반복하지 않게 한다."
      },
      {
        "title": "예고된 폭증은 미리",
        "body": "할인 행사·공연 오픈처럼 시각을 아는 이벤트는 반응형 확장을 기다리지 않고 미리 늘려 둔다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "Pod만 10배로 늘리면",
    "domain": "autoscaling",
    "incident": true,
    "base": {},
    "change": {
      "podReplicas": 40
    },
    "changeLabel": "Pod 4 → 40개 (리소스 제한·배포 안전장치 그대로)",
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
    ]
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "재시작하는 Pod도 10배가 된다",
    "body": "이 도메인의 '적체 / 대기' 지표는 재시작을 반복하는 Pod 수다. Pod를 10배로 늘리면 처리 용량과 함께 죽어 있는 Pod도 10배가 된다 — 비율은 그대로이기 때문이다. 비용은 10배를 내고 지연·에러는 움직이지 않는다."
  },
  {
    "type": "numbers",
    "title": "죽지 않는 Pod를 늘리면",
    "domain": "autoscaling",
    "incident": true,
    "base": {
      "resourceLimitsTuned": true,
      "rolloutSafeguardEnabled": true
    },
    "change": {
      "podReplicas": 40
    },
    "changeLabel": "리소스 제한·배포 안전장치를 갖춘 상태에서 Pod 4 → 40개",
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
    ]
  },
  {
    "type": "text",
    "body": "같은 40개인데 결과가 다르다. 리소스 제한과 배포 안전장치를 먼저 갖추면 Pod 4개로는 여전히 수요의 몇 분의 일이지만, 여기서 늘린 Pod는 전부 용량이 돼 사용률이 여유 구간으로 내려온다. 안정성이 확장을 대신하지 못하고, 확장도 안정성을 대신하지 못한다 — **둘 다 필요하고, 순서는 안정성이 먼저**다.\n\n**다른 개념과의 관계**\n\n- **리소스 제한** — 오토스케일링의 선행 조건이다. limit이 맞지 않아 Pod가 죽으면 늘린 Pod도 같은 비율로 죽는다.\n- **무중단 배포** — HPA가 Pod를 늘리는 것과 배포가 Pod를 교체하는 것은 같은 readiness 관문을 지난다. 그 관문이 없으면 새 Pod는 준비되기 전에 요청을 받아 실패한다.\n- **관측 가능성** — HPA는 지표로 움직인다. 지표가 늦거나 병목과 무관하면 확장도 늦거나 엉뚱하다.\n- 엔진 밖의 위험: 늘어난 Pod가 DB 커넥션을 그만큼 더 요구해 병목이 DB로 옮겨 갈 수 있고, 새 Pod는 콜드 스타트 동안 요청을 받지 못한다 — 실시간 추천 API 설계 가이드 4단계가 이 한계를 다룬다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "반응형 확장은 폭증보다 늘 한 박자 늦다. 그 공백을 메우려 최소 Pod 수를 높이면 평시 비용이 오르고, 최대값을 높이면 뒤의 DB·외부 의존성이 새 병목이 된다. 확장 기준·쿨다운을 잘못 잡으면 Pod 수가 출렁이며 그 자체가 불안정의 원인이 된다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_AUTOSCALING';

update learning_concepts set blocks = $blocks$[
  {
    "type": "compare",
    "caption": "메모리 limit이 실제 피크보다 낮으면, 트래픽이 몰리는 바로 그 순간 Pod가 죽는다.",
    "before": {
      "label": "감으로 정한 limit",
      "body": "트래픽이 몰려 추천 계산에 메모리가 더 필요해지면 limit을 넘어 커널이 프로세스를 죽인다(OOMKilled). 재시작한 Pod는 다시 요청을 받고 또 죽는다.",
      "alt": "트래픽 급증으로 메모리 사용량이 limit을 넘어 Pod가 OOMKilled되고, 재시작한 Pod가 다시 요청을 받아 또 limit을 넘는 반복이 생긴다",
      "mermaid": "flowchart LR\n  T[\"트래픽 급증\"] --> M[\"메모리 사용량 ↑\"]\n  M -- \"limit 초과\" --> K[\"OOMKilled\\n(exit 137)\"]\n  K --> R[\"재시작\"]\n  R -- \"다시 요청 받음\" --> M"
    },
    "after": {
      "label": "실측 기반 request/limit",
      "body": "부하 테스트로 평시·피크 사용량을 재고, request는 평시 실측으로, limit은 피크에 여유를 더해 정한다. 스케줄러는 request를 보고 노드에 배치한다.",
      "alt": "부하 테스트로 잰 평시 사용량이 request가 되어 스케줄러 배치에 쓰이고, 피크에 여유를 더한 값이 limit이 되어 스파이크를 흡수하고 노드를 보호한다",
      "mermaid": "flowchart LR\n  L[\"부하 테스트\\n평시·피크 실측\"] --> RQ[\"request = 평시\"]\n  L --> LM[\"limit = 피크 + 여유\"]\n  RQ --> S[\"스케줄러 배치\"]\n  LM --> A[\"스파이크 흡수\\n노드 보호\"]"
    }
  },
  {
    "type": "steps",
    "title": "request와 limit을 정하는 법",
    "items": [
      {
        "title": "먼저 잰다",
        "body": "평시와 피크(바이럴 같은 폭증)의 메모리·CPU 사용량을 부하 테스트와 운영 지표로 잰다. 측정 없이 정한 값은 안 정한 것보다 나쁠 수 있다."
      },
      {
        "title": "request는 배치, limit은 상한",
        "body": "request는 스케줄러가 노드에 Pod를 놓을 때 쓰는 예약량이고, limit은 넘으면 제재를 받는 상한이다. request가 너무 낮으면 한 노드에 Pod가 과밀하게 몰린다."
      },
      {
        "title": "메모리와 CPU는 넘었을 때가 다르다",
        "body": "메모리 limit을 넘으면 **죽고**(OOMKilled), CPU limit을 넘으면 **느려진다**(throttling). 메모리 limit은 피크에 여유를 두고, CPU limit은 느려짐을 감수할지로 판단한다."
      },
      {
        "title": "런타임 설정을 limit에 맞춘다",
        "body": "JVM 힙 최대치처럼 런타임이 스스로 잡는 메모리가 컨테이너 limit보다 크면, 애플리케이션은 정상이라고 믿는 사이에 커널이 죽인다."
      },
      {
        "title": "죽음을 지표로 본다",
        "body": "OOMKilled는 프로세스가 죽는 것이라 애플리케이션 로그에 남지 않는다. 재시작 횟수·종료 코드 137·CPU throttling 비율을 컨테이너 지표로 수집하고 알린다."
      }
    ]
  },
  {
    "type": "numbers",
    "title": "limit만 고치면 — Pod 4개로는",
    "domain": "autoscaling",
    "incident": true,
    "base": {},
    "change": {
      "resourceLimitsTuned": true
    },
    "changeLabel": "리소스 request/limit 재산정 (Pod 4개, 배포 안전장치 없음)",
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
    "body": "crash loop은 사라지고 처리 용량은 두 배 반으로 오르지만, Pod 4개로는 10배 트래픽에 한참 못 미쳐 지연·에러가 그대로다. 리소스 제한은 Pod 하나를 **용량으로 만드는** 조치이지, 용량을 **늘리는** 조치가 아니다."
  },
  {
    "type": "numbers",
    "title": "Pod도 늘리고 배포도 지켰는데 limit만 남았다면",
    "domain": "autoscaling",
    "incident": true,
    "base": {
      "podReplicas": 40,
      "rolloutSafeguardEnabled": true
    },
    "change": {
      "resourceLimitsTuned": true
    },
    "changeLabel": "Pod 40개·배포 안전장치 상태에서 리소스 제한 조정",
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
    ]
  },
  {
    "type": "text",
    "body": "**다른 개념과의 관계**\n\n- **오토스케일링** — 확장은 Pod 하나의 용량에 곱하는 수라서, 리소스 제한이 틀리면 늘린 Pod가 같은 비율로 죽는다. 위 두 번째 블록처럼 Pod 40개와 배포 안전장치가 있어도 OOM 하나가 남으면 포화에서 벗어나지 못한다.\n- **무중단 배포** — OOM으로 잃는 용량과 배포 중 잃는 용량은 서로 독립이라 곱해진다. 둘 중 하나만 고치면 나머지 손실이 그대로 남는다.\n- **관측 가능성** — OOMKilled는 애플리케이션 로그가 아니라 컨테이너 이벤트에 남는다. 그 지표가 없으면 '가끔 503이 난다'로만 보인다.\n- limit은 노드 안의 **격벽**이기도 하다. 상한이 없으면 메모리를 많이 쓰는 Pod 하나가 같은 노드의 무관한 서비스까지 쫓아낸다 — 결제 커넥션 풀 격리가 DB 커넥션에서 하는 일을 노드 자원에서 한다. 이웃 간섭은 엔진 밖의 위험이다."
  },
  {
    "type": "callout",
    "tone": "tradeoff",
    "title": "대가",
    "body": "측정에는 부하 테스트와 운영 지표를 꾸준히 보는 비용이 든다. 피크에 맞춘 limit은 평시에 남는 자원이고, 노드당 Pod 밀도가 떨어져 노드 비용이 오른다. 코드나 모델이 바뀌면 사용량도 바뀌므로 한 번 정한 값은 낡는다."
  }
]$blocks$::jsonb
where risk_key = 'MISSING_RESOURCE_LIMITS';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 눈에 띄는 것은 Redis인데, 사용자를 막는 것은 DB 쓰기다.",
    "alt": "오픈 순간 트래픽 20배가 두 갈래로 나뉜다. 조회는 느려진 Redis 때문에 hit ratio가 떨어져 DB 읽기로 새어 85%까지 오르지만 포화는 아니고, 발급 쓰기는 커넥션 풀과 DB 쓰기 용량을 넘겨 포화된다. 포화된 쓰기가 API 지연과 503 에러로 사용자에게 보인다",
    "mermaid": "flowchart LR\n  T[\"오픈 트래픽 20배\"] -- \"조회 70%\" --> R[\"Redis 지연 ×15\\nhit 95%→39%\"]\n  R -- \"miss\" --> DR[(\"DB 읽기\\n85%\")]\n  T -- \"발급 30%\" --> P[\"커넥션 풀\\n고갈\"]\n  P --> DW[(\"DB 쓰기\\n포화\")]\n  DW --> A[\"API\\nP95↑·503\"]\n  A --> U[\"사용자\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "coupon",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 5,
    "metrics": [
      "cacheHitRatio",
      "dbWriteLoad",
      "p95LatencyMs",
      "errorRate"
    ],
    "alerts": [
      {
        "metric": "dbWriteLoad",
        "op": "ABOVE",
        "threshold": 0.8,
        "label": "DB 쓰기 ≥ 80%"
      },
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.03,
        "label": "에러율 ≥ 3%"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 250,
        "label": "P95 ≥ 250ms"
      },
      {
        "metric": "cacheHitRatio",
        "op": "BELOW",
        "threshold": 0.8,
        "label": "hit ratio < 80%"
      }
    ],
    "scenarios": [
      {
        "label": "캐시 TTL만 10초 → 300초",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "INCREASE_CACHE_TTL"
          }
        ],
        "claims": [
          {
            "metric": "cacheHitRatio",
            "direction": "UP"
          },
          {
            "metric": "dbWriteLoad",
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
        ]
      },
      {
        "label": "Rate Limit → TTL 연장 → 풀 증설",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "STRENGTHEN_RATE_LIMIT"
          },
          {
            "second": 150,
            "action": "INCREASE_CACHE_TTL"
          },
          {
            "second": 180,
            "action": "INCREASE_DB_POOL"
          }
        ],
        "claims": [
          {
            "metric": "cacheHitRatio",
            "direction": "UP"
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
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤부터 시작한다. TTL만 늘리면 hit ratio 선은 올라오지만 DB 쓰기·지연·에러 선은 그대로다. Rate Limit을 걸자마자 쓰기 선이 꺾이고, 풀 증설에서 지연·에러가 평시로 돌아온다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "가장 시끄러운 지표가 병목은 아니다",
    "body": "에러율 다음으로 울리는 것은 캐시 경보다 — Redis 지연이 열다섯 배가 되고 hit ratio가 절반 아래로 떨어지니 눈에 가장 띈다. 그러나 엔진에서 지연·에러를 정하는 것은 읽기와 쓰기 중 **더 포화된 쪽**이고, 그것은 캐시와 무관한 발급 쓰기다. 캐시를 고치면 hit ratio는 돌아오지만 사용자가 겪는 선은 한 칸도 움직이지 않는다 — 위 그래프의 빨간 선이 그 결과다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **Rate Limit 먼저** — 들어오는 양을 DB가 감당할 만큼으로 자른다. 읽기·쓰기 양쪽이 한 번에 절반으로 줄어 쓰기 사용률이 포화에서 90% 구간으로 내려온다. 원인(버스트)에 가장 가까운 손이다.\n- **TTL 연장** — 남은 조회를 캐시에 더 오래 붙잡아 DB 읽기를 덜어 준다. 이 장애에서 사용자 지표를 바꾸지는 않지만, 쓰기를 고친 뒤 읽기가 다음 병목이 되지 않게 미리 비운다.\n- **풀 증설은 마지막** — 유입이 제한된 뒤에야 늘린 풀이 쓰기 사용률을 30%로 내려 지연·에러를 평시로 돌린다. Rate Limit 없이 풀부터 늘리면 쓰기는 숨을 돌려도, 느린 캐시에서 새어 나온 읽기가 85%로 남아 지연·에러가 가라앉지 않는다.\n\n선착순 쿠폰 설계 가이드가 같은 경로를 단계별로 따라가며, 각 단계에서 어떤 값이 어디까지 듣는지 보여 준다. 엔진은 처리량만 본다 — 재고 한 행에 동시 차감이 몰릴 때의 초과 발급이나 재시도로 인한 중복 발급은 가이드 마지막의 엔진 밖 위험으로 따로 다룬다."
  }
]$blocks$::jsonb
where domain = 'coupon';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — provider가 느려진 것이 시작이고, 재시도가 그것을 눈덩이로 만든다.",
    "alt": "프로모션으로 주문 이벤트가 10배로 늘고, 외부 provider 응답이 20ms에서 300ms로 느려져 컨슈머 처리량이 무너진다. 실패한 메시지의 즉시 재시도가 유입을 세 배로 부풀려 큐에 다시 들어오고, lag이 계속 쌓여 사용자는 알림을 몇 분 늦게 받는다",
    "mermaid": "flowchart LR\n  E[\"주문 이벤트 10배\"] --> Q[(\"큐\")]\n  Q --> C[\"컨슈머 4개\"]\n  C -- \"300ms씩 대기\" --> P[\"provider\\n지연 ×15\"]\n  P -- \"타임아웃\" --> RT[\"즉시 재시도\\n유입 ×3\"]\n  RT --> Q\n  Q -- \"lag 누적\" --> U[\"사용자\\n알림 지연\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "notification",
    "traits": {},
    "durationSeconds": 480,
    "stepSeconds": 5,
    "metrics": [
      "queueLag",
      "consumerThroughput",
      "externalDependencyLatencyMs",
      "p95LatencyMs"
    ],
    "alerts": [
      {
        "metric": "queueLag",
        "op": "ABOVE",
        "threshold": 5000,
        "label": "lag ≥ 5,000건"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 1200,
        "label": "전송 P95 ≥ 1.2초"
      },
      {
        "metric": "consumerThroughput",
        "op": "BELOW",
        "threshold": 100,
        "label": "처리량 < 100건/s"
      },
      {
        "metric": "externalDependencyLatencyMs",
        "op": "ABOVE",
        "threshold": 200,
        "label": "provider ≥ 200ms"
      }
    ],
    "scenarios": [
      {
        "label": "컨슈머만 4 → 12개",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "ADD_CONSUMERS"
          }
        ],
        "claims": [
          {
            "metric": "queueLag",
            "direction": "SAME"
          },
          {
            "metric": "consumerThroughput",
            "direction": "UP"
          },
          {
            "metric": "externalDependencyLatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "SAME"
          }
        ]
      },
      {
        "label": "Circuit Breaker → 백오프 → 컨슈머 증설",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "ENABLE_CIRCUIT_BREAKER"
          },
          {
            "second": 150,
            "action": "ADJUST_RETRY_BACKOFF"
          },
          {
            "second": 180,
            "action": "ADD_CONSUMERS"
          }
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
            "metric": "externalDependencyLatencyMs",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          }
        ]
      }
    ],
    "caption": "8분까지 본다. 컨슈머만 늘리면 처리량 선은 세 배가 되지만 lag 선의 기울기는 거의 그대로다. 올바른 순서에서는 3분에 전송 지연이 평시로 돌아오고도, 그때까지 쌓인 lag은 몇 분에 걸쳐 내려간다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "증상이 멎은 뒤에도 적체는 남는다",
    "body": "lag은 **지금의 속도**가 아니라 **지금까지의 합**이다. 세 번째 조치에서 처리량이 유입을 넘어서는 순간 새로 쌓이는 것은 멈추지만, 그때까지 쌓인 15만 건은 '처리량 − 유입'의 속도로만 줄어든다 — 그래프에서 지연 선은 바로 바닥으로 내려오는데 lag 선은 긴 비탈을 타는 이유다. 그 사이 사용자는 이미 지난 주문의 알림을 늦게 받는다. 회복을 '전송 지연이 정상'이 아니라 **'lag이 0'**으로 판정해야 하는 까닭이다. 또 lag 경보가 가장 먼저 울리고 provider 경보가 가장 늦게 울리기 때문에, 첫 경보만 보면 '손이 부족하다'로 읽혀 컨슈머부터 늘리게 된다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **Circuit Breaker 먼저** — 죽은 provider를 300ms씩 기다리지 않고 바로 실패시켜 컨슈머 하나의 처리량을 30배로 되살린다. 그래프의 provider 지연 선이 평시보다도 낮아지는 것은 provider가 나아서가 아니라 **기다리지 않기** 때문이다.\n- **백오프** — 실패한 메시지가 곧바로 큐에 돌아오지 않게 해, 세 배로 부풀었던 유입을 1.4배로 줄인다. 이 두 조치가 '처리할 일'과 '처리하는 속도'를 정상 쪽으로 돌려놓는다.\n- **컨슈머 증설은 마지막** — 이제야 늘린 컨슈머가 유입보다 빠르게 처리해, 사용률이 60% 아래로 내려오고 쌓인 lag을 비우기 시작한다. 먼저 늘리면 늘린 만큼 느린 provider를 동시에 더 기다릴 뿐이다.\n\n주문 알림 설계 가이드가 같은 순서를 단계별로 따라간다. 차단기로 빠르게 실패한 메시지가 어디로 가는지(재시도 큐·DLQ)와, 재시도가 같은 알림을 두 번 보내는 문제(Idempotent Consumer)는 엔진이 계산하지 않는 부분이라 가이드의 엔진 밖 위험에서 다룬다."
  }
]$blocks$::jsonb
where domain = 'notification';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — PG가 느려진 것은 결제 쪽인데, 막히는 것은 주문 전체다.",
    "alt": "외부 PG 응답이 50ms에서 1초로 느려져 디스패처 워커 네 개의 처리량이 초당 4건으로 떨어지고, 응답 유실 뒤 멱등성 없는 재시도가 처리할 일을 네 배로 부풀린다. outbox 적체가 결제와 주문이 함께 쓰는 커넥션 풀을 넘겨, 결제와 무관한 주문 API까지 지연과 에러를 낸다",
    "mermaid": "flowchart LR\n  PG[\"외부 PG\\n50ms→1초\"] --> D[\"디스패처 4개\\n초당 4건\"]\n  RT[\"멱등성 없는 재시도\\n일 ×4\"] --> O[(\"outbox 적체\")]\n  D --> O\n  O -- \"커넥션 점유\" --> POOL[\"공유 커넥션 풀\\n포화\"]\n  POOL --> A[\"주문 API\\nP95↑·에러\"]\n  A --> U[\"사용자\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "payment",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 3,
    "metrics": [
      "connectionPoolUsage",
      "queueLag",
      "externalDependencyLatencyMs",
      "p95LatencyMs"
    ],
    "alerts": [
      {
        "metric": "queueLag",
        "op": "ABOVE",
        "threshold": 2000,
        "label": "outbox ≥ 2,000건"
      },
      {
        "metric": "externalDependencyLatencyMs",
        "op": "ABOVE",
        "threshold": 500,
        "label": "PG ≥ 500ms"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 250,
        "label": "주문 P95 ≥ 250ms"
      },
      {
        "metric": "connectionPoolUsage",
        "op": "ABOVE",
        "threshold": 0.95,
        "label": "커넥션 풀 ≥ 95%"
      }
    ],
    "scenarios": [
      {
        "label": "디스패처만 4 → 20개",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "ADD_DISPATCHER_WORKERS"
          },
          {
            "second": 150,
            "action": "ADD_DISPATCHER_WORKERS"
          }
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
            "metric": "externalDependencyLatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "SAME"
          }
        ]
      },
      {
        "label": "멱등 재시도 → 풀 격리 → 디스패처 증설",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "ENABLE_IDEMPOTENT_PG_RETRY"
          },
          {
            "second": 150,
            "action": "ISOLATE_PAYMENT_POOL"
          },
          {
            "second": 180,
            "action": "ADD_DISPATCHER_WORKERS"
          }
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
            "metric": "externalDependencyLatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          }
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤부터 시작한다. 어느 쪽도 PG 지연 선은 움직이지 못한다. 올바른 순서에서는 2분 30초에 주문 지연이 평시로 돌아오지만, outbox 선은 끝까지 천천히 오른다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "주문이 살아났다고 결제가 끝난 것은 아니다",
    "body": "풀을 격리하는 순간 주문 API의 지연은 평시로 돌아온다. 하지만 그래프의 outbox 선은 그 뒤에도 내려오지 않고 계속 오른다 — PG는 여전히 1초씩 걸리고, 워커 12개로는 초당 30건의 결제 요청을 다 보내지 못한다. 격벽은 적체가 **번지는 것**을 막을 뿐 적체를 **줄이지는** 않는다. 주문 쪽 대시보드만 보고 장애를 닫으면, 결제 확정이 몇 분씩 밀린 주문이 쌓이고 있다는 사실을 놓친다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **멱등성 키를 붙인 재시도 먼저** — 응답이 유실된 승인을 몇 번이고 새 일처럼 다시 보내던 낭비가 사라져, 처리할 일이 네 배에서 실제 주문량으로 돌아온다. 재시도가 중복 결제를 만들 위험도 함께 닫힌다. 이것만으로 풀 사용률이 포화에서 70%대로 내려온다.\n- **풀 격리** — 남은 outbox 적체가 주문 처리의 커넥션을 빼앗지 못하게 가둔다. 주문 API가 평시로 돌아오는 것은 이 시점이다.\n- **디스패처 증설은 마지막** — 이제 적체의 기울기를 줄이는 일만 남았다. 먼저 늘리면 부풀려진 일을 더 빨리 PG에 보낼 뿐이고, 이미 느린 PG에 동시 호출만 늘린다.\n\n장애 패턴의 완화 목록은 격리를 먼저 두지만, 엔진에서 멱등성 없이 격리부터 하면 풀 사용률이 정확히 80% — 지연 구간의 경계 — 에 머문다. 두 순서 모두 같은 곳에 도착하지만, 여기서는 낭비를 먼저 없애는 쪽을 보여 준다. 주문/결제 설계 가이드가 이 과정을 단계별로 따라가고, PG가 완전히 멈출 때의 circuit breaker와 장애 뒤의 대사(reconciliation)는 엔진 밖 위험으로 다룬다."
  }
]$blocks$::jsonb
where domain = 'payment';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 세 가지 설계 구멍이 한 락의 처리 용량에서 만난다.",
    "alt": "예매 오픈으로 예약 요청이 15배 몰리고, 비원자적 재고 확인 때문에 경쟁에서 진 요청이 재시도해 부하가 세 배가 된다. 이 요청들이 공연 전체를 묶은 락 하나 앞에 줄을 서는데, 결제하지 않고 떠난 사용자의 홀드가 락의 처리 용량을 깎는다. 락이 포화되어 줄이 쌓이고 사용자는 lock wait timeout 실패와 매진처럼 보이는 화면을 본다",
    "mermaid": "flowchart LR\n  T[\"예매 오픈\\n요청 15배\"] --> R[\"비원자적 재고 확인\\n재시도로 부하 ×3\"]\n  R --> L[\"공연 전체 락 하나\"]\n  H[\"떠난 사용자의 홀드\\n5분간 점유\"] -- \"용량 90% 잠식\" --> L\n  L --> Q[\"락 대기 줄 누적\"]\n  Q --> U[\"사용자\\nlock wait timeout·'매진'\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "reservation",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 5,
    "metrics": [
      "dbWriteLoad",
      "errorRate",
      "p95LatencyMs",
      "queueLag"
    ],
    "alerts": [
      {
        "metric": "dbWriteLoad",
        "op": "ABOVE",
        "threshold": 0.8,
        "label": "락·쓰기 사용률 ≥ 80%"
      },
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.08,
        "label": "예약 에러율 ≥ 8%"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 150,
        "label": "P95 ≥ 150ms"
      },
      {
        "metric": "queueLag",
        "op": "ABOVE",
        "threshold": 14000,
        "label": "락 대기 ≥ 14,000건"
      }
    ],
    "scenarios": [
      {
        "label": "홀드 타임아웃만 30초로 줄이기",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "SHORTEN_HOLD_TIMEOUT"
          }
        ],
        "claims": [
          {
            "metric": "dbWriteLoad",
            "direction": "DOWN"
          },
          {
            "metric": "errorRate",
            "direction": "SAME"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "queueLag",
            "direction": "DOWN"
          }
        ]
      },
      {
        "label": "좌석 단위 락 → 원자적 재고 확인 → 홀드 단축",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "ENABLE_FINE_GRAINED_LOCKING"
          },
          {
            "second": 150,
            "action": "ENABLE_ATOMIC_INVENTORY_CHECK"
          },
          {
            "second": 180,
            "action": "SHORTEN_HOLD_TIMEOUT"
          }
        ],
        "claims": [
          {
            "metric": "dbWriteLoad",
            "direction": "DOWN"
          },
          {
            "metric": "errorRate",
            "direction": "DOWN"
          },
          {
            "metric": "p95LatencyMs",
            "direction": "DOWN"
          },
          {
            "metric": "queueLag",
            "direction": "DOWN"
          }
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤부터 시작한다. 홀드만 줄이면 사용률 선은 내려가도 여전히 포화라 에러·지연 선은 그대로이고, 대기 줄은 조금 느리게 쌓일 뿐 계속 늘어난다. 맨 아래 대기 줄 선이 언제 0으로 돌아오는지 보자."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "지표가 돌아와도 줄 선 요청은 남아 있다",
    "body": "원자적 재고 확인까지 켜면 락 사용률이 100% 아래로 내려와 에러·지연은 곧바로 크게 줄지만, 그때까지 쌓인 대기 줄은 **남는 용량만큼만** 빠진다 — 여유가 적으면 거의 줄지 않는다. 홀드 단축으로 용량이 크게 늘어난 뒤에야 줄이 빠르게 0으로 돌아온다. 에러율이 내려간 순간을 '복구'로 선언하면, 아직 기다리는 사용자를 놓친다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **좌석 단위 락 먼저** — 서로 다른 좌석을 고른 요청이 더는 서로를 기다리지 않아 처리 용량이 20배가 된다. 그래도 재시도 낭비(×3)와 유령 홀드 때문에 아직 포화다.\n- **원자적 재고 확인** — 확인과 확정 사이 경쟁에서 진 요청의 재시도가 사라져 부하가 3분의 1로 준다. 여기서 사용률이 100% 아래로 내려온다. 이 단계는 수치보다 **중복 예약을 막는다**는 점이 더 중요하다 — 엔진은 재시도 낭비만 계산하고 한 좌석이 두 번 팔리는 사고는 숫자로 나타내지 않는다.\n- **홀드 타임아웃 조정은 마지막** — 락 경합을 그대로 둔 채 홀드만 줄이면 위 빨간 선처럼 아무것도 풀리지 않고, 정상 사용자의 결제 도중 홀드가 풀리는 대가만 남는다. Drill의 이 조치는 30초로 한 번에 줄이지만, 실제 값은 **결제에 걸리는 시간**에서 정한다 — 예약 시스템 설계 가이드는 2분을 고르고 30초가 왜 지나친지 따라간다."
  }
]$blocks$::jsonb
where domain = 'reservation';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 방아쇠는 외부 API지만, 피해를 키우는 것은 재시작 방식이다.",
    "alt": "100만 건 정산 배치가 진행 60% 지점에서 외부 정산 API 지연으로 청크 하나가 실패한다. 체크포인트가 없어 처음부터 다시 돌면서 이미 처리한 60만 건을 다시 처리하고, 재처리가 멱등하지 않아 이미 반영된 정산이 중복으로 쓰인다. 결과로 마감 시간이 밀리고 가맹점에 중복 정산이 생긴다",
    "mermaid": "flowchart LR\n  A[\"정산 API 지연\\n40ms → 600ms\"] --> F[\"60% 지점\\n청크 실패\"]\n  F -- \"체크포인트 없음\" --> R[\"처음부터 재실행\\n60만 건 재처리\"]\n  R -- \"멱등성 없음\" --> D[\"이미 반영된 정산\\n중복 반영\"]\n  R --> S[\"처리량 하락\\n마감 지연\"]\n  D --> M[\"가맹점\\n중복 지급\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 틀린 대응, 올바른 완화",
    "domain": "batch-settlement",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 5,
    "metrics": [
      "externalDependencyLatencyMs",
      "errorRate",
      "queueLag",
      "consumerThroughput"
    ],
    "alerts": [
      {
        "metric": "externalDependencyLatencyMs",
        "op": "ABOVE",
        "threshold": 200,
        "label": "정산 API ≥ 200ms"
      },
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.25,
        "label": "중복·실패 레코드 ≥ 25%"
      },
      {
        "metric": "queueLag",
        "op": "ABOVE",
        "threshold": 450000,
        "label": "재처리 대기 ≥ 45만 건"
      },
      {
        "metric": "consumerThroughput",
        "op": "BELOW",
        "threshold": 8800,
        "label": "처리량 < 8,800건/초"
      }
    ],
    "scenarios": [
      {
        "label": "청크 크기만 10,000 → 1,000",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "REDUCE_CHUNK_SIZE"
          }
        ],
        "claims": [
          {
            "metric": "externalDependencyLatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "errorRate",
            "direction": "SAME"
          },
          {
            "metric": "queueLag",
            "direction": "SAME"
          },
          {
            "metric": "consumerThroughput",
            "direction": "DOWN"
          }
        ]
      },
      {
        "label": "체크포인트 재개 → 멱등 재처리 → 청크 조정",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "ENABLE_CHECKPOINT_RESTART"
          },
          {
            "second": 150,
            "action": "ENABLE_IDEMPOTENT_RECONCILIATION"
          },
          {
            "second": 180,
            "action": "REDUCE_CHUNK_SIZE"
          }
        ],
        "claims": [
          {
            "metric": "externalDependencyLatencyMs",
            "direction": "SAME"
          },
          {
            "metric": "errorRate",
            "direction": "DOWN"
          },
          {
            "metric": "queueLag",
            "direction": "DOWN"
          },
          {
            "metric": "consumerThroughput",
            "direction": "UP"
          }
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤부터 시작한다. 정산 API 지연 선은 어떤 대응에도 움직이지 않는다. 청크만 줄이면 처리량 선만 더 내려가고, 올바른 완화에서는 세 번째 조치에서 처리량이 한 번 꺾이는 것도 보자."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "방아쇠는 끝까지 그대로다",
    "body": "가장 먼저 울리는 경보는 정산 API 지연이고, 어떤 대응도 그 선을 내리지 못한다 — 외부 API는 우리가 고칠 수 있는 대상이 아니다. 고치는 것은 **실패 한 번의 비용**이다: 어디서부터 다시 하는가(체크포인트), 다시 해도 결과가 같은가(멱등성). 방아쇠에만 매달려 API 쪽 회복을 기다리면, 다음 실패에서도 60만 건을 다시 돌리고 중복을 다시 만든다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **체크포인트 재개 먼저** — 실패한 청크 하나(1만 건)만 다시 하므로 재처리 대기가 60만 건에서 1만 건으로, 처리량이 거의 평시로 돌아온다. 중복 반영도 재처리 범위만큼으로 줄지만 0은 아니다.\n- **멱등한 정산 재처리** — 다시 처리되는 레코드가 이미 반영됐는지 확인해 중복을 0으로 만든다. 중복 정산 0건은 이 배치가 포기하지 않는 요구사항이다.\n- **청크 크기는 마지막에** — 1,000건으로 줄이면 실패 시 버리는 양은 더 줄지만 청크마다 붙는 커밋 오버헤드로 처리량이 체크포인트 직후보다 낮아진다. 그래서 청크만 줄이는 대응은 처리량만 잃는 틀린 대응이고, 올바른 순서에서도 이것은 미세 조정이다 — 정산 배치 설계 가이드는 체크포인트·멱등성을 켠 뒤 청크를 다시 키우는 단계까지 따라간다.\n\n외부 정산 API 결과와 우리 정산 테이블이 서로 맞는지 확인하는 **대사**는 엔진 밖의 영역이다."
  }
]$blocks$::jsonb
where domain = 'batch-settlement';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 수요가 늘어난 순간, 떠 있는 Pod 중 일하는 Pod가 줄어든다.",
    "alt": "바이럴로 트래픽이 10배가 되자 메모리 limit이 낮은 Pod가 OOM으로 재시작을 반복하고, 겹친 롤링 배포가 남은 Pod 일부를 더 빼서 실제 처리 용량이 수요의 몇 분의 일로 떨어지고, 로드밸런서가 503과 긴 지연을 사용자에게 돌려준다",
    "mermaid": "flowchart LR\n  T[\"트래픽 10배\\n(바이럴)\"] --> M[\"메모리 사용량↑\"]\n  M -- \"limit 초과\" --> O[\"OOMKilled\\n재시작 반복\"]\n  R[\"롤링 배포\\n(안전장치 없음)\"] --> C\n  O --> C[\"일하는 Pod\\n급감\"]\n  C --> L[\"로드밸런서\\n503·P95↑\"]\n  L --> U[\"사용자\"]"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, Pod만 늘리기, 원인부터 고치기",
    "domain": "autoscaling",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 2,
    "metrics": [
      "consumerThroughput",
      "queueLag",
      "p95LatencyMs",
      "errorRate"
    ],
    "alerts": [
      {
        "metric": "errorRate",
        "op": "ABOVE",
        "threshold": 0.01,
        "label": "에러율(503) ≥ 1%"
      },
      {
        "metric": "queueLag",
        "op": "ABOVE",
        "threshold": 0.5,
        "label": "재시작 반복 Pod ≥ 1개"
      },
      {
        "metric": "consumerThroughput",
        "op": "BELOW",
        "threshold": 100,
        "label": "처리 용량 < 100 rps"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 300,
        "label": "P95 ≥ 300ms"
      }
    ],
    "scenarios": [
      {
        "label": "Pod만 4 → 40개",
        "tone": "bad",
        "actions": [
          {
            "second": 120,
            "action": "SCALE_OUT_REPLICAS"
          }
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
        ]
      },
      {
        "label": "리소스 제한 조정 → 배포 안전장치 → Pod 증설",
        "tone": "good",
        "actions": [
          {
            "second": 120,
            "action": "TUNE_RESOURCE_LIMITS"
          },
          {
            "second": 150,
            "action": "ENABLE_ROLLOUT_SAFEGUARD"
          },
          {
            "second": 180,
            "action": "SCALE_OUT_REPLICAS"
          }
        ],
        "claims": [
          {
            "metric": "consumerThroughput",
            "direction": "UP"
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
        ]
      }
    ],
    "caption": "두 대응 모두 2분 뒤부터 시작한다. Pod만 늘리면 처리 용량 선은 오르지만 수요에 못 미쳐 지연·에러 선은 그대로이고, 재시작하는 Pod 선(적체 / 대기)은 오히려 뛴다. 올바른 순서에서는 앞의 두 조치 동안 지연·에러가 움직이지 않다가 마지막 증설에서 한꺼번에 내려온다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "고친 조치가 바로 보이지 않을 수도 있다",
    "body": "리소스 제한을 고치면 재시작하는 Pod는 곧바로 사라지지만, 지연·에러 선은 Pod를 늘리기 전까지 꿈쩍하지 않는다 — Pod 4개로는 어차피 10배 수요를 받을 수 없기 때문이다. 반대로 Pod만 늘리면 처리 용량 선은 바로 올라 **뭔가 듣는 것처럼 보인다.** 사용자 지표만 보고 판단하면 맞는 조치를 버리고 틀린 조치를 반복하기 쉽다 — 둘은 곱해지는 관계라 순서가 아니라 **둘 다**가 필요하다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **리소스 제한 조정 먼저** — Pod가 죽지 않아야 용량이 된다. 이 상태에서 늘린 Pod는 6할이 다시 OOM으로 재시작한다.\n- **배포 안전장치 다음** — readiness probe와 PDB로 배포 중에도 떠 있는 Pod 전부가 요청을 받게 한다. 여기까지는 Pod 수가 그대로라 용량이 수요에 못 미친다.\n- **Pod 증설은 마지막** — 같은 36개를 더해도, 앞의 두 조치 뒤에는 처리 용량이 수요의 두 배가 되어 지연·에러가 평시로 돌아온다. 처음에 늘렸다면 대부분이 낭비다.\n\n실제 HPA는 지표를 모으고 새 Pod가 모델을 올리는 데 시간이 걸리지만, 엔진은 증설이 즉시 용량이 된다고 본다 — 실제 회복은 이 그래프보다 늦다. 실시간 추천 API 설계 가이드가 같은 순서를 단계별로 따라간다."
  }
]$blocks$::jsonb
where domain = 'autoscaling';

update failure_patterns set blocks = $blocks$[
  {
    "type": "diagram",
    "caption": "장애가 번지는 경로 — 원인은 인프라가 아니라 방금 나간 변경이고, 피해는 카나리 비율을 따라 커진다.",
    "alt": "결함 있는 새 버전이 카나리로 배포되고, 롤아웃 시계가 관찰 시간마다 비율을 두 배로 올려 결함 버전이 받는 트래픽이 커지고, 그 비율만큼 결제 요청이 실패하며 클라이언트 재시도로 지연도 늘어나 사용자가 결제 실패를 겪는다",
    "mermaid": "flowchart LR\n  D[\"결함 버전\\n2.14.0\"] --> C[\"카나리 10%\"]\n  C -- \"60초마다 ×2\" --> P[\"20% → 40%\\n→ 80% → 100%\"]\n  P --> E[\"비율만큼\\n결제 실패\"]\n  E -- \"클라이언트 재시도\" --> L[\"P95↑\"]\n  E --> U[\"사용자\"]\n  L --> U"
  },
  {
    "type": "timeline",
    "title": "시간에 따라 — 방치, 계속 진행, 멈추고 되돌리기",
    "domain": "deployment",
    "traits": {},
    "durationSeconds": 300,
    "stepSeconds": 2,
    "metrics": [
      "errorRate",
      "p95LatencyMs",
      "availability",
      "trafficRps"
    ],
    "alerts": [
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
        "metric": "availability",
        "op": "BELOW",
        "threshold": 0.9,
        "label": "가용성 < 90%"
      },
      {
        "metric": "p95LatencyMs",
        "op": "ABOVE",
        "threshold": 400,
        "label": "P95 ≥ 400ms"
      }
    ],
    "scenarios": [
      {
        "label": "배포를 앞당겨 빨리 끝내기 (승격 두 번)",
        "tone": "bad",
        "actions": [
          {
            "second": 100,
            "action": "CONTINUE_ROLLOUT"
          },
          {
            "second": 130,
            "action": "CONTINUE_ROLLOUT"
          }
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
          },
          {
            "metric": "trafficRps",
            "direction": "SAME"
          }
        ]
      },
      {
        "label": "일시 중지 → 롤백",
        "tone": "good",
        "actions": [
          {
            "second": 100,
            "action": "PAUSE_ROLLOUT"
          },
          {
            "second": 150,
            "action": "ROLLBACK"
          }
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
          },
          {
            "metric": "trafficRps",
            "direction": "SAME"
          }
        ]
      }
    ],
    "caption": "이 도메인만 시계가 상태를 바꾼다 — 아무것도 하지 않아도 60초마다 카나리 비율이 두 배가 되어 에러율이 계단처럼 오른다. 승격을 앞당기면 마지막 상태는 방치와 같지만 80%·100%에 각각 50초 먼저 닿고, 일시 중지는 계단을 멈추고 롤백은 결함 버전 트래픽을 0으로 만든다. 트래픽 선은 셋 다 그대로다."
  },
  {
    "type": "callout",
    "tone": "warning",
    "title": "기다리는 시간이 곧 피해다",
    "body": "다른 장애는 방치하면 나쁜 상태에 머물지만, 카나리는 방치하면 **스스로 나빠진다.** 경보가 하나씩 울리는 시각이 승격 시각과 겹치는 것도 그 때문이다. 원인 변경을 찾는 동안에도 롤아웃 시계는 돌아가므로, 첫 조치는 원인 분석이 아니라 확산을 멈추는 것(일시 중지)이고 다음이 롤백이다. 승격을 앞당겨 '빨리 끝내기'는 피해를 줄이지 않고 최대 피해(100%, 에러율 60%)에 더 빨리 닿게 할 뿐이다."
  },
  {
    "type": "text",
    "body": "**올바른 순서가 이런 이유**\n\n- **일시 중지 먼저** — 판단할 시간을 번다. 다음 승격이 오기 전에 누를 수 있는 가장 가벼운 조치지만, 이미 카나리가 받는 비율의 실패는 그대로 이어진다.\n- **롤백 다음** — 결함 버전으로 가는 트래픽을 0으로 만드는 유일한 조치다. 에러율·지연이 평소로 돌아온다.\n- **수정 버전을 다시 카나리로** — 원인 변경(이 시나리오에서는 PriceCalculator 교체)을 찾아 고친 뒤 작은 비율부터 다시 내보낸다. 재배포 액션은 엔진에 없어 그래프에는 없다.\n\n서버·DB 증설은 이 도메인의 선택지에 아예 없다 — 트래픽 선이 보여 주듯 용량은 원인이 아니다. 설계에 자동 롤백 임계(`autoRollbackErrorPct`)를 두면 사람이 누르기 전에 롤아웃 시계가 스스로 되돌린다. 카나리 배포 설계 가이드가 롤백 뒤 시작 비율을 1%로 낮추는 데까지 따라간다."
  }
]$blocks$::jsonb
where domain = 'deployment';
