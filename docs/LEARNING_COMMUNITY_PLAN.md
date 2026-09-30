# Learning · Community 확장 기획서

> 작성 2026-09-30. 대상 독자: 이 저장소에서 구현을 이어갈 사람.
>
> 관련 문서 — 제품 정의는 [PRD.md](PRD.md), 구현 기준은 [ARCHITECTURE.md](ARCHITECTURE.md), 결정 기록은 [ADR](adr/README.md), 이전에 보류된 항목의 맥락은 [DRILLS_SIMULATION_VISION.md](DRILLS_SIMULATION_VISION.md)입니다.
>
> 이 문서는 **설계안**이며 아직 구현되지 않았습니다. 확정된 결정이 아니라 선택지와 권고를 담고 있고, §10에 아직 열려 있는 질문을 모아 뒀습니다.

---

## 1. 배경 — 지금 두 화면의 실제 상태

헤더의 4개 탭(Home · Drills · Learning · Community) 중 **둘이 사실상 비어 있습니다.**

| | 현재 구현 | 백엔드 | 개인화 | 문제 |
|---|---|---|---|---|
| **Learning** | [`learning/page.tsx`](../frontend/src/app/learning/page.tsx) 65줄 — `designGuidance.ts`(7개 도메인 가이드)와 `riskLabels.ts`(25개 개념)를 그대로 나열 | 없음 | 없음 | 누가 보든 같은 화면. 내가 뭘 틀렸는지와 무관 |
| **Community** | [`community/page.tsx`](../frontend/src/app/community/page.tsx) 30줄 — GitHub Issues 링크 한 장 | 없음 | 없음 | 앱을 떠나야 함. 사실상 빈 탭 |

두 파일의 주석이 상태를 정확히 설명합니다 — UI/UX 계획에서 **P2(콘텐츠·네트워크 효과 확장)로 분류돼 의도적으로 미뤄진 자리표시자**입니다. 당시 판단은 합리적이었습니다: 인앱 CMS나 게시판을 지어내는 대신 이미 검증된 자산(세션 중 쓰이는 가이드 문구, 실제로 돌아가는 GitHub Issues)을 재사용했습니다.

문제는 그 이후 **핵심 루프가 훨씬 두꺼워졌는데 이 두 탭만 그대로**라는 점입니다. 지금은 세션 하나가 루브릭 7항목 점수, 놓친 개념, 실무 리스크, 꼬리질문, MTTD/MTTR, 액션 타임라인, 권장 변경사항을 만들어 냅니다. 그 대부분이 **세션이 끝나는 순간 사라집니다.**

---

## 2. 문제 정의 — 왜 하필 이 둘인가

SysDrill의 학습 루프는 `[Build] → [Design] → [Tail Design] → [Wargame] → [Response] → [Evaluation] → [Retrospective]`입니다. 마지막 단계인 **Retrospective가 세션 안에 갇혀 있습니다.**

세션이 끝난 사용자에게 남는 질문은 두 가지입니다.

| 사용자의 질문 | 현재 답할 수 있는가 | 담당 |
|---|---|---|
| "그래서 **내가 뭘 공부해야 하지?**" | 부분적 — SkillProfile이 약한 역량 카테고리와 추천 도메인을 계산하지만, 개념 설명·학습 순서·진행 상태가 없다 | **Learning** |
| "내 대응이 **잘한 건가?**" | 못 함 — 절대 점수(78/100)와 MTTR(1분 55초)이 있지만 **비교 대상이 없다** | **Community** |

즉 두 기능은 장식이 아니라 **루프를 닫는 마지막 조각**입니다.

- **Learning** = 여러 세션에서 나온 신호를 *한 사람의 시간축*으로 잇는 것
- **Community** = 같은 문제를 푼 *여러 사람의 축*으로 잇는 것

이 관점이 중요한 이유: 둘 다 **새 콘텐츠를 창작하는 일이 아니라, 이미 생성 중인 데이터를 집계·연결하는 일**이라는 뜻이기 때문입니다. 구현 비용이 생각보다 낮고, 제품의 차별점(실측 기반 평가)을 직접 강화합니다.

---

## 3. 이 기획이 하지 않는 것 (Anti-goals)

