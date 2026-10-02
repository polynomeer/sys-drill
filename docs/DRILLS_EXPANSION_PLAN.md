# Drill 확장 계획안 — 요구사항부터 복구 검증까지

> 작성 2026-09-30. 원본: [`archive/drill-feature-proposal.md`](archive/drill-feature-proposal.md) (27절).
>
> 관련 문서 — [PRD.md §6](PRD.md)(핵심 학습 루프), [ARCHITECTURE.md §5·§6](ARCHITECTURE.md)(세션 상태 머신, Simulation Engine), 이전 비전 문서 [DRILLS_SIMULATION_VISION.md](DRILLS_SIMULATION_VISION.md), 형제 계획안 [OBSERVABILITY_UI_PLAN.md](OBSERVABILITY_UI_PLAN.md)(**먼저 읽기 권장** — 시간축 O0-a와 조사 행위 기록 O0-b를 이 문서가 전제로 씀) · [LEARNING_EXPANSION_PLAN.md](LEARNING_EXPANSION_PLAN.md) · [COMMUNITY_EXPANSION_PLAN.md](COMMUNITY_EXPANSION_PLAN.md).
>
> 이 문서는 **계획안**이며 아직 구현되지 않았습니다.

---

## 1. 원본 제안 한 줄 요약

기능 수를 늘리기보다 **"현실의 엔지니어가 실제로 어려움을 겪는 지점"** 을 Drill에 넣자는 제안입니다 — 불완전한 요구사항, 규모 추정, 예산·팀 역량 제약, 배포 판단, SLO, 완화와 복구의 구분, 가정의 붕괴, "그때 다르게 했다면". 원본은 이를 17단계 미션(`Requirements → Clarification → … → Replay`)으로 그렸습니다.

## 2. 현재 Drill의 실제 형태

```
INITIAL(설계) ─제출→ 평가 ─→ FOLLOWUP(꼬리설계: 조건 변경) ─제출→ 평가 ─→ INCIDENT(워게임 + 대응 답안) ─제출→ 평가 ─→ 리포트
                                                                              │
                                                                  포스트모템 · 리플레이 · 샌드박스(완료 후)
```

- 단계 타입은 **`INITIAL / FOLLOWUP / INCIDENT` 셋뿐**이고 상태 머신(`SessionStateMachine`)·벤치마크·면접 타이머가 전부 이 셋을 전제로 합니다.
- 꼬리설계는 시드 기반으로 변형을 고르며, **사용자의 가장 큰 약점 riskKey를 겨냥한 변형을 우선**합니다(`selectVariant`). 원본 §24 Scenario Variants는 이미 부분적으로 있습니다.
- 인시던트 액션은 도메인당 3개 고정(`SimulationActionType`)이고 **점수에 들어가지 않습니다.** 평가는 단계별 답안 텍스트에만 합니다 — 원본 §23 Hidden Score 원칙은 이미 지켜지고 있습니다.
- 완료 후 샌드박스(같은 세션 상태에서 계속 실험), 액션 단위 리플레이, MTTD/MTTR 포스트모템이 있습니다.

원본 항목별 대조:

| 원본 | 현재 | 판정 |
|---|---|---|
| 1 불완전한 요구사항 + 질문하기 | 없음 (INITIAL 프롬프트가 요구사항을 다 줌) | **채택 — M1** |
| 2 규모 추정 | 없음 | **채택 — M2** |
| 3 제약 / 4 Complexity Budget / 5 비용 | 없음 | **축소 채택 — M8** |
| 6 Deploy / 7 Change Risk | autoscaling의 `ENABLE_ROLLOUT_SAFEGUARD` 액션뿐 | **채택(후순위) — M9**, 새 도메인으로 |
| 8 SLO 정의 | 없음 | **채택 — M3** |
| 9 Runbook | 없음 | **채택(후순위) — M12** |
| 10 On-call Handoff | 없음 | **보류** |
| 11 Incident Communication | INCIDENT 답안이 루브릭 "커뮤니케이션" 5점으로 채점되기는 함 | **축소 채택 — M11** |
| 12 Multi-role AI / 13 Multiplayer War Room | Game Day = 관전 + 채팅(ADR-0026) | **보류** |
| 14 Unknown Unknowns | 없음 | **보류** — 액션 목록이 원인을 누설(§6) |
| 15 Compound Failure | 없음 | **보류** — 도메인별 단일 메커니즘(ADR-0010) |
| 16 Recovery Verification / 17 Data Integrity | 없음 (MTTR = 마지막 액션 시각) | **채택 — M5** |
| 18 Security Drill | 없음 | **기각(제품 범위 밖)** — 원본도 "고급 트랙으로" 권고 |
| 19 사용자가 GameDay 설계 | real-infra Toxiproxy 배관은 있음 | **보류** |
| 20 Design Review Gate | 평가 결과에 `followupQuestions`가 이미 생성됨 | **채택 — M4** (기존 산출물 재사용) |
| 21 Assumption Tracker | 꼬리설계 변형이 사실상 "가정 붕괴"를 이미 수행 | **채택 — M7** (그것을 드러내기) |
| 22 Counterfactual Replay | 리플레이는 재생만. 샌드박스는 "끝에서" 계속만 | **채택 — M6** |
| 23 Hidden Score | 이미 지켜짐 | **원칙으로 명문화**(§3) |
| 24 Scenario Variants | 부분 존재 | **확장 — M10** |
| 25 Mastery와 Confidence 분리 | SkillProfile은 riskKey 카운트뿐 | **M10 + Learning L7** |
| 26 Drill Generator | 커스텀 시나리오(ADR-0024/0038) | **보류** |
| 27 Mission Control UI | 없음 | **[OBSERVABILITY_UI_PLAN.md](OBSERVABILITY_UI_PLAN.md) O1으로 이관** |

