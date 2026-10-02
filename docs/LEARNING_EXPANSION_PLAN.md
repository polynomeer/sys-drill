# Learning 확장 계획안 — 읽는 곳에서 실험하는 곳으로

> 작성 2026-09-30. 원본: [`archive/learning-feature-proposal.md`](archive/learning-feature-proposal.md) (21절).
>
> 관련 문서 — 1차 확장 기획 [LEARNING_COMMUNITY_PLAN.md](LEARNING_COMMUNITY_PLAN.md)(L1 개념 라이브러리 · L2 학습 경로 · L3 훈련 진입, 전부 구현됨), [ADR-0039](adr/0039-learning-concepts-live-in-the-database-not-frontend-constants.md), 형제 계획안 [OBSERVABILITY_UI_PLAN.md](OBSERVABILITY_UI_PLAN.md) · [DRILLS_EXPANSION_PLAN.md](DRILLS_EXPANSION_PLAN.md) · [COMMUNITY_EXPANSION_PLAN.md](COMMUNITY_EXPANSION_PLAN.md).
>
> 이 문서는 **계획안**이며 아직 구현되지 않았습니다. 슬라이스 번호는 1차 기획의 L1~L3에 이어 **L4부터** 매깁니다.

---

## 1. 원본 제안 한 줄 요약

Learning을 강의·문서 모음이 아니라 **"지식 체계 + 실험실 + 개인화"** 로 만들어 `Learning → Lab → Drill → 약점 발견 → Learning`의 닫힌 루프를 만들자는 제안입니다. 핵심 철학은 **"설명을 읽어서 이해하는 것이 아니라 값을 바꿔서 현상을 발견하게 한다"** 입니다.

## 2. 지금 Learning의 실제 상태

1차 기획(L1~L3)이 전부 끝나 "빈 탭"은 벗어났습니다.

| 구성 | 위치 | 상태 |
|---|---|---|
| 개념 25개 | `learning_concepts` (V45/V46), PK = 채점 엔진의 `risk_key` | 요약·왜 문제인가·증상·패턴·대가·관련 도메인/액션/챌린지 |
| 개념 상세 | `/learning/[riskKey]` | 블록 단위 점진적 공개(Enter), 읽기 시간, 끝에 "직접 해보기"(관련 Drill 개요로) |
| 학습 경로 | `GET /learning/path`, `LearningPathPanel` | 가장 약한 카테고리의 개념 최대 3개, 상태 `NOT_STARTED / IN_PROGRESS / ADDRESSED`를 읽기 시점 파생 |
| 트랙 | `/tracks`, `/tracks/[domain]` | 도메인별 Drill·Build·개념·인증·랭킹 |
| 약점 | `skill_profiles.weaknesses` (riskKey → 횟수) | 카테고리 그룹핑. **개념별 숙련 상태는 없음** — "읽음"은 localStorage 플래그뿐 |
| 확인 문제 (Round B14, 이 계획 작성 후 추가) | `GET /learning/concepts/{riskKey}/quiz` | 개념의 해결 패턴을 고르는 1문항. 정답·오답 모두 다른 개념의 실제 패턴에서 생성, 서버 기록 없음 |

**비어 있는 연결** (조사로 확인):

- 리포트·포스트모템의 리스크 지적에서 `/learning/{riskKey}`로 가는 링크가 **없습니다.** 약점을 발견하는 바로 그 화면에서 Learning으로 못 갑니다.
- 프로필·대시보드의 약점 목록도 개념으로 링크되지 않습니다.
- 개념의 `relatedChallenges`는 특정 챌린지가 아니라 일반 `/bridge`로만 갑니다. **더 근본적으로 `/bridge`는 rate-limiter(Python/TS)만 풀 수 있습니다** — 스텁이 프론트 상수 두 개뿐이라 나머지 5개 챌린지(queue · circuit-breaker · distributed-lock · retry-backoff · event-bus)는 CLI(`challenges/`)로만 풀 수 있고 웹 진입점이 없습니다(2026-10-01 확인).
- 인터랙티브 랩·시각화·예측 문제·지식 맵·마이크로 빌드는 **전무**합니다.