명시해 두지 않으면 범위가 무한히 늘어나는 항목들입니다.

| 하지 않음 | 이유 |
|---|---|
| 범용 SNS·타임라인·팔로우 | 제품 목적과 무관. 운영 부담만 큼 |
| 동영상·강의 CMS | "강의보다 시도-피드백 루프를 우선한다"는 PRD §6 원칙과 정면 충돌 |
| ~~순위 중심 리더보드~~ → **도입하되 구조로 왜곡을 막는다** | 초안은 측정 왜곡(쉬운 시나리오 반복)을 이유로 제외했으나 도입으로 결정([ADR-0042](adr/0042-drill-score-and-ranking-reward-breadth-and-difficulty-not-repetition.md)). 반복이 이득이 되지 않는 점수 구조로 설계한다 — §6.6 |
| 외부 아티클 큐레이션 | 유지보수 비용 대비 가치 낮음. 개념 설명은 **채점 기준과 같은 어휘**로 직접 쓴다 |
| 실시간 채팅 | 기존 Game Day 관전 채팅도 폴링으로 충분했다([ADR-0026](adr/0026-game-day-spectating-is-scoped-by-shared-org-membership-and-still-defers-websocket.md)) |

---

## 4. 재사용 자산 — 새로 만들 필요가 없는 것

이 기획의 핵심 전제입니다. 아래는 **이미 동작 중**입니다.

| 자산 | 위치 | Learning/Community에서의 쓰임 |
|---|---|---|
| riskKey 25개 → 역량 카테고리 6개 매핑 | `RuleEvaluator.categoryByRiskKey` | 개념 라이브러리의 **분류 체계 그대로** |
| 한국어 개념 라벨 25개 | `frontend/src/lib/riskLabels.ts` | 개념 카드 제목 |
| 도메인별 설계 가이드 7종 | `frontend/src/lib/designGuidance.ts` | 도메인 개요 |
| 약점 집계 · 추천 카테고리/도메인 | `GET /skill-profile` | 학습 경로의 **입력 신호** |
| 세션별 점수·항목별 점수·리스크 | `reports`, `evaluations`, `evaluation_risk_flags` | 벤치마크 분포의 원천 |
| MTTD/MTTR·액션 타임라인 | `GET /sessions/{id}/postmortem` | 대응 속도 벤치마크 |
| 공개 시나리오 풀 | `GET /marketplace/scenarios` ([ADR-0031](adr/0031-marketplace-scenarios-join-the-public-pool-with-no-payment-in-v1.md)) | Community의 시나리오 탭 |
| 도메인별 인증 상태 (live 계산) | `GET /certifications/{userId}` ([ADR-0032](adr/0032-certification-is-live-computed-and-scoped-to-official-domains.md)) | 공개 프로필 |
| 조직 커리큘럼 (순서 있는 advisory 학습 경로) | `organization_curriculum_steps` ([ADR-0030](adr/0030-onboarding-curriculum-is-advisory-full-replace-and-counts-retroactive-completion.md)) | **개인 학습 경로의 참조 구현** |
| 세션 채팅 테이블 패턴 | `session_chat_messages` | 토론 스레드의 참조 구현 |

**새 6개 도메인 개념을 발명하지 않습니다.** 채점 엔진이 이미 쓰는 25개 riskKey와 6개 카테고리를 그대로 씁니다 — 학습 화면과 피드백 화면이 **같은 어휘**를 쓰는 것이 이 제품의 강점입니다.

---

## 5. Learning 확장 설계

### 5.1 정보 구조: 현재 → 목표

```
현재                              목표
Learning                          Learning
├ 도메인 가이드 7개 (평면)         ├ 내 학습 경로          ← 개인화, SkillProfile 기반
└ 개념 25개 (평면)                ├ 역량별 개념 라이브러리  ← 6 카테고리 × 25 개념 계층
                                  │   └ 개념 상세          ← 정의/증상/해결/트레이드오프
                                  └ 도메인 레퍼런스 7개     ← 기존 유지
```

### 5.2 L1 — 개념 라이브러리

25개 개념을 6개 역량 카테고리 아래로 계층화하고, 각 개념에 **채점 기준과 연결된 구조화된 설명**을 붙입니다.