---

## 3. 이 계획안이 취하는 입장

1. **새 단계 타입을 만들지 않는다.** 원본의 17단계를 상태 머신에 그대로 넣으면 `SessionStateMachine`, 면접 타이머(단계별 600/600/480초), 벤치마크(단계 수 전제), `StageList`가 전부 흔들립니다. 새 요소는 **기존 세 단계 안의 하위 활동**으로 붙입니다.

   ```
   INITIAL   [M1 요구사항 질의] → [M2 규모 추정] → [M7 가정 기록] → 설계(+[M8 비용·복잡도]) → 제출 → 피드백 → [M4 설계 방어]
   FOLLOWUP  조건 변경 (+ M7 깨진 가정 표시, M8 예산 삭감 같은 변형)
   INCIDENT  [M3 SLO] [O5 알림] [M9 변경 검토] → 관측 → 액션 → [M5 복구 확인] [M11 상태 공지] → 대응 답안
   이후      포스트모템 → 리플레이 → [M6 Counterfactual]
   ```

   저장은 해당 단계 `submissions.structured_json` 또는 세션 단위 JSONB — "스키마가 도메인마다 다르거나 자주 바뀌는 값은 JSONB"라는 기존 기준(ARCHITECTURE §4.1)을 따릅니다.

2. **결정론적으로 판정할 수 있는 것부터.** 질문 목록, 추정치 비교, 가정 붕괴, 복구 상태는 전부 규칙으로 판정합니다. LLM은 기존처럼 그 판정을 "사전 점검"으로 받아 해석만 합니다(ARCHITECTURE §8.2). 자유 입력을 LLM으로 매칭하는 버전은 전부 2차로 미룹니다 — 턴마다 LLM을 부르는 기능은 `LlmUsageGuard` 한도와 정면으로 부딪힙니다.

3. **진행 중에는 점수를 보이지 않는다(Hidden Score).** 새 하위 활동 어디에도 "+10점" 같은 즉시 보상을 넣지 않습니다. 결과는 단계 피드백과 리포트에서만 공개합니다.

4. **도메인 메커니즘을 흉내 내지 않는다.** Deploy/Canary처럼 새 병목 메커니즘이 필요한 것은 기존 도메인에 끼워 넣지 않고 새 도메인으로 기획합니다([ADR-0012](adr/0012-new-incident-domains-get-distinct-mechanisms.md)).

### 하지 않는 것

| 하지 않음 | 이유 |
|---|---|
| 17단계 상태 머신 | §3-1 |
| 진행 중 점수·배지 팝업 | §3-3 |
| AI가 장애를 자유 생성하는 Drill Generator | 엔진이 검증할 수 없는 장애는 채점할 수 없음. 커스텀 시나리오도 7개 도메인 안에서만 INCIDENT를 허용한 선례([ADR-0038](adr/0038-custom-scenarios-get-an-incident-step-by-constraining-domain-to-the-known-set.md)) |
| Security Drill | 제품 정의(PRD §1) 밖 |

---

## 4. 콘텐츠 버전에 관한 주의

M1·M2·M7·M8은 시나리오 콘텐츠(INITIAL 프롬프트를 일부러 불완전하게 고치기, 질문·정답표, 가정 목록, 제약)를 추가합니다. 콘텐츠는 Flyway로 시딩하고([ADR-0002](adr/0002-content-via-migrations-not-admin-crud.md)), 프롬프트가 바뀌면 **새 `scenario_version`** 이 됩니다.

- 벤치마크는 `scenario_version_id` 안에서만 비교하므로([LEARNING_COMMUNITY_PLAN.md §6.1](LEARNING_COMMUNITY_PLAN.md)) **새 버전은 표본 0에서 다시 시작**합니다.
- **확정(2026-10-01) — 버전은 한 번만 올린다** ([ADR-0048](adr/0048-official-scenarios-move-to-mission-content-in-a-single-v2-bump.md)). 초안은 "coupon으로 먼저 검증"이었지만 그러면 coupon만 버전을 두 번 올리게 되고(M1·M2 검증용 → M7·M8 추가용) 표본이 두 번 초기화됩니다. 대신:
  1. 기능(M1·M2·M7·M8·M11)은 **콘텐츠 필드가 없으면 화면에 나타나지 않게** 구현하고, 테스트는 테스트 안에서 만든 시나리오 버전으로 검증합니다
  2. 기능이 다 들어간 뒤 **공식 7개 시나리오의 v2를 하나의 마이그레이션으로** 추가합니다(이전 선례 V14처럼 v1을 제자리 UPDATE하지 않음 — 이번에는 INITIAL 프롬프트 자체를 줄이므로 난이도가 바뀌어 같은 버전으로 비교하면 안 됨)