원본 항목별 대조:

| 원본 | 현재 | 판정 |
|---|---|---|
| 1 최상위 구조(My Learning / Map / Topics / Components / Patterns / Labs) | 경로 + 카테고리별 개념 + 트랙 | **부분 — IA는 L7·L8 결과에 맞춰 조정** |
| 2 Knowledge Map / 3 Knowledge Node 7단계 | 카테고리 평면 목록 | **채택 — L7** |
| 4 Understand | 블록 공개로 구현됨 | 유지 |
| 5 Visualize / 17 분산 시스템 시각 랩 | 없음 | **보류** — 엔진 근거 없는 별도 위젯(§6) |
| 6 Interactive Explainer / 7 Experiment / 9 Break / 19 Predict Before Run | 없음 | **채택 — L5** (핵심) |
| 8 Implement (Micro Build) | Build 챌린지는 단계별 진행 가능(B6), 링크가 일반 `/bridge` | **축소 채택 — L4** (특정 챌린지·단계로 딥링크) |
| 10 Compare / 11 Decision Lab | 없음 | **보류** |
| 12 Failure Encyclopedia | 개념은 "설계 리스크" 중심, 장애 패턴 목록 없음 | **채택 — L8** |
| 13 Pattern Library (When NOT to use) | 개념의 `patterns`/`tradeoffs` | **축소 채택 — L8** (필드 추가) |
| 14 Real Incident Case Studies | 없음 | **보류** — 사실 검증·저작권 |
| 15 Capacity Planning Lab | 없음 | **채택 — L6** (Drill M2와 공유) |
| 16 Query/Index Lab | 없음 | **보류** — 새 엔진 필요 |
| 18 "Why did this happen?" | 없음 | **채택 — L9** |
| 20 Misconception Detection | 없음 | **채택 — L10** (LLM 없이 액션 패턴으로) |
| 21 Learning ↔ Drill 강한 연결 | 한 방향(개념 → Drill)만 | **채택 — L4** (우선) |

---

## 3. 이 계획안이 취하는 입장

1. **랩은 새 시뮬레이터를 만들지 않고 기존 엔진을 호출한다.** `RuleBasedSimulationEngine`의 도메인 함수는 결정론적이고 마이크로초 단위입니다. 세션 없이 `DesignTraits`만 넣어 호출하면 그것이 곧 랩입니다. 이렇게 하면 **Learning에서 본 현상과 Drill에서 겪는 현상이 같은 수식에서 나옵니다** — 1차 기획이 "학습 화면과 피드백 화면이 같은 어휘를 쓴다"를 강점으로 꼽은 것의 연장입니다.
2. **엔진이 표현하지 못하는 현상은 랩으로 만들지 않는다.** 원본의 Connection Pool 예시("500으로 올리면 오히려 처리량 하락")는 엔진 수식이 그 부작용을 실제로 계산해야 성립합니다. 랩마다 엔진이 그 현상을 표현하는지 먼저 확인하고, 표현하지 못하면 랩을 포기하거나 **도메인 테스트와 함께** 엔진을 고칩니다. 랩을 위해 엔진과 다른 수식을 프론트에 두지 않습니다.
3. **정답표를 저장하지 않는다.** 예측 문제의 정답은 엔진 실행 결과입니다. 콘텐츠 작성자가 "DB 부하 ↑"를 손으로 적어 두면 엔진이 바뀔 때 조용히 틀립니다.
4. **읽음 ≠ 숙련.** 개념 숙련 상태는 Drill 결과에서만 파생합니다. 문서를 끝까지 읽었다는 localStorage 플래그는 편의 기능으로만 씁니다.

### 하지 않는 것 (1차 기획의 안티골 유지 + 추가)