**개념 스키마**

| 필드 | 설명 | 예시 (`MISSING_IDEMPOTENCY`) |
|---|---|---|
| `riskKey` | 채점 엔진의 키 (PK) | `MISSING_IDEMPOTENCY` |
| `category` | 6개 역량 중 하나 | `CONCURRENCY_CONSISTENCY` |
| `label` | 한국어 이름 | 멱등성 처리 |
| `summary` | 한 줄 정의 | 같은 요청이 두 번 도착해도 결과가 한 번과 같아야 한다 |
| `whyItMatters` | 왜 실무에서 문제가 되는가 | 클라이언트 재시도·큐 재배달·사용자 더블클릭은 *정상 동작*이다. 방어가 없으면 중복 발급·이중 결제가 된다 |
| `symptoms` | **어떤 지표로 드러나는가** | 발급 수 > 재고, DB 유니크 제약 위반 급증, 결제 건수와 주문 건수 불일치 |
| `patterns` | 해결 패턴 (복수) | 멱등키 + 저장소 TTL / DB 유니크 제약 / 조건부 UPDATE |
| `tradeoffs` | 대가 | 키 저장소가 추가 의존성이 된다. TTL이 짧으면 늦은 재시도를 못 막고 길면 메모리를 먹는다 |
| `relatedDomains` | 이 개념이 등장하는 시나리오 도메인 | `coupon`, `payment` |
| `relatedActions` | 관련 워게임 액션 | `ENABLE_IDEMPOTENT_PG_RETRY` |
| `relatedChallenges` | 관련 Build 과제 | `rate-limiter` |

`symptoms`가 특히 중요합니다 — 이 제품은 "개념을 외우는 곳"이 아니라 "지표를 보고 알아채는 곳"이므로, 개념 설명도 **관측 가능한 증상**과 묶여야 Wargame 화면의 경험과 이어집니다.

**개인화 연결**: 개념 카드에 내 이력 배지를 붙입니다 — `내가 3번 놓친 개념`, `최근 세션에서 지적받음`. 데이터는 `GET /skill-profile`의 `weaknessesByCategory`에 이미 있습니다.

### 5.3 L2 — 개인 학습 경로

조직 커리큘럼([ADR-0030](adr/0030-onboarding-curriculum-is-advisory-full-replace-and-counts-retroactive-completion.md))의 **개인판**입니다. 그 ADR이 정한 세 가지 성질을 그대로 따릅니다: *advisory*(강제하지 않음), *전체 교체*, *소급 완료 인정*.

**생성 규칙** (전부 읽기 시점 계산, 저장 없음 — [ADR-0011](adr/0011-derived-values-are-never-persisted.md))

```
1. SkillProfile.recommendedCategory  → 가장 약한 역량 카테고리
2. 그 카테고리의 riskKey 중 빈도 상위 3개  → 학습할 개념
3. 각 개념의 relatedDomains / relatedChallenges → 실제로 할 훈련
4. 각 단계의 상태를 기존 이력에서 파생:
     완료   — 해당 도메인 세션을 COMPLETED 했고 그 riskKey 지적이 없음
     진행중 — 세션은 했지만 여전히 같은 riskKey를 지적받음
     미시작 — 해당 도메인 세션 이력 없음
```

**화면**

```
내 학습 경로                                     가장 약한 역량: 동시성·정합성
─────────────────────────────────────────────────────────────────
✓ 1  멱등성 처리          개념 읽기 · 선착순 쿠폰       완료 (2회 세션, 최근 미지적)
▶ 2  동시성 제어          개념 읽기 · 예약 시스템       진행 중 — 최근 세션에서 또 지적됨
○ 3  트랜잭션 경계 분리    개념 읽기 · 주문/결제         미시작
```

"추천"이 아니라 **"왜 이걸 추천하는지"가 보이는 것**이 요점입니다 — 각 항목에 근거(몇 번 지적받았는지, 어느 세션에서)를 함께 표시합니다.

### 5.4 L3 — 개념에서 훈련으로 진입

개념 상세 화면의 끝은 항상 **행동**입니다.