- 토론도 버전 단위라 v2가 나오면 **기존 v1 토론이 화면에서 사라집니다.** Community C7에서 "이전 버전 토론(읽기 전용)"을 함께 보여주도록 고칩니다 — v2 마이그레이션보다 먼저.
- 공개 풀이·토론도 버전 단위이므로 같은 영향을 받습니다.
- **확정(2026-10-02, Round E24 구현 시)**: V65. v2 버전 ID는 v1의 접두사에 `…0020`. INITIAL은 수치를 뺀 짧은 프롬프트 + 질문 5~6개(핵심 3~4개는 `requirementKey`로 공개 개요에서도 가림) + 추정 2개(답은 요구사항에서 계산되는 값: 피크 = 사용자 × 집중 시간, 동시 호출 = 처리율 × 지연) + 가정 4개 + 제약. FOLLOWUP은 **SQL로 v1 변형 JSON을 그대로 읽어 `breaks`만 덧붙인다** — 손으로 다시 쓰면 문구가 미세하게 달라져 v1·v2 꼬리설계가 다른 문제가 된다. INCIDENT와 버전 규칙(`followup_rules`·`incident_rules`)은 v1에서 복사. 공개 풀이 목록은 원래 시나리오의 모든 버전을 함께 보여 주므로 v2 이후에도 v1 풀이가 사라지지 않는다(토론만 버전 단위).

---

## 5. 슬라이스

실행 순서대로 번호를 붙였습니다. ✦ = 관측 계획안의 시간축(O0-a) 없이 가능.

| # | 슬라이스 | 선행 | 저장 | 비용 |
|---|---|---|---|---|
| M1 | 요구사항 질의 | 없음 ✦ | 시나리오 콘텐츠 + 세션 JSONB | 콘텐츠 |
| M2 | 규모 추정 | 없음 ✦ | 시나리오 콘텐츠 + 제출 JSONB | 낮음 |
| M3 | SLO 정의 | 없음 ✦ (상태 표시는 O5) | 세션 JSONB | 낮음 |
| M4 | 설계 방어 | 없음 ✦ | 제출 JSONB | 낮음 |
| M5 | 완화 / 복구 분리 + 정합성 점검 | O0-a | 복구 선언 이벤트 | 중 |
| M6 | Counterfactual Replay | 없음 ✦ (영향 비교는 O0-a) | Redis 임시 상태 | 중 |
| M7 | 가정 기록 → 꼬리설계에서 붕괴 표시 | 없음 ✦ | 시나리오 콘텐츠 | 콘텐츠 |
| M8 | 제약 · 비용 · 운영 복잡도 | 없음 ✦ | 설정 데이터 | 중 |
| M9 | Deploy/Canary 도메인 + 변경 검토 | O0-a | 새 도메인 | 높음 |
| M10 | 변형 기반 숙련 신뢰도 | 없음 ✦ | 없음(파생) | 낮음 |
| M11 | 인시던트 상태 공지 | 없음 ✦ | 제출 JSONB | 낮음 |
| M12 | 개인 Runbook | O0-b | `user_runbooks` | 중 |

### M1 — 요구사항 질의 (Ask a question) ✦

- INITIAL 프롬프트는 핵심 한두 줄만 주고(원본 예: "선착순 쿠폰, 약 100만 명 접속 예상"), 나머지는 **질문 카드**로 확인하게 합니다.
- 시나리오 콘텐츠에 `clarifications: [{id, question, answer, critical, relatedRiskKey}]`를 둡니다. 핵심 질문(`critical`) 약 5~9개 + 무관하거나 덜 중요한 질문 몇 개를 섞어 **목록에서 고르게** 합니다. 자유 질문 입력 + LLM 매칭은 2차.
- 확인한 질문·답은 설계 화면 옆에 "확인된 요구사항"으로 쌓이고, 제출 시 평가 프롬프트에 `확인한 요구사항 / 확인하지 않은 핵심 요구사항`으로 들어갑니다 — 루브릭 "요구사항 해석력(15점)"의 근거가 됩니다.
- 리포트에 `Requirements Discovery: 핵심 질문 7/9, 놓친 요구사항 — 중복 발급 절대 불가`.
- **새 riskKey를 만들지 않습니다.** riskKey를 늘리면 `learning_concepts`와 1:1 매칭 테스트(`LearningConceptCatalogTest`)까지 번집니다. 놓친 요구사항은 별도 섹션으로 표시합니다.

**완료 기준**: coupon 한 도메인 새 버전, 질문 선택 → 설계 화면 반영 → 평가 프롬프트 포함을 통합 테스트로, 리포트 섹션 실브라우저 확인.

### M2 — 규모 추정 ✦