| 하지 않음 | 이유 |
|---|---|
| 동영상·강의 CMS | PRD §6 "강의보다 시도-피드백 루프" |
| 외부 아티클 큐레이션 | 1차 기획과 동일 |
| 퀴즈 문제은행·플래시카드·간격 반복 | 제품 원칙(PRD §5)은 "사용법이 아니라 판단"을 판다. 이미 있는 확인 문제(B14, 개념당 1문항 자동 생성)는 유지하되 늘리지 않는다. 예측 문제(L5)는 암기가 아니라 가설 검증이므로 별개 |
| AI 튜터 자유 대화 | 턴 단위 LLM 비용. 힌트는 이미 Mentor 역할이 세션 안에서 담당 |
| 실제 기업 장애 사례 재구성 | 공개 postmortem의 사실과 교육용 변형을 구분·유지하는 비용 |

---

## 4. 슬라이스

| # | 슬라이스 | 선행 | 새 저장소 | 비용 |
|---|---|---|---|---|
| L4 | Drill ↔ Learning 양방향 연결 (+ L4-b `/bridge` 일반화) | 없음 | `build_challenges.starter_code` | 낮음~중 — **가장 먼저** |
| L5 | 인터랙티브 랩 (Experiment · Break · Predict) | 없음 (시간 개념 랩은 O0-a) | 랩 정의(콘텐츠) | 중 |
| L6 | Capacity Lab (규모 추정) | 없음 | 문제 정의(콘텐츠) | 낮음 |
| L7 | 개념 숙련 상태 + Knowledge Map | 없음 | 개념 간 연결(콘텐츠) | 중 |
| L8 | 장애 패턴 사전 + Bad Fixes / When NOT to use | 없음 | 개념 필드 + `failure_patterns` | 콘텐츠 |
| L9 | 진단 퍼즐 ("무슨 일이 일어났나") | O1·O3 컴포넌트 | 없음 | 낮음 |
| L10 | 오개념 감지 (액션 패턴) | L5, L8 | 규칙(설정) | 중 |

### L4 — Drill ↔ Learning 양방향 연결

새 백엔드 없이 링크만 잇는 작업이며, 가치 대비 비용이 가장 좋습니다.

- **리포트·포스트모템**: 리스크 지적마다 `개념 보기 →` 링크(`/learning/{riskKey}`), 리포트 끝에 "이번에 놓친 개념" 묶음.
- **프로필·대시보드 약점**: 약점 항목을 개념으로 링크.
- **특정 Build 챌린지·단계로 딥링크**: 개념의 `relatedChallenges`가 일반 `/bridge`가 아니라 해당 챌린지(필요하면 해당 단계)로 가게. 원본의 Micro Build("10~30분짜리 짧은 구현")는 새 챌린지를 만들지 않고 **기존 챌린지의 한 단계**를 진입점으로 쓰는 것으로 대신합니다 — 단계별 진행(B6)이 이미 있습니다.
- **L4-b `/bridge` 일반화 (확정 2026-10-01 — 범위 추가)**: 위 딥링크가 성립하려면 7개 챌린지 전부를 웹에서 풀 수 있어야 합니다. `build_challenges.starter_code` 컬럼을 추가해 `challenges/<slug>/`의 스텁을 마이그레이션으로 시딩하고(서버 채점이 이미 DB의 테스트 스크립트를 쓰는 것과 같은 "콘텐츠는 DB" 원칙, ADR-0006), `/bridge?challenge=<slug>`로 진입합니다. 프론트의 rate-limiter 스텁 상수 두 개는 DB 값으로 대체하고, 파일과 DB 스텁이 어긋나지 않는지 테스트로 고정합니다.
- **개념 상세에 "이 개념을 연습하는 방법" 레일**: 랩(L5 이후) → Build 단계 → Drill 개요 → 관련 토론([COMMUNITY_EXPANSION_PLAN.md](COMMUNITY_EXPANSION_PLAN.md) C7). 원본 §21의 `Micro Lab 5분 / Build 30분 / System Drill / Incident Drill` 구성.

**완료 기준**: 리포트의 모든 riskKey 지적이 링크를 갖는지(개념 없는 키가 없음은 `LearningConceptCatalogTest`가 이미 보장), `tsc`/`lint`/`build`, 실브라우저로 리포트 → 개념 → Build 단계 이동.

