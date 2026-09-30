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

- 벤치마크는 `scenario_version_id` 안에서만 비교하므로([LEARNING_COMMUNITY_PLAN.md §6.1](LEARNING_COMMUNITY_PLAN.md)) **새 버전은 표본 0에서 다시 시작**합니다. 공식 7개 시나리오를 한꺼번에 새 버전으로 올리지 말고, 한 도메인(coupon)으로 먼저 검증합니다.
- 공개 풀이·토론도 버전 단위이므로 같은 영향을 받습니다.

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

### M8 — 제약 · 비용 · 운영 복잡도 ✦

- 시나리오 콘텐츠에 `constraints: {budgetPerMonth, teamSize, opsExperience: {kafka: LOW, redis: HIGH, …}}`.
- **비용**: 캔버스 노드 kind × 수량(트레이트의 `readReplicaCount`, `podReplicas`, `consumerCount` 등) × 단가표(설정 데이터, [ADR-0006](adr/0006-config-as-data.md)) → 월 추정 비용을 캔버스 상단에 `예산 대비 +24%`로. scale-out 액션에는 비용 증분을 표시.
- **운영 복잡도**: 서로 다른 인프라 종류 수 × 팀 운영 경험 가중치 → `복잡도 82 / 팀 역량 43`. 점수가 아니라 **평가 프롬프트에 주는 사실**입니다 — "기술을 많이 쓰면 좋은 설계"라는 습관을 교정한다는 원본의 목적은 LLM이 이 사실을 근거로 트레이드오프를 지적할 때 달성됩니다.
- "CFO: 인프라 비용 30% 절감" 같은 이벤트는 **새 코드 없이 FOLLOWUP 변형 콘텐츠**로 추가할 수 있습니다.
- 모든 금액 옆에 "추정치" 표기. 실제 클라우드 단가 추적은 하지 않습니다.

### M9 — Deploy/Canary 도메인 + 변경 검토

- 8번째 도메인 `deployment`: 에러율 = `카나리 트래픽 비율 × 새 버전 결함률`. 액션은 `CONTINUE_ROLLOUT / PAUSE_ROLLOUT / ROLLBACK`, 사용자가 전략(Rolling/Blue-Green/Canary)·카나리 비율·롤백 임계값을 설계 단계에서 정합니다. 기존 7개와 메커니즘이 확실히 다르므로 ADR-0012 조건을 만족합니다.
- 시간이 흘러야 카나리가 5% → 10% → 25%로 진행되므로 **시간축(O0-a)이 필수**입니다.
- 인시던트 직전 **변경 검토**: 릴리즈 변경 목록(`Retry 추가`, `Redis TTL 300→60`…) 중 가장 위험한 것을 고르고, 실제 원인과 대조.
- **번지는 범위**가 커서 후순위입니다: 공식 도메인이 늘면 인증(ADR-0032)·DrillScore 상한(현재 940, ADR-0042)·트랙·개념 `relatedDomains`·real-infra 선택 맵(ADR-0018)이 전부 영향을 받습니다. 착수 시 영향 목록을 PLAN.md에 먼저 적습니다.

### M10 — 변형 기반 숙련 신뢰도 ✦

- 도메인별로 **서로 다른 꼬리설계 변형을 몇 개 통과했는지**를 파생해 `신뢰도`로 표시합니다. "한 번 맞혔다고 Mastered 처리하지 않는다"(원본 §25).
- 저장 없이 세션·제출 이력에서 계산([ADR-0011](adr/0011-derived-values-are-never-persisted.md)). 트랙 페이지와 인증 페이지에 표시하고, Learning의 개념 숙련 표시([LEARNING_EXPANSION_PLAN.md](LEARNING_EXPANSION_PLAN.md) L7)와 같은 규칙을 공유합니다.
- **DrillScore에는 넣지 않습니다** — ADR-0042는 "도메인별 최고점"으로 반복을 무력화했는데, 신뢰도를 점수에 섞으면 반복이 다시 보상받습니다.

### M11 — 인시던트 상태 공지 ✦

- INCIDENT 답안에 구조화 필드 `고객 공지 초안`을 추가(`We are investigating elevated latency affecting checkout…`). 평가는 **기존 INCIDENT 평가 한 번에 합쳐서** 루브릭 "커뮤니케이션(5점)"의 근거로 씁니다. 원본의 명료성·정확성·과장 여부는 평가 프롬프트의 관점 목록으로.
- 상황이 바뀔 때마다 새 공지를 요구하는 다회 평가는 LLM 비용 때문에 보류.

### M12 — 개인 Runbook

- 조사 행위 기록(O0-b) 이후. 포스트모템에서 도메인별 Runbook(조사 종류·액션·자유 문장의 순서 목록)을 작성·수정하고, 다음 같은 도메인 인시던트에서 **실제 조사 이벤트와 대조**해 `Step 2 ✕ — connection pool 확인 안 함`을 보여줍니다.
- 반복할수록 사용자의 Runbook이 자라는 것이 요점이므로 사용자 단위로 저장(`user_runbooks`)합니다 — 세션 파생값이 아니라 사용자 입력입니다.

---

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

1. **포크는 영속 세션이 아니라 Redis 임시 상태다** (M6) — Community C9가 같은 결정을 물려받음.
2. **Deploy 도메인을 공식 도메인으로 추가하는가** (M9) — 인증·랭킹 상한까지 번지는 결정.
3. 하위 활동을 새 단계 타입이 아니라 기존 단계 안에 둔다(§3-1)는 것은 **ADR 대상이 아닙니다** — 대안(새 단계 타입)의 비용이 명백히 커서 진짜 트레이드오프가 아니고, 나중에 필요하면 새 단계 타입을 추가하면 됩니다.

## 8. 열린 질문

1. **M1 질문 카드에서 무관한 질문을 고르는 것을 감점할 것인가.** 원본은 `Irrelevant questions 3`을 표시했지만, 호기심을 벌주면 질문 자체를 줄입니다. 표시만 하고 감점은 하지 않기를 권합니다.
2. **M2·M7 입력을 필수로 할 것인가.** 초급은 선택, 면접형 타이머 모드는 필수를 권합니다.
3. **공식 시나리오 새 버전 전환 시점.** §4 — coupon 1개로 M1·M2·M7을 한꺼번에 검증한 뒤 나머지 6개를 한 번에 올리는 것을 권합니다(버전을 여러 번 올리면 벤치마크 표본이 매번 초기화됨).

## 9. 성공 지표

| 지표 | 왜 |
|---|---|
| M1 도입 후 "요구사항 해석력" 점수 분포 | 질문하기가 설계 품질로 이어지는가 |
| M2 적중률의 재도전 간 변화 | 규모 감각이 늘어나는가 |
| Partial Recovery 비율 추이 | 완화와 복구의 구분을 학습하는가 |
| Counterfactual 사용 세션의 재도전율 | "다르게 했다면"이 다음 행동을 바꾸는가 |
| M8 도입 후 설계에 쓰인 인프라 종류 수 중앙값 | 과잉 설계 습관이 줄어드는가 |