- 설계 전에 `Peak RPS · Write RPS · Storage/day` 등 시나리오가 지정한 항목을 입력. 기준값은 시나리오 콘텐츠(요구사항에서 계산한 참값)와, 인시던트에서는 엔진의 `trafficRps`.
- 판정은 **자릿수 기준**: `|log10(추정/참값)| ≤ 0.3`(2배 이내)이면 적중. 원본이 강조한 "정확한 숫자보다 order of magnitude".
- 추정 입력 UI는 Learning의 Capacity Lab([LEARNING_EXPANSION_PLAN.md](LEARNING_EXPANSION_PLAN.md) L6)과 **같은 컴포넌트**로 만듭니다.

### M3 — SLO 정의 ✦

- 인시던트 시작 전 `가용성 · P99 · 에러율` 목표 입력(기본값: 시나리오 비기능 요구). 세션 JSONB에 저장.
- 시간축 전에는 "현재 스냅샷 vs 목표"만 Mission Control에 표시하고, 에러 버짓·burn rate는 O5에서 붙습니다.
- 목표를 너무 느슨하게/빡빡하게 잡는 것 자체는 감점하지 않고, 평가 프롬프트에 "사용자가 정한 SLO"로 넘겨 해석하게 합니다.

### M4 — 설계 방어 (Design Review Gate) ✦

- INITIAL 피드백에는 이미 `followupQuestions`("DB가 SPOF 아닌가요?")가 들어 있습니다. 지금은 읽고 지나갈 뿐입니다.
- FOLLOWUP으로 넘어가기 전에 그중 **2개에 짧게 답하게** 합니다(일반 모드는 건너뛰기 가능, 면접형 타이머 모드는 필수). 답은 FOLLOWUP 제출의 `structured_json`에 붙어 다음 평가 프롬프트로 들어갑니다.
- 새 LLM 호출이 없습니다 — 질문은 이미 생성돼 있고, 답은 다음 평가에 합쳐집니다.

### M5 — 완화 / 복구 분리 + 정합성 점검

- 지금 MTTR은 "마지막 액션 시각"이라, **지표가 돌아온 것과 시스템이 복구된 것을 구분하지 못합니다.**
- 엔진이 두 상태를 파생합니다:
  - `MITIGATED` — 1차 증상 지표(에러율·지연)가 정상 밴드로 복귀
  - `RECOVERED` — 적체 지표(queueLag, 미처리 레코드, 만료 안 된 hold)까지 해소
  - 적체가 줄어드는 과정은 시간축(O0-a)이 있어야 표현됩니다.
- 사용자가 **"복구 선언"** 액션을 명시적으로 하게 하고, 선언 시점에 적체가 남아 있으면 포스트모템에 `⚠ Partial Recovery — Kafka lag 1.2M 남음`.
- 정합성 도메인(payment · reservation · batch-settlement)은 선언 전 체크리스트에 **정합성 항목**을 추가하고, 멱등 재처리 계열 trait(`idempotentReconciliationEnabled`, `idempotentPgRetryEnabled`, `atomicInventoryCheckEnabled`)이 꺼져 있으면 불일치 건수를 보여줍니다. 원본 §17의 reconciliation 액션 4종은 새 액션이라 이번 범위에 넣지 않습니다.
- **MTTR 정의 주의**: 벤치마크·랭킹 보드가 기존 MTTR을 씁니다. 정의를 바꾸지 않고 `복구 선언까지 시간`을 **새 지표로 추가**합니다.

### M6 — Counterfactual Replay ✦

- 리플레이의 임의 지점에서 `[여기서 다르게 해보기]` → 그 시점까지의 액션 이력을 복제한 **포크 상태**에서 다른 액션을 적용해 봅니다.
- 규칙 기반 엔진은 `(시드, 액션 이력)`만으로 상태가 결정되므로 포크 비용이 거의 없습니다. 실제 인프라 세션은 스냅샷 기반([ADR-0016](adr/0016-incident-replay-snapshots-only-for-real-infra.md))이라 **지원하지 않습니다.**
- 포크는 **세션을 새로 만들지 않고 Redis 임시 상태(TTL)** 로 둡니다. 기존 샌드박스는 같은 세션의 `applied_actions`에 계속 쌓는 방식이라 포크에 쓰면 원래 기록이 오염됩니다. → **ADR 후보**(포크는 영속 세션이 아니다 — 되돌리기 비싸고 Community Fork My Run이 같은 결정을 물려받음).
- 비교 화면: `실제 vs 포크`의 최종 상태, 액션 수. 시간축(O0-a) 이후 MTTR·영향 면적(에러율 곡선 아래 넓이)까지.
- 같은 메커니즘을 [COMMUNITY_EXPANSION_PLAN.md](COMMUNITY_EXPANSION_PLAN.md) C9 Fork My Run이 **남의 공개 풀이**에 적용합니다.

### M7 — 가정 기록 ✦