### L5 — 인터랙티브 랩

- **API**: `POST /learning/labs/{labId}/run` — 요청 `{traits, incidentActive}`, 응답 `SystemState`(O0-a 이후에는 짧은 시계열). 세션·저장 없음. 엔진 호출만 하므로 비용은 무시할 수준이고 LLM을 부르지 않습니다.
- **랩 정의(콘텐츠, Flyway 시딩)**: `learning_labs(id, slug, kind, risk_key, domain, title, spec jsonb, display_order)` — `kind`는 `ENGINE`(L5)·`CAPACITY`(L6)·`PUZZLE`(L9 고정 퍼즐이 생기면). ENGINE의 `spec`은 `{knobs: [{trait, min, max, step}], watch: [metric…], predict: [metric…]}`. 어느 trait을 슬라이더로 줄지만 정하고, 결과는 엔진이 냅니다. 서버는 knob 값을 범위·도메인 trait 목록으로 검증한 뒤 엔진을 부릅니다.
- **한 화면 세 모드** (원본의 Experiment · Break · Predict를 하나로):
  1. *Predict* — 값을 바꾸기 전에 지표별 `↑ / ≈ / ↓` 예측
  2. *Experiment* — 슬라이더 조정 → 실행 → 예측과 실제 비교(`Cache Hit ↓ ✓, P99 ↑ ✗`). 비교는 변화량 부호 + 허용 오차 밴드
  3. *Break* — `인시던트 켜기` 토글로 같은 설정이 장애 상황에서 어떻게 되는지
- **첫 랩 후보** — 기존 도메인 trait으로 표현 가능한 것만:

  **확정(2026-10-01) — 엔진 수식을 직접 확인한 결과**:

| 랩 | 도메인 · knob | 엔진이 표현하는 현상 (확인함) |
|---|---|---|
| 어느 병목인가 | coupon `cacheTtlSeconds`, `dbPoolSize` | 인시던트 중 TTL 10초면 hit ratio 0.39 → DB 읽기 사용률 0.85. **pool을 아무리 늘려도 읽기 병목은 그대로**(pool은 쓰기 용량에만 비례) — "pool은 클수록 좋은가" 대신 "내가 고친 게 실제 병목인가" |
| Retry Storm | notification `retryBackoffMultiplier`, `circuitBreakerEnabled`, `consumerCount` | 재시도 증폭 = 2 / backoff, CB는 fast-fail로 consumer 처리량 회복 — 셋을 다 해야 회복 |
| Cache Stampede | product-browsing `cachePolicySplit`, `singleFlightEnabled`, `readReplicaCount` | single-flight 없으면 miss 1건이 DB read 10건 |
| 격리와 멱등 재시도 | payment `idempotentPgRetryEnabled`, `paymentPoolIsolated`, `dispatcherWorkers` | 멱등성 없는 재시도가 유효 부하 4배, 격리 없으면 적체가 주문 처리 풀로 번짐 |
| 락 세분화와 유령 홀드 | reservation `fineGrainedLockingEnabled`, `holdTimeoutSeconds`, `atomicInventoryCheckEnabled` | 홀드 타임아웃이 용량을 깎는 정도 |
| 청크 크기 트레이드오프 | batch-settlement `chunkSize`, `checkpointingEnabled`, `idempotentReconciliationEnabled` | **진짜 양방향 트레이드오프** — 작을수록 재처리는 줄지만 커밋 오버헤드로 처리량 하락 |
| Scale-out의 한계 | autoscaling `podReplicas`, `resourceLimitsTuned`, `rolloutSafeguardEnabled` | Pod만 늘리면 OOM·롤아웃 패널티가 곱해져 거의 개선 안 됨 |

  **초안의 "Connection Pool은 클수록 좋은가" 랩은 뺐습니다** — 엔진에서 DB 쓰기 용량이 pool 크기에 정비례해 "너무 크면 오히려 나빠진다"를 표현하지 않습니다(§3-2). 엔진을 랩에 맞춰 고치지 않습니다. 7개 도메인 전부에 랩 1개씩이 되고, 각 랩은 그 도메인 인시던트와 같은 수식을 씁니다.