- `이 개념이 나오는 시나리오 시작하기` → `POST /sessions` 바로 진입
- `관련 Build 과제 풀기` → Bridge/Build 진입
- `내 과거 답안에서 이 지적 보기` → 해당 submission 피드백으로 딥링크

### 5.5 콘텐츠를 어디에 둘 것인가

**결정: DB에 두고 Flyway 마이그레이션으로 시딩합니다** ([ADR-0039](adr/0039-learning-concepts-live-in-the-database-not-frontend-constants.md), 2026-09-30).

근거는 저장소의 기존 선례입니다 — 시나리오·Build 과제·프롬프트 템플릿이 전부 그렇게 관리됩니다([ADR-0002](adr/0002-content-via-migrations-not-admin-crud.md), [ADR-0006](adr/0006-config-as-data.md)). 개념 설명도 같은 종류의 콘텐츠이고, 서버가 "내 약점" 배지를 붙여 내려주려면 어차피 백엔드가 알아야 합니다.

다만 현재 `riskLabels.ts`는 프론트 상수입니다. 이관 시 **단일 출처는 DB**로 하고 프론트 상수는 제거합니다 — 지금은 `riskLabels.ts`의 25개 키와 `RuleEvaluator.categoryByRiskKey`의 25개 키가 손으로 동기화되고 있어, 이 이관은 그 이중 관리를 없애는 일이기도 합니다.

riskKey → 카테고리 **매핑 자체는 코드에 남깁니다** — 그것은 콘텐츠가 아니라 채점 엔진의 분류 로직이고 평가 파이프라인이 런타임에 씁니다. `learning_concepts.category`는 시드 시점에 그 매핑으로부터 채우고, 둘이 어긋나지 않는지 검증하는 테스트를 함께 둡니다.

### 5.6 API 초안

| Method / Path | 응답 |
|---|---|
| `GET /learning/concepts` | 카테고리 6개 + 그 아래 개념 요약 목록. 인증 시 내 약점 카운트 포함 |
| `GET /learning/concepts/{riskKey}` | 개념 상세 전체 필드 + 관련 시나리오/과제 + 내 과거 지적 이력 |
| `GET /learning/path` | 개인 학습 경로 (읽기 시점 계산, 저장 없음) |

```jsonc
// GET /learning/path
{
  "recommendedCategory": "CONCURRENCY_CONSISTENCY",
  "categoryLabel": "동시성 · 정합성",
  "rationale": "최근 5개 세션에서 이 역량의 지적이 7회로 가장 많습니다",
  "steps": [
    { "riskKey": "MISSING_IDEMPOTENCY", "label": "멱등성 처리",
      "status": "COMPLETED", "weaknessCount": 0,
      "evidence": "선착순 쿠폰 2회 완료, 최근 세션에서 미지적",
      "scenarioId": "...", "challengeSlug": "rate-limiter" }
  ]
}
```

**새 테이블은 `learning_concepts` 하나뿐**입니다. 학습 경로·진행 상태는 전부 기존 데이터에서 파생합니다.

---

## 6. Community 확장 설계

### 6.1 C1 — 벤치마크 (가장 가치가 높고 비용이 낮음)

세션 리포트와 포스트모템에 **분포 대비 내 위치**를 더합니다.

```
장애 대응 결과                          나        커뮤니티(n=48)
─────────────────────────────────────────────────────────────
MTTD (최초 대응까지)                 1분 42초    p50 2분 10초  p90 4분 30초
MTTR (복구까지)                      1분 55초    p50 3분 05초  p90 6분 20초
초기 설계 점수                          78        p50 71        p90 86
잘못된 액션 수                            0        p50 1
```

- **데이터는 이미 전부 있습니다.** `postmortems`(MTTD/MTTR), `reports`/`evaluations`(점수), `applied_actions`(액션 수). 집계 쿼리만 추가하면 됩니다.
- **저장하지 않고 읽기 시점 집계**합니다([ADR-0011](adr/0011-derived-values-are-never-persisted.md) 일관). 부하가 문제가 되면 그때 명시적 캐시를 붙입니다.
- **표본이 적으면 표시하지 않습니다** — `n < 5`면 분포를 숨기고 "아직 비교할 표본이 부족합니다"로 대체합니다. 3명이 푼 시나리오에서 "상위 33%"는 정보가 아니라 노이즈입니다.
- **개인 식별 없음**. 집계값만 노출합니다.
- 같은 `scenario_version_id` 안에서만 비교합니다 — 시나리오가 바뀌면 난이도가 달라지므로.