- 꼬리설계는 이미 "트래픽 20배", "Redis 예산 삭감"처럼 가정을 깨는 역할을 합니다. 다만 사용자는 **자기가 무엇을 가정했는지 적은 적이 없어서** 무엇이 깨졌는지 모릅니다.
- INITIAL에서 시나리오가 준 가정 후보(`Read ≫ Write`, `stale 30초 허용`, `리전 장애 없음`…)를 고르거나 직접 추가하고, FOLLOWUP 변형 콘텐츠에 `breaks: [assumptionId]`를 달아 해당 가정을 `Assumption Broken`으로 표시합니다.
- 직접 추가한 자유 가정은 표시만 하고 자동 매칭하지 않습니다.

  **확정(2026-10-02, Round E20 착수 시)**: INITIAL `content.assumptions: [{id, text}]`, FOLLOWUP 단일 콘텐츠 또는 각 변형에 `breaks: [id]`. 선택은 INITIAL 제출의 `structured_json.assumptions = {selected: [id], custom: [text]}`로 확정(M2 추정치와 같은 자리, 선택 사항). `GET /sessions/{id}/assumptions`는 후보·내 선택·(FOLLOWUP 진입 후) 고정된 변형이 깨뜨린 가정을 돌려준다 — 깨진 가정은 **고정된 변형 키**(Round E10)로만 판정해 나중에 바뀌지 않는다. 화면은 "내가 둔 가정이 깨졌다"와 "가정하지 않았던 조건이 바뀌었다"를 구분한다. 평가 프롬프트: INITIAL엔 사용자가 둔 가정, FOLLOWUP엔 깨진 가정과 그것을 사용자가 가정했었는지.

### M8 — 제약 · 비용 · 운영 복잡도 ✦

- 시나리오 콘텐츠에 `constraints: {budgetPerMonth, teamSize, opsExperience: {kafka: LOW, redis: HIGH, …}}`.
- **비용**: 캔버스 노드 kind × 수량(트레이트의 `readReplicaCount`, `podReplicas`, `consumerCount` 등) × 단가표(설정 데이터, [ADR-0006](adr/0006-config-as-data.md)) → 월 추정 비용을 캔버스 상단에 `예산 대비 +24%`로. scale-out 액션에는 비용 증분을 표시.
- **운영 복잡도**: 서로 다른 인프라 종류 수 × 팀 운영 경험 가중치 → `복잡도 82 / 팀 역량 43`. 점수가 아니라 **평가 프롬프트에 주는 사실**입니다 — "기술을 많이 쓰면 좋은 설계"라는 습관을 교정한다는 원본의 목적은 LLM이 이 사실을 근거로 트레이드오프를 지적할 때 달성됩니다.
- "CFO: 인프라 비용 30% 절감" 같은 이벤트는 **새 코드 없이 FOLLOWUP 변형 콘텐츠**로 추가할 수 있습니다.
- 모든 금액 옆에 "추정치" 표기. 실제 클라우드 단가 추적은 하지 않습니다.

  **확정(2026-10-02, Round E20 착수 시)**:
  - 콘텐츠: INITIAL `content.constraints = {budgetPerMonth, teamSize, opsExperience: {<노드 kind>: LOW|MEDIUM|HIGH}}`. 경험 키는 기술 이름(kafka, redis)이 아니라 **캔버스 노드 kind**(queue, cache…) — 캔버스가 kind만 알기 때문에 매핑 표를 하나 더 두지 않는다. 화면에는 kind 이름을 사람이 읽는 이름으로 보여준다.
  - 비용 = 노드 kind별 단가 × 노드 수 + 확장 단위 단가 × 트레이트 값(읽기 복제본·컨슈머·디스패처 워커·Pod, 도메인에서 캔버스가 정하는 것만). 단가는 Kotlin 상수(USD/월, "추정치"). `constraints`가 없는 시나리오는 비용·복잡도를 보여주지 않는다(M1·M2와 같은 "콘텐츠 없으면 숨김").
  - 복잡도 = Σ(사용한 서로 다른 kind의 기본 복잡도 × 팀 경험 배수: LOW 1.5 · MEDIUM 1.0 · HIGH 0.7 · 미기재 1.0), 팀 역량 = 팀 인원 × 15. 점수가 아니라 평가 프롬프트의 사실("복잡도 82 / 팀 역량 43, 예산 대비 +24%").
  - `GET /sessions/{id}/cost-estimate`는 저장된 캔버스 기준으로 매번 계산(ADR-0011) — 액션별 비용 증분(`actionCostDeltas`)도 함께 내려 Wargame 액션 카드에 "+월 $240(추정)"을 붙인다.

### M9 — Deploy/Canary 도메인 + 변경 검토