- 랩 진행 기록은 저장하지 않습니다. 예측 적중 여부는 화면 안에서만 보여줍니다(숙련은 Drill 결과로만 — §3-4).

### L6 — Capacity Lab

- `DAU · 사용자당 요청 · 객체 크기 · 읽기/쓰기 비율`을 주고 `평균/피크 RPS · 일/년 스토리지 · 대역폭 · 캐시 크기`를 계산하게 합니다. 즉시 자릿수 기준으로 판정(`|log10(추정/참값)| ≤ 0.3`), 이어서 "DAU 10M → 100M" 같은 변형.
- 참값은 문제 정의의 입력으로 계산하는 **순수 함수**라 정답표가 필요 없습니다. 문제는 `learning_labs`(kind=`CAPACITY`)에, 계산 함수는 서버 코드에(`AVG_RPS`, `PEAK_RPS`, `STORAGE_PER_DAY` … 고정 목록). `POST /learning/labs/{slug}/check`가 판정.
- 입력 컴포넌트와 판정 함수를 Drill M2(규모 추정)와 **공유**합니다 — 여기서 연습하고 Drill에서 쓰는 구조.

### L7 — 개념 숙련 상태 + Knowledge Map

- **숙련 상태** (저장 없이 파생, [ADR-0011](adr/0011-derived-values-are-never-persisted.md)):

| 표시 | 규칙 |
|---|---|
| ○ 미시작 | 이 개념의 관련 도메인 세션 이력 없음 |
| ⚠ 약점 | 최근 N개 세션 안에 이 riskKey 지적 있음 |
| ◐ 연습함 | 관련 도메인 완료 + 최근 미지적, 단 서로 다른 변형 1개뿐 |
| ● 신뢰 | 서로 다른 변형 2개 이상에서 미지적 |

  "변형 수"는 Drill M10(변형 기반 숙련 신뢰도)과 **같은 계산**을 씁니다. 원본 §25의 "한 번 맞혔다고 Mastered 처리하지 않는다". 기존 학습 경로의 `NOT_STARTED / IN_PROGRESS / ADDRESSED`는 이 규칙의 부분집합이므로 한 함수로 합칩니다.
- **Knowledge Map**: 개념에 `related_concepts`(선행/연관 엣지, `learning_concepts.related_concepts` JSONB 컬럼) 콘텐츠를 추가하고, 카테고리 6개 × 개념 25개를 그래프로 그립니다. 렌더링은 이미 의존성에 있는 @xyflow/react. 노드에 위 숙련 표시.
- **개념 페이지의 단계 레일**: 원본의 Knowledge Node 7단계(Understand → Visualize → Experiment → Implement → Break → Apply → Verify) 중 **이 개념에 실제로 있는 것만** 표시합니다 — Understand(블록) · Experiment/Break(L5 랩) · Implement(L4 Build 단계) · Apply(Drill) · Verify(숙련 상태). 없는 단계를 빈 칸으로 두지 않습니다.
- 원본의 거대한 토픽 트리(Networking, Security, CDN…)는 만들지 않습니다. 지도는 **채점 엔진이 아는 25개 개념**의 지도입니다 — 채점과 무관한 노드는 숙련을 파생할 근거가 없습니다.

  **확정(2026-10-02, Round E18 구현 시)**:
  - "최근 N개"는 **N=1(가장 최근 관련 세션)**. 기존 학습 경로가 최근 1회만 보는 이유(고친 사용자가 몇 판 더 "약점"으로 남지 않게)가 그대로 유효하고, "한 번 맞힌 것"에 대한 방어는 창 크기가 아니라 변형 수(2개 이상)가 맡는다. 경로의 3상태는 4단계에서 사상한다: 미시작→NOT_STARTED, 약점→IN_PROGRESS, 연습함·신뢰→ADDRESSED.
  - 변형의 정체성은 `도메인:꼬리설계 변형 키`(Round E10에서 FOLLOWUP 진입 시 고정되는 키). 그 이전 세션과 변형이 하나뿐인 시나리오는 `도메인:base` 하나로 친다 — 과거 이력이 신뢰를 부풀리지 않는 보수적 선택. 다른 도메인에서 통과한 것도 다른 변형으로 센다(개념은 여러 도메인에 걸친다).
  - 엣지는 `learning_concepts.related_concepts`에 `[{key, relation}]`로, PREREQUISITE는 **의존하는 개념 쪽**에(key → 이 개념), RELATED는 한쪽에만 저장한다(V62, 선행 14 + 연관 12). 카탈로그 테스트가 고립 개념 없음·없는 키 없음·선행 순환 없음을 강제한다.
  - 지도 배치는 카테고리 6개를 3열×2단으로 — 6열을 한 줄에 놓으면 화면 폭에 맞출 때 글자가 읽히지 않는다.
  - 단계 레일은 이해(블록을 끝까지 읽음) → 실험(랩) → 구현(Build) → 적용(Drill, 관련 세션 완료 시 ✓) → 검증(숙련 4단계, 통과한 변형 수). "Break"는 별도 단계가 아니라 엔진 랩에 포함돼 있어 따로 두지 않는다.