이것이 [DRILLS_SIMULATION_VISION.md](DRILLS_SIMULATION_VISION.md)의 *Community 벤치마크* / *Expert Replay* 항목 중 **당장 구현 가능한 부분**입니다. "전문가 대응과 비교"는 전문가 답안이라는 새 자산이 필요하지만, "커뮤니티 중앙값과 비교"는 기존 데이터만으로 됩니다.

### 6.2 C2 — 풀이 공유 (Writeups)

세션 완료 후 **명시적으로 공개를 선택**하면 다른 사람이 볼 수 있습니다.

| 항목 | 결정 |
|---|---|
| 기본값 | **비공개**. ARCHITECTURE §13 "사용자 설계 답안은 기본 비공개, 공유는 명시적 옵션" 준수 |
| 공개 단위 | 세션 하나 (설계 답안 + 점수 + 포스트모템). 부분 공개 없음 — 맥락이 끊기면 학습 가치가 사라짐 |
| 신원 | 닉네임 또는 익명 중 선택 |
| 공개 후 | 철회 가능(비공개 전환). 이미 받은 댓글은 함께 숨김 |
| **열람 조건** | **그 시나리오를 완료한 사용자만** ([ADR-0041](adr/0041-shared-writeups-are-visible-only-to-users-who-completed-that-scenario.md)). 미완료자에게는 "먼저 직접 풀어보세요"와 시작 버튼을 노출 |
| 정렬·필터 | 시나리오별, 점수대별, 최신순 |
| 남용 대응 | 신고 → `PLATFORM_ADMIN` 검토 → 숨김. 자동 필터는 v1 범위 밖 |

새 테이블 대신 **`sessions`에 `visibility` 컬럼 추가**를 권합니다 — 시나리오가 이미 `visibility`로 같은 문제를 푼 선례가 있습니다([ADR-0024](adr/0024-custom-scenarios-coexist-with-migration-content-and-scope-cut-to-design-only.md)).

### 6.3 C3 — 시나리오 마켓플레이스 통합

`GET /marketplace/scenarios`가 이미 동작하는데 **Community 탭과 분리돼 있어 발견되지 않습니다.** 별도 `/marketplace` 경로를 Community의 한 탭으로 흡수합니다.

추가할 것: 사용 횟수, 평균 점수(난이도 신호), 제작자 표시. 별점은 넣지 않습니다 — 표본이 적을 때 왜곡이 크고, "평균 점수"가 더 객관적인 난이도 신호입니다.

### 6.4 C4 — 공개 프로필

`GET /certifications/{userId}`가 이미 `userId`로 타인 조회를 허용합니다. 이를 공개 프로필 화면으로 만듭니다.

표시: 닉네임, 도메인별 인증 배지, 완료한 도메인 수, 공개한 풀이 목록. **표시하지 않음**: 실패 이력, 약점 프로필(본인만), 이메일.

### 6.5 C5 — 시나리오별 토론 (인앱)

**결정: 인앱으로 구현합니다** ([ADR-0040](adr/0040-in-app-discussion-threads-replace-the-github-issues-link.md), 2026-09-30). 초안은 모더레이션 비용과 빈 게시판 위험을 이유로 보류를 권고했으나, 맥락이 결정적이라는 판단으로 뒤집었습니다 — "이 시나리오에서 62점을 받았는데 왜 이 설계가 감점인가" 같은 질문은 내 답안·지적·지표가 함께 있는 곳에서만 제대로 물을 수 있고, GitHub Issues에서는 그 맥락이 전부 끊깁니다.