- 8번째 도메인 `deployment`: 에러율 = `카나리 트래픽 비율 × 새 버전 결함률`. 액션은 `CONTINUE_ROLLOUT / PAUSE_ROLLOUT / ROLLBACK`, 사용자가 전략(Rolling/Blue-Green/Canary)·카나리 비율·롤백 임계값을 설계 단계에서 정합니다. 기존 7개와 메커니즘이 확실히 다르므로 ADR-0012 조건을 만족합니다.
- 시간이 흘러야 카나리가 5% → 10% → 25%로 진행되므로 **시간축(O0-a)이 필수**입니다.
- 인시던트 직전 **변경 검토**: 릴리즈 변경 목록(`Retry 추가`, `Redis TTL 300→60`…) 중 가장 위험한 것을 고르고, 실제 원인과 대조.
- **번지는 범위**가 커서 후순위입니다: 공식 도메인이 늘면 인증(ADR-0032)·DrillScore 상한(현재 940, ADR-0042)·트랙·개념 `relatedDomains`·real-infra 선택 맵(ADR-0018)이 전부 영향을 받습니다. 착수 시 영향 목록을 PLAN.md에 먼저 적습니다.

  **확정(2026-10-02, Round E30 착수 시)**: 카나리는 **시간이 흐르며 자동 진행**(시작 비율 → ×2 → … → 100%, 60초마다)하고, `PAUSE_ROLLOUT`은 그 자리에서 멈추고, `CONTINUE_ROLLOUT`은 즉시 다음 단계로, `ROLLBACK`은 0%로 되돌린다. 에러율 = 카나리 비율 × 결함률(60%). 설계 단계의 자동 롤백 임계(gateway 노드)를 정했다면 그 임계를 넘는 단계에서 30초 뒤 자동 롤백 — "롤백 임계를 미리 정한 설계"가 장애 중에 보상받는다. 시간 의존 부분은 ADR-0045처럼 샘플러가 그 초의 카나리 비율을 계산해 도메인 함수에 넘긴다(도메인 함수는 비율 → 지표만). 변경 검토는 INCIDENT 단계 콘텐츠 `changeReview: {changes: [{id, text}], culpritId}` — 인시던트 시작 전(배포 전 준비 화면)에 가장 위험한 변경을 고르고(`mission_state.changeReviewPick`), 시작 후에는 실제 원인과 대조해 보여주며 INCIDENT 평가 프롬프트에 넘긴다. 인증 대상은 7개로 고정(ADR-0049).

### M10 — 변형 기반 숙련 신뢰도 ✦

- 도메인별로 **서로 다른 꼬리설계 변형을 몇 개 통과했는지**를 파생해 `신뢰도`로 표시합니다. "한 번 맞혔다고 Mastered 처리하지 않는다"(원본 §25).
- 저장 없이 세션·제출 이력에서 계산([ADR-0011](adr/0011-derived-values-are-never-persisted.md)). 트랙 페이지와 인증 페이지에 표시하고, Learning의 개념 숙련 표시([LEARNING_EXPANSION_PLAN.md](LEARNING_EXPANSION_PLAN.md) L7)와 같은 규칙을 공유합니다.
- **DrillScore에는 넣지 않습니다** — ADR-0042는 "도메인별 최고점"으로 반복을 무력화했는데, 신뢰도를 점수에 섞으면 반복이 다시 보상받습니다.

  **확정(2026-10-02, Round E21 구현 시)**: 변형의 "통과" = 그 세션 평균 점수가 인증 통과 점수 이상(인증과 같은 기준, 공식 시나리오·채용 평가 제외 규칙도 인증 그대로). 변형의 정체성과 세기는 L7과 **같은 함수**(`ConceptMasteryService.variantOf`/`distinctVariants`) — L7은 "그 개념 미지적"을, M10은 "통과 점수"를 통과 조건으로 넘긴다. 분모는 현재 공식 버전 FOLLOWUP의 변형 수(단일 프롬프트면 1). 인증 응답의 도메인별 `passedVariants/totalVariants`로만 내려가고 `passed`·인증·DrillScore는 그대로다.

### M11 — 인시던트 상태 공지 ✦

- INCIDENT 답안에 구조화 필드 `고객 공지 초안`을 추가(`We are investigating elevated latency affecting checkout…`). 평가는 **기존 INCIDENT 평가 한 번에 합쳐서** 루브릭 "커뮤니케이션(5점)"의 근거로 씁니다. 원본의 명료성·정확성·과장 여부는 평가 프롬프트의 관점 목록으로.
- 상황이 바뀔 때마다 새 공지를 요구하는 다회 평가는 LLM 비용 때문에 보류.

### M12 — 개인 Runbook

- 조사 행위 기록(O0-b) 이후. 포스트모템에서 도메인별 Runbook(조사 종류·액션·자유 문장의 순서 목록)을 작성·수정하고, 다음 같은 도메인 인시던트에서 **실제 조사 이벤트와 대조**해 `Step 2 ✕ — connection pool 확인 안 함`을 보여줍니다.
- 반복할수록 사용자의 Runbook이 자라는 것이 요점이므로 사용자 단위로 저장(`user_runbooks`)합니다 — 세션 파생값이 아니라 사용자 입력입니다.

  **확정(2026-10-02, Round E27 구현 시)**: 단계 = `{type, target, text}`, type은 조사 종류 4개(O0-b와 같은 값) + `ACTION`(액션 이름) + `NOTE`(대조하지 않음). 대조는 인시던트 시작 이후의 조사 기록·조치에서 같은 종류이고 target을 포함하는 첫 기록(대소문자 무시) — 순서 위반은 표시하지 않고 "밟았는가"만 본다(순서까지 채점하면 상황에 맞게 바꾼 판단이 감점처럼 보인다). 포스트모템에서 이번 대응의 조사·조치를 한 번에 단계로 추가할 수 있고, 인시던트 중에는 작은 카드로 밟은 단계가 체크된다. 최대 20단계.

---

## 5-1. 착수 전 확정 사항 (2026-10-01)