### L8 — 장애 패턴 사전 + Bad Fixes

- 지금 개념 25개는 "설계 리스크"(멱등성 누락, 동시성 제어 누락…)입니다. 원본의 Failure Encyclopedia는 **장애 패턴**(Cache Stampede, Consumer Lag, Connection Exhaustion…)이라 축이 다릅니다.
- 장애 패턴은 이 제품에서 **인시던트 도메인 7개와 1:1**입니다. 그래서 개념 테이블을 억지로 넓히지 않고(PK가 riskKey라 맞지 않음) `failure_patterns`를 도메인 키로 따로 둡니다: `증상 · 전형적 지표 · 전형적 로그 · 흔한 원인 · 잘못된 대응 · 완화 · 예방 · 관련 Drill · 관련 개념`.
- **"잘못된 대응(Bad Fixes)"의 원천은 이미 있습니다** — ARCHITECTURE §6.4의 액션 부작용 표와 엔진의 부작용 수식. 콘텐츠를 새로 지어내는 것이 아니라 엔진이 이미 계산하는 부작용을 문장으로 옮기는 작업입니다.
- 개념(설계 리스크)에는 `bad_fixes`, `when_not_to_use` 필드를 추가합니다(원본 §13 Pattern Library의 핵심). 25개 × 2필드 콘텐츠 작업.

  **확정(2026-10-02, Round E19 구현 시)**: 장애 패턴의 잘못된 대응은 `[{fix, why}]` — "왜 틀렸나"가 이 콘텐츠의 요점이라 문장을 둘로 나눴고, 각 항목은 엔진의 액션 부작용(예: 컨슈머 증설은 provider가 느린 동안 컨슈머당 처리량이 그대로라 동시 호출만 늘림, Pod 증설은 OOM·롤아웃 패널티가 곱해져 거의 개선 없음)과 맞는지 엔진 수식으로 확인했다. 전형적 로그는 O4 로그 생성기(Round E17)의 템플릿 문구와 같게 써서 Wargame Logs 탭에서 본 줄을 사전에서 다시 알아볼 수 있게 했다. 개념의 `bad_fixes`는 문자열 목록, `when_not_to_use`는 문장 하나. 개념 상세는 자신을 포함하는 장애 패턴 도메인 목록을 함께 내려 양방향으로 잇는다.

### L9 — 진단 퍼즐