| 항목 | 결정 |
|---|---|
| 단위 | `scenario_version_id` 하나당 스레드 하나. **자유 주제 게시판이 아닙니다** |
| 열람 | 누구나. 단 풀이를 인용한 댓글은 [ADR-0041](adr/0041-shared-writeups-are-visible-only-to-users-who-completed-that-scenario.md)의 완료자 조건을 따릅니다 |
| 작성 | 로그인 사용자 |
| 전송 | 폴링 ([ADR-0026](adr/0026-game-day-spectating-is-scoped-by-shared-org-membership-and-still-defers-websocket.md) 연장) |
| 스키마 | `session_chat_messages`를 참조 구현으로 삼아 `scenario_discussions` 신설 |
| 모더레이션 | 신고 → `PLATFORM_ADMIN` 숨김. 자동 필터는 v1 밖 |
| 빈 스레드 대응 | 시나리오 단위라 "빈 게시판"이 아니라 "아직 질문 없음". 상단에 그 시나리오의 벤치마크(C1)를 함께 노출해 화면이 비지 않게 함 |

### 6.6 C6 — Drill Score · 티어 · 랭킹

**결정: 도입합니다** ([ADR-0042](adr/0042-drill-score-and-ranking-reward-breadth-and-difficulty-not-repetition.md), 2026-09-30). 초안의 안티골을 대체하며, 우려했던 측정 왜곡은 **점수 수식 자체로** 막습니다.

#### 점수 정의

```
DrillScore = Σ  (공식 도메인 d의 최고 세션 점수) × 난이도가중(d)
             d
난이도가중:  EASY 1.0 · MEDIUM 1.2 · HARD 1.5
```

현재 공식 도메인 7개(EASY 1 · MEDIUM 2 · HARD 4) 기준 **상한 940점**입니다.

| 왜곡 시나리오 | 이 수식에서 벌어지는 일 |
|---|---|
| 같은 시나리오를 반복해 점수 올리기 | **최고점만** 반영 → 반복에 보상 없음 |
| 쉬운 시나리오만 파기 | EASY 도메인은 1개, 만점이어도 100점 = 상한의 약 10% |
| 안 해 본 도메인 회피 | 도메인 합이라 미수행 도메인은 0점 — 넓이가 자동 보상 |
| 자작 쉬운 시나리오로 찍어내기 | **공식 시나리오만 집계** (조직·사용자 제작 제외) |

계산 재료는 전부 이미 있습니다 — `CertificationService`가 쓰는 "공식 시나리오 필터 + 도메인별 최고점" 로직을 그대로 재사용합니다. **저장하지 않고 읽기 시점에 계산**합니다([ADR-0011](adr/0011-derived-values-are-never-persisted.md)).

#### 티어 (설정값, 실제 분포 보고 조정)

| 티어 | DrillScore |
|---|---|
| 훈련생 (Trainee) | 0 – 99 |
| 운영자 (Operator) | 100 – 299 |
| 대응자 (Responder) | 300 – 499 |
| 설계자 (Architect) | 500 – 699 |
| 수석 (Principal) | 700+ |

#### 랭킹 보드 4종

| 보드 | 기준 | 왜 필요한가 |
|---|---|---|
| **종합** | DrillScore | 넓이 + 난이도 + 깊이 |
| **도메인별** | 도메인 최고 점수 | 특정 도메인 강자를 드러냄 |
| **대응 속도** | 시나리오별 MTTR | 설계와 다른 축 — 점수에 섞지 않고 분리 |
| **최근 30일 상승폭** | 기간 내 DrillScore 증가량 | 누적만 보면 초기 사용자가 영구히 유리 |

#### 표시 원칙

- **티어와 백분위를 앞세우고 절대 순위는 부차적으로** 둡니다. "3등"은 소수에게만 의미가 있지만 "상위 30% · 대응자"는 모두에게 다음 목표를 줍니다.
- 랭킹 참여는 **기본 참여 + 프로필에서 숨기기 가능**. 노출되는 것은 닉네임·티어·점수이며 답안은 포함되지 않습니다.
- 점수 옆에 **계산 근거**를 항상 표시합니다 (도메인별 최고점 × 가중치 내역). 설명할 수 없는 점수는 신뢰받지 못합니다.

### 6.7 API 초안