| 항목 | 결정 |
|---|---|
| 하위 활동 입력의 저장 위치 | 제출과 함께 확정되는 입력(M2 추정치, M4 방어 답, M7 가정 선택, M11 공지)은 **이미 있지만 아무도 쓰지 않는 `submissions.structured_json`** 에 담는다(`SubmitAnswerRequest.structuredJson`은 받아서 저장까지 하는데 프론트가 보낸 적이 없음). 제출 전에 서버가 알아야 하는 진행 상태(M1 확인한 질문, M3 SLO, 선택된 꼬리설계 변형 키)는 `sessions.mission_state` JSONB(관측 계획안 §5-1과 같은 컬럼) |
| 평가 프롬프트 | `HybridRuleAiEvaluator.buildUserPrompt`가 이미 `Submission` 전체를 받으므로 `structured_json`에서 `## 확인한 요구사항`·`## 규모 추정 판정`·`## 설계 방어`·`## 고객 공지 초안` 섹션을 덧붙인다. 규칙 판정(`RuleEvaluator`)은 `rawText`만 보는 지금 구조 유지 |
| 콘텐츠 필드 위치 | INITIAL 단계 `content`에 `clarifications` · `estimation` · `assumptions` · `constraints`, FOLLOWUP 변형에 `breaks`. 공개 API(`GET /scenarios/{id}`)는 이 필드의 **정답(answer)을 절대 내보내지 않는다** — 후속 프롬프트를 숨기는 기존 원칙(Round B1)과 같다 |
| 공개 개요의 요구사항 (M1, 2026-10-02 추가) | Drill 개요(`GET /scenarios/{id}`)가 `baseRequirements`를 그대로 보여줘 질문으로 찾아야 할 숫자(쿠폰 수량 등)가 미리 드러난다. 질문 항목에 `requirementKey`를 두고, 개요 응답에서 그 키를 `nonFunctional`에서 뺀다. `baseRequirements`는 버전이 아니라 시나리오에 붙어 있어 v1 개요도 같은 규칙을 따르지만, 키를 가리는 것은 현재 버전의 질문 목록뿐이라 v1(질문 없음)에는 영향이 없다 |
| 설계 방어 위치 (M4, 2026-10-02 변경) | 초안은 "FOLLOWUP으로 넘어가기 전에" 답하게 했지만, 답은 어차피 FOLLOWUP 제출의 `structured_json.defense`로 함께 평가된다. 넘어가기 전 화면에 답을 받으면 그 답을 화면 사이에서 들고 다닐 상태가 하나 더 생겨서 **FOLLOWUP 작성 화면 왼쪽에 방어 패널**을 둔다. 면접형 타이머 모드의 "필수"는 프론트에서만 막고 서버는 빈 답도 받는다 — 시간 초과 자동 제출을 막으면 안 되기 때문(빈 답은 프롬프트에 "답하지 않음"으로 남는다) |
| 오프라인 평가의 꼬리질문 (M4, 2026-10-02) | API 키가 없을 때의 고정 응답에 꼬리질문이 비어 있어 M4가 데모·테스트에서 나타나지 않았다. "(오프라인 예시)" 표시를 단 일반 질문 2개를 넣었다 |
| 꼬리설계 변형 고정 | **기존 공백**: 변형 키가 저장되지 않고 프롬프트를 읽을 때마다 `selectVariant`가 다시 고른다. 평가 후 약점 카운트가 바뀌면 같은 세션의 FOLLOWUP 프롬프트가 나중에 다르게 보일 수 있다. FOLLOWUP 진입 시 고른 키를 `mission_state.followupVariantKey`에 고정한다 — M7(어느 가정이 깨졌나)과 M10(서로 다른 변형 수)의 전제. M1 라운드에서 먼저 고친다 |
| 인시던트 종료 | 지금은 종료 개념이 없다(Redis 6시간 TTL뿐). M5가 `INCIDENT_RESOLVED` 표식 행을 `INCIDENT_STARTED`와 같은 방식으로 `applied_actions`에 남긴다 |
| 정합성 점검 (M5, 2026-10-02 구현 시 변경) | batch-settlement는 엔진의 `errorRate`가 곧 불일치 비율이고 `queueLag`가 재처리 레코드 수라 **엔진 자신의 숫자로 "중복 반영 N건"**을 낸다. payment·reservation은 초안처럼 "재시도 수"를 세려면 도메인 함수의 private 계수(`PARTIAL_FAILURE_WASTE_FACTOR` 등)를 밖으로 복제해야 해서(ADR-0045가 지키려는 경계) **위험 구간의 길이**만 낸다 — "멱등 재시도 없이 인시던트 N초, 그동안의 재시도는 대사 필요". 수정 조치가 선언 전에 들어갔으면 항목은 통과로 보되, 그 전 구간의 대사 필요는 문구로 남긴다 |
| 포크 (M6) | `POST /sessions/{id}/forks {atStep}` → Redis `fork:{id}`(1시간 TTL)에 도메인 · 기준 trait · 액션 접두부 저장, 이후 `GET/POST /forks/{id}/…`. 규칙 기반만 — [ADR-0046](adr/0046-forks-are-ephemeral-redis-state-not-sessions.md) |
| 샌드박스 오염 수정 (M6, 구현 시 변경) | 표식 대신 **세션 완료 시각 이후의 액션은 샌드박스**로 보고 MTTR·벤치마크·시계열·리플레이에서 제외 — 이미 쌓인 행까지 정리된다. "샌드박스에서 계속 실험하기"는 마지막 단계 포크로 바꿔 더는 기록에 쓰지 않는다 |
| 비용 단가표 (M8) | 노드 kind별 월 단가는 Kotlin 설정 상수(관리자 CRUD 없음, ADR-0006 정신 — 코드 리뷰를 거친 설정). 모든 금액에 "추정치" |
| Runbook (M12) | `user_runbooks(user_id, domain, steps jsonb, updated_at)`, `unique(user_id, domain)` |
| M9 Deploy 도메인 | 착수 시 영향 목록(KNOWN_DOMAINS · 인증 공식 도메인 · DrillScore 상한 · 트랙 · 개념 `relatedDomains` · `TOPOLOGY_FIELDS`/`NODE_TRAIT_CONFIG` · 액션 enum)을 PLAN.md에 먼저 적고 ADR을 쓴다 |