- 엔진으로 인시던트 중간 상태(시드·trait을 조금씩 흔든 변형)를 만들고 **지표만** 보여준 뒤 "무슨 일이 일어났나?" — 장애 패턴 7개 중 선택 + "무엇을 먼저 확인하겠는가". 답을 고른 뒤 L8 장애 패턴 페이지로.
- 화면은 관측 계획안의 Overview(O1)와 Service Map(O3) 컴포넌트를 그대로 재사용합니다. 5분짜리, 로그인 없이도 가능.
- 한계: 패턴이 7개뿐이라 반복하면 외워집니다. 그래서 목표는 "신호 → 패턴" 대응을 처음 익히는 입문용이고, 숙련 판정에는 쓰지 않습니다.

### L10 — 오개념 감지

- 원본은 AI로 판단 패턴을 찾자고 했지만, 1차는 **LLM 없이 액션 이력으로** 합니다. 규칙 예: "그 도메인에서 부작용이 큰 액션을 첫 액션으로 3개 세션 이상 선택" → `잠재적 오해: 'DB pool을 늘리면 해결된다'` 카드.
- 규칙은 설정 데이터(도메인 × 액션 × 문장 × 연결 랩). 카드는 관련 L5 랩과 L8 Bad Fixes로 연결합니다.
- 답안 텍스트의 판단 패턴을 LLM으로 찾는 버전은 비용 신호를 본 뒤.

---

## 5. 정보 구조 (목표)

```
Learning
├ 내 학습          ← 경로(L2) + 오개념 카드(L10) + 약점 개념
├ 지식 맵          ← L7
├ 개념 25          ← 카테고리별 목록(L1) · 상세에 단계 레일(L7) · 랩(L5)
├ 장애 패턴 7      ← L8
└ 랩               ← L5 랩 목록 · Capacity Lab(L6) · 진단 퍼즐(L9)
```

"System Components(PostgreSQL/Redis/Kafka…)" 축은 두지 않습니다 — 제품이 파는 것은 도구 사용법이 아니라 판단이고(PRD §5), 컴포넌트별 내용은 개념과 장애 패턴에 이미 흩어져 있습니다.

## 6. 보류 항목과 재개 조건

| 항목 | 막는 것 | 재개 조건 |
|---|---|---|
| Visualize 위젯 (Consistent Hashing, Raft, Replication Lag…) | 엔진 근거가 없는 프론트 전용 시뮬레이터 — 채점과 무관하고 위젯마다 따로 만들어야 함 | L5 랩 사용률이 높을 때, 가장 많이 틀리는 개념 1개로 파일럿 |
| Compare (Kafka vs RabbitMQ) / Decision Lab | 비교표는 의견 콘텐츠라 유지보수 비용이 큼. Decision Lab은 사실상 Drill M4(설계 방어)·M7(가정 붕괴)의 축소판 | Drill M4·M7 이후, 그 흐름을 짧게 자른 버전으로 |
| Query/Index Lab | 쿼리 플래너 모델이 필요 | 별도 엔진 기획 시 |
| Real Incident Case Studies | §3 하지 않는 것 | 사실 검증 절차가 생길 때 |

## 7. ADR 후보

1. **랩은 세션 없는 엔진 직접 호출이고, 예측 정답은 저장하지 않는다** (L5) — 작성함: [ADR-0047](adr/0047-learning-labs-call-the-simulation-engine-and-store-no-answer-key.md).
2. **장애 패턴은 개념 테이블이 아니라 인시던트 도메인 키로 따로 둔다** (L8) — 되돌리기 쉬운 스키마 선택이라 ADR 조건 1(되돌리기 비용)을 만족하지 않을 가능성이 높습니다. 착수 시 다시 판단합니다.

## 8. 성공 지표

| 지표 | 왜 |
|---|---|
| 리포트 → 개념 클릭률 (L4) | 약점 발견 지점에서 학습으로 넘어가는가 |
| 랩 실행 후 관련 Drill 시작률 | 실험이 훈련으로 이어지는가 |
| 랩 예측 적중률의 반복 간 변화 | mental model이 교정되는가 |
| 오개념 카드가 뜬 사용자의 해당 액션 재선택률 | L10이 행동을 바꾸는가 |
| ⚠ → ● 전환 개념 수 / 사용자 / 월 | Learning 전체의 결과 지표 |