| Method / Path | 설명 |
|---|---|
| `GET /community/benchmarks/{scenarioVersionId}` | MTTD/MTTR/점수 분포 (n < 5면 분포 없이 응답) |
| `GET /community/writeups` | 공개된 풀이 목록 (시나리오·점수대 필터) |
| `GET /community/writeups/{sessionId}` | 공개 풀이 상세 |
| `PUT /sessions/{id}/visibility` | 내 세션 공개/비공개 전환 |
| `GET /community/profiles/{userId}` | 공개 프로필 (인증 배지 + 공개 풀이) |
| `POST /community/writeups/{sessionId}/report` | 신고 |
| `GET /community/scenarios/{scenarioVersionId}/discussions` | 시나리오 토론 스레드 조회 (폴링) |
| `POST /community/scenarios/{scenarioVersionId}/discussions` | 댓글 작성 |
| `POST /community/discussions/{id}/report` | 댓글 신고 |
| `GET /community/rankings?board=overall\|domain\|response\|recent` | 랭킹 보드 (티어·백분위 포함) |
| `GET /community/rankings/me` | 내 DrillScore·티어·백분위와 **계산 근거 내역** |

---

## 7. 프라이버시 · 안전 · 남용

| 위험 | 대응 |
|---|---|
| 의도치 않은 답안 공개 | 기본 비공개. 공개는 명시적 액션 + 무엇이 공개되는지 미리보기 |
| 공개 후 후회 | 언제든 비공개 전환 가능 |
| 벤치마크로 개인 식별 | 집계값만, `n < 5`면 미표시 |
| 풀이 표절 후 제출 | 평가가 LLM 기반이라 복붙은 잡기 어렵다. **v1은 막지 않되**, 공개 풀이에 "먼저 직접 풀어보세요" 경고와 *본인이 해당 시나리오를 완료한 뒤에만 열람 가능* 옵션을 검토 (§10 열린 질문 3) |
| 스팸·부적절 콘텐츠 | 신고 → 플랫폼 관리자 숨김. 조직 감사 로그 패턴 재사용([ADR-0029](adr/0029-audit-log-scoped-to-organization-actions-recorded-synchronously.md)) |
| 조직 전용 콘텐츠 유출 | 조직 시나리오 세션은 **공개 대상에서 제외**. `scenarios.organization_id`가 있으면 공개 불가 |

---

## 8. 단계별 실행 계획

가치/비용 순으로 배열했습니다. 각 슬라이스는 **독립적으로 배포 가능**합니다.

| 슬라이스 | 내용 | 새 테이블 | 비고 |
|---|---|---|---|
| **1. 벤치마크** | C1 — 리포트·포스트모템에 분포 비교 추가 | 없음 | 기존 데이터 집계만. 가치 대비 가장 쌈 |
| **2. 개념 라이브러리** | L1 — 25개 개념 DB 이관 + 계층 화면 + 내 약점 배지 | `learning_concepts` | [ADR-0039](adr/0039-learning-concepts-live-in-the-database-not-frontend-constants.md). 콘텐츠 작성이 작업량의 대부분 |
| **3. 개인 학습 경로** | L2 + L3 — 경로 생성, 상태 파생, 훈련 진입 | 없음 | 슬라이스 2 의존 |
| **4. 공개 프로필 · 마켓플레이스 통합** | C3 + C4 | 없음 | 기존 엔드포인트 재배치 |
| **5. 점수 · 티어 · 랭킹** | C6 | 없음 | 인증 로직 재사용. 새 테이블 없이 읽기 시점 계산 |
| **6. 풀이 공유** | C2 (완료자만 열람) | `sessions.visibility` 컬럼 | 모더레이션 부담 시작 |
| **7. 토론** | C5 | `scenario_discussions` | 신고·숨김 운영이 함께 필요 |

슬라이스 1~3만 해도 두 탭이 "빈 껍데기"를 벗어납니다. 1·3·4·5는 새 콘텐츠 작성이 거의 없어 빠르게 나올 수 있고, 2가 실제 작업량의 대부분(개념 25개 × 7필드)입니다.

**5를 6·7보다 앞에 둔 이유**: 랭킹은 새 테이블 없이 기존 인증 로직 재사용만으로 나오는데(가치 대비 가장 쌈), 풀이 공유와 토론은 둘 다 모더레이션 운영을 시작시킵니다. 운영 부담이 생기는 시점을 최대한 뒤로 미룹니다.