## 6. 보류 항목과 재개 조건

| 항목 | 막는 것 | 재개 조건 |
|---|---|---|
| Unknown Unknowns (증상만 주기) | 제목을 숨겨도 **도메인 전용 액션 3개가 원인을 누설**함 | 도메인 무관 범용 액션 카탈로그(scale/rollback/failover/flag — DRILLS_SIMULATION_VISION §2 "Action Engine 범용화") 도입 시 |
| Compound Failure | 도메인당 단일 메커니즘([ADR-0010](adr/0010-simulation-engine-per-domain-functions.md)) | 노드별 상태 모델 + 범용 액션 이후. 그 전에는 "원인 1 + 증폭 요인 1" 정도를 한 도메인 수식 안에서 표현하는 것까지만 |
| Multi-role AI 팀 / War Room | 턴마다 LLM 호출 비용, 공동 액션 동시성 | Game Day 관전 사용량 신호 확인 후. 역할별 패널 권한은 관측 탭(O1) 구조 위에 얹을 수 있음 |
| On-call Handoff | 틀린 인수인계 노트 = 시나리오별 콘텐츠 | M12 Runbook 이후 — 인수인계 노트를 "다른 사람의 Runbook 요약"으로 만들 수 있음 |
| 사용자가 GameDay(카오스 실험) 설계 | real-infra는 coupon·notification 2개 도메인뿐 | real-infra 일반화(Phase 3-B 후속) 시 |
| Drill Generator | §3 하지 않는 것 | 커스텀 시나리오 제작 수요 신호 확인 후, "기존 도메인 × 변형 조합기"로 좁혀서 |

## 7. ADR 후보

1. **포크는 영속 세션이 아니라 Redis 임시 상태다** (M6) — 작성함: [ADR-0046](adr/0046-forks-are-ephemeral-redis-state-not-sessions.md). Community C9가 같은 결정을 물려받음.
4. **공식 시나리오를 미션 콘텐츠로 한 번에 v2로 올린다** (§4) — 작성함: [ADR-0048](adr/0048-official-scenarios-move-to-mission-content-in-a-single-v2-bump.md).
2. **Deploy 도메인을 공식 도메인으로 추가하는가** (M9) — 작성함: [ADR-0049](adr/0049-deployment-is-an-official-drill-but-not-a-certification-domain.md). 공식 Drill이되 인증 대상 7개는 고정.
3. 하위 활동을 새 단계 타입이 아니라 기존 단계 안에 둔다(§3-1)는 것은 **ADR 대상이 아닙니다** — 대안(새 단계 타입)의 비용이 명백히 커서 진짜 트레이드오프가 아니고, 나중에 필요하면 새 단계 타입을 추가하면 됩니다.

## 8. 열린 질문

1. ~~M1 무관한 질문 감점~~ **확정(2026-10-01)** — 표시만 하고 감점하지 않는다. 평가 프롬프트에도 "확인하지 않은 핵심 질문"만 넘긴다.
2. ~~M2·M7 입력 필수 여부~~ **확정** — 선택. 면접형 타이머 모드에서만 M4 방어를 필수로 한다(M2·M7은 시간 압박 속에 강제하면 설계 시간을 잠식).
3. ~~새 버전 전환 시점~~ **확정** — §4, 기능 완료 후 7개를 한 번에.

## 9. 성공 지표

| 지표 | 왜 |
|---|---|
| M1 도입 후 "요구사항 해석력" 점수 분포 | 질문하기가 설계 품질로 이어지는가 |
| M2 적중률의 재도전 간 변화 | 규모 감각이 늘어나는가 |
| Partial Recovery 비율 추이 | 완화와 복구의 구분을 학습하는가 |
| Counterfactual 사용 세션의 재도전율 | "다르게 했다면"이 다음 행동을 바꾸는가 |
| M8 도입 후 설계에 쓰인 인프라 종류 수 중앙값 | 과잉 설계 습관이 줄어드는가 |