---

## 9. 성공 지표

| 기능 | 지표 | 왜 이것인가 |
|---|---|---|
| Learning | 학습 경로 항목 → 세션 시작 전환율 | Learning의 목적은 읽히는 게 아니라 **훈련으로 이어지는 것** |
| Learning | 경로에서 추천한 riskKey의 재지적률 감소 | 실제로 약점이 개선됐는가 |
| Community | 벤치마크가 표시된 리포트의 재도전율 | 비교가 행동을 유발했는가 |
| Community | 세션 완료 대비 공개 전환율 | 공유 장벽이 적절한가 |
| Community | 랭킹 도입 후 **1인당 도전 도메인 수** 변화 | ADR-0042의 핵심 가설 검증 — 넓이로 유도됐는가, 아니면 여전히 반복인가 |
| Community | 동일 시나리오 재도전 비율 | 급증하면 파밍 방지 설계가 실패한 것 |
| 공통 | 탭 방문 후 이탈률 | 여전히 빈 껍데기로 느껴지는가 |

체류 시간·PV는 지표로 쓰지 않습니다 — 오래 머무는 것이 목표가 아닙니다.

---

## 10. 열어둔 결정 (ADR 후보)

구현 착수 전에 정해야 하는 것들입니다. 셋 다 [CLAUDE.md](../CLAUDE.md)의 ADR 3조건(되돌리기 비용·의외성·실제 대안)에 걸릴 가능성이 있습니다.

1. ~~**개념 콘텐츠를 DB로 이관할 것인가, 프론트 상수로 남길 것인가.**~~
   **결정 완료 (2026-09-30) — DB 이관.** `learning_concepts` 테이블 + Flyway 시드, 프론트 상수 `riskLabels.ts` 제거. 근거와 대가는 [ADR-0039](adr/0039-learning-concepts-live-in-the-database-not-frontend-constants.md).

2. ~~**Community를 인앱으로 만들 것인가, GitHub에 남길 것인가.**~~
   **결정 완료 (2026-09-30) — 토론까지 인앱.** 시나리오 단위 스레드로 범위를 제한하고 폴링을 유지합니다. 모더레이션 부담은 받아들입니다. [ADR-0040](adr/0040-in-app-discussion-threads-replace-the-github-issues-link.md)

3. ~~**공개 풀이를 언제 볼 수 있게 할 것인가.**~~
   **결정 완료 (2026-09-30) — 해당 시나리오 완료자만.** 발견성·마케팅 가치를 포기하고 학습 효과를 택합니다. [ADR-0041](adr/0041-shared-writeups-are-visible-only-to-users-who-completed-that-scenario.md)

5. **난이도 가중치(1.0/1.2/1.5)와 티어 구간을 어떻게 조정할 것인가.** 설정값으로 두고 실제 점수 분포를 본 뒤 조정합니다. ADR 대상이 아니라 운영 튜닝입니다.

4. **벤치마크 표본 하한(n=5)을 몇으로 할 것인가.** 되돌리기 쉬우므로 ADR 대상은 아니고 설정값으로 둡니다.

---

## 11. 다음 단계

§10의 주요 결정 3건이 모두 확정됐습니다([ADR-0039](adr/0039-learning-concepts-live-in-the-database-not-frontend-constants.md) · [0040](adr/0040-in-app-discussion-threads-replace-the-github-issues-link.md) · [0041](adr/0041-shared-writeups-are-visible-only-to-users-who-completed-that-scenario.md) · [0042](adr/0042-drill-score-and-ranking-reward-breadth-and-difficulty-not-repetition.md)). 남은 것은 운영 튜닝 항목(§10의 4·5)뿐이며 구현 중에 정하면 됩니다.

착수 순서는 §8의 슬라이스 1(벤치마크)을 권합니다 — 새 콘텐츠도 새 테이블도 없이 기존 데이터 집계만으로 Community 탭의 공백을 실제 데이터로 메울 수 있고, 슬라이스 5(랭킹)와 7(토론)이 같은 집계 기반 위에 올라가기 때문입니다.
