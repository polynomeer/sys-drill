# Drills 고도화 설계 — Simulation-Driven Architecture Canvas

> 원본: [`docs/archive/sysdrill_advanced_plan.md`](archive/sysdrill_advanced_plan.md), [`docs/archive/SysDrill_Drills_Advanced_Plan.docx`](archive/SysDrill_Drills_Advanced_Plan.docx) (2026-09-09 추가). 두 문서를 하나의 비전으로 종합하고, 현재 구현 상태와 대조해 격차를 표시하고, [ROADMAP.md](ROADMAP.md)에 편입 가능한 단계별 작업계획을 제시한다.
>
> 이 문서는 **제품 결정 문서가 아니라 결정을 위한 근거 문서**다. §4에서 이 문서가 실제로 취하는 입장을 밝히고, §8에서 아직 사용자가 결정해야 할 지점을 명시한다. [PRD.md](PRD.md)/[ARCHITECTURE.md](ARCHITECTURE.md)/[ROADMAP.md](ROADMAP.md)를 대체하지 않는다.

## 0. 두 원본 문서의 관계

- `sysdrill_advanced_plan.md`(30절, UI/UX 관점) — Workspace 레이아웃, Architecture Canvas, Test/Traffic/Chaos Lab, Observability Lab, Incident Investigation UX, AI 역할, Skill Graph, Postmortem/Replay, Sandbox, Drill Map 등 **제품 경험** 전반을 정의.
- `SysDrill_Drills_Advanced_Plan.docx`(23절, 제품/엔지니어링 관점) — 같은 비전을 Drill 정의·Simulation Engine 9모듈·10-엔터티 데이터 모델·평가 원칙·7-Wave 로드맵으로 **구조화**. 22절에서 "모든 기능을 최종 구현한다는 전제"를 명시.
- 두 문서는 사실상 하나의 비전을 다른 층위(UX vs 엔지니어링)에서 기술한 것이라 겹치는 내용이 많다. 이하에서는 중복을 제거하고 하나의 비전으로 합쳐 다룬다.

## 1. 종합 비전 요약

| 축 | 핵심 내용 |
|---|---|
| Drill의 재정의 | 단일 문제가 아니라 "요구사항 → 설계 → 구현 → 부하 → 관측 → 장애 → 복구 → 개선"을 상태 유지한 채 겪는 System Mission |
| Workspace | Architecture/Code/Traffic/Metrics/Logs/Traces/Terminal 탭 + 하단 Timeline + 상단 Status(RUNNING/DEGRADED/INCIDENT/RECOVERED)를 단계가 바뀌어도 유지 |
| Architecture Canvas | 노드가 아이콘이 아니라 **Simulation Engine과 연결된 실행 가능한 모델** — 노드별 config(인스턴스 수/pool/TTL 등)를 바꾸면 capacity/latency/cost가 재계산됨 |
| Labs | Test(기능/동시성/장애/부하/카오스), Traffic(패턴+워크로드 시퀀스), Chaos(인스턴스/AZ/네트워크/DB 장애 주입), Observability(실제 metrics/logs/traces + Dashboard Builder + Alert Rule) |
| Incident UX | 객관식 원인 선택 폐지 → 조사 도구(Metrics/Logs/Traces/Architecture/Deploy/Config/Flag/Dependency)로 가설을 세우고 액션을 실행 |
| Trade-off Engine | 모든 대응 액션에 즉시 효과 + 장기 리스크를 모델링하고, 주요 결정을 ADR(Why/Alternatives/Trade-offs/Assumptions)로 기록 |
| AI 5역할 | Mentor / Evaluator / Scenario Director / Interviewer / Postmortem Coach — "Simulation Engine이 만든 사실을 해석·전달"하는 역할이 우선 |
| Skill Graph | 9개 상위 역량 + 세부 skill, 추천은 "다음 챕터"가 아니라 취약 skill을 보완하는 Mission |
| Postmortem/Replay | MTTD/MTTR/impact/root cause + 특정 시점 재생(Replay) + 전문가 대응과 비교(Expert Replay) + Community 벤치마크 |
| Sandbox | 완료한 시스템을 저장해 변수(트래픽/인스턴스/토폴로지)를 바꿔가며 what-if 비교 (estimate임을 명시) |
| Drill Map | L1 Components → L2 Services → L3 Distributed Systems → L4 Production Systems → L5 Production Incidents, prerequisite 그래프 |
| 데이터 모델 | DrillTemplate / MissionRun / SystemTopology / SimulationEvent / UserAction / TelemetrySnapshot / DecisionRecord / Evaluation / ReplayFrame / SandboxSystem (10 엔터티) |
| Simulation Engine | Component Model / Dependency Graph / Traffic Engine / Event Engine / State Engine / Telemetry Generator / Action Engine / Scenario Engine / Scoring Engine (9 모듈), 동일 seed 재현성 요구 |
| 평가 원칙 | Outcome/Diagnosis/Efficiency/Safety/Cost/Observability/Reasoning — "AI 단독이 아니라 Simulation 결과 + rule-based evidence 우선" |
| 로드맵(원본) | Wave 1 Core → 2 Build → 3 Ops → 4 Intelligence → 5 Sandbox → 6 Ecosystem → 7 Depth, "모든 기능을 최종 구현한다는 전제" |

## 2. 현재 아키텍처 대비 격차 분석

> 근거: 백엔드 코드 인벤토리(2026-09-09 조사). 파일 경로는 모두 `backend/src/main/kotlin/com/sysdrill/backend/` 기준 상대경로.

| 제안 기능 | 현재 상태 | 격차 |
|---|---|---|
| Architecture Canvas ↔ Simulation 연동 | `DiagramCanvas.tsx`는 7종 고정 노드를 **라벨 텍스트로만** Mermaid 문자열로 직렬화(`serializeToMermaid`). 백엔드에 토폴로지 개념 자체가 없음 — `architecture/` 패키지(Architecture Linter)는 OpenAPI 파싱 전용이고 `simulation/`과 무관 | **전무.** 노드별 config(인스턴스/pool/TTL 등)를 저장·해석하는 경로가 없음. §3에서 다룸 |
| Simulation Engine 9모듈 | `simulation/SimulationEngine.kt`(인터페이스) + `RuleBasedSimulationEngine.kt`(도메인별 순수함수 7개) + `SystemState.kt`(단일 전역 스냅샷, 노드별 상태 없음) | Component Model/Dependency Graph/Telemetry Generator는 **전무**. Traffic/Event/Action은 현재 구현의 축소판으로 부분 대응. Scenario Engine은 아래처럼 의외로 씨앗이 있음 |
| per-component(노드별) 시뮬레이션 상태 | `SystemState`는 시스템 전체 1개 스냅샷(`trafficRps`, `p95LatencyMs`, `dbReadLoad` 등), 노드 구분 없음. ADR-0011: 이 값은 항상 재계산, 절대 영속화 안 함 | **전무** — Redis/API/DB를 각각의 상태를 갖는 개체로 모델링하려면 새 데이터 모델 필요 |
| Scenario Authoring DSL | `ScenarioStep`에 이미 `stepType`, `triggerCondition: jsonb`, `content: jsonb` 필드 존재(`scenario/ScenarioStep.kt`) — 트리거 조건을 데이터로 표현하는 기반은 있음. 다만 실제 7개 도메인의 시뮬레이션 수식 자체는 `RuleBasedSimulationEngine.kt`에 하드코딩 | **부분** — 저장 형식(jsonb)은 재사용 가능, DSL 문법/에디터/시뮬레이션 수식의 데이터화는 신규 |
| Action Engine(범용) | `SimulationActionType.kt`: 도메인당 3개씩 고정 21-value enum, `applyAction`이 세션 도메인과 다른 액션을 거부 | **부분** — 액션이 상태에 영향을 주는 구조 자체는 있으나, "scale/rollback/failover/feature flag" 같은 범용 액션 카탈로그가 아니라 도메인 결합형 |
| Observability Lab(실제 metrics/logs/traces) | `LogViewer.tsx`/`WargameLive.tsx`는 **클라이언트에서 `SystemState` 한 스냅샷으로부터 합성**한 값을 보여줌(자체 주석에 명시). 백엔드에 `/metrics`, `/logs`, `/traces` 같은 엔드포인트 없음 | **전무 — 이미 ROADMAP.md Phase 3가 계획 중**(OTel 기반 실 파이프라인). 신규 스코프 아님, Phase 3와 병합 대상 |
| Test/Traffic/Chaos Lab | `simulation/realinfra/`에 쿠폰·알림 2개 도메인만 Toxiproxy 기반 실인프라 파일럿(부하생성기/장애주입기) 존재. 나머지 5개 도메인·범용 Lab UI 없음 | **부분** — 실인프라 배관 자체는 검증됨(2개 도메인). 7개 도메인 일반화 + Lab UI는 신규 |
| Trade-off Engine + 인앱 ADR 로깅 | 없음. 이 저장소 자체의 ADR(`docs/adr/`)은 개발자가 수동 작성하는 것이지, 훈련생이 세션 중 남기는 `DecisionRecord`가 아님 | **전무** |
| AI 5역할 | **2026-09-11 갱신**: 원안의 5역할 중 4역할(Mentor/Director/Interviewer/Postmortem Coach)이 전부 완료. Evaluator(`evaluation/HybridRuleAiEvaluator.kt`) 기반 Interviewer, Postmortem Coach(`PostmortemService.save()`), Mentor(`mentor/MentorService.kt`, 온디맨드 힌트)는 기존 트리거 시점 재사용. Director(`SimulationService.startIncident`가 반환하는 `IncidentStartResult.narration`, `purpose='director_narration'`)만 유일하게 새 트리거 시점(인시던트 시작)이 필요했다 — real-infra 세션은 k6 레이턴시 누적을 피하려 제외, LLM 실패 시 기존 정적 문자열로 fail-open. 넷 다 새 오케스트레이션/엔터티 없이 `LlmClient`/`PromptTemplate`(purpose별) 배관만 재사용 | **완료** — 원안의 "Evaluator에 해당하는 1역할"에서 4역할로 확장 완료. 5번째(Evaluator 자체는 이미 있었으니 사실상 전체)는 이걸로 마무리 |
| Skill Graph | **2026-09-10 갱신**: `SkillProfileController`가 이제 도메인 그룹핑 위에 `categoryByRiskKey`(6개 교차-도메인 역량 카테고리, `RuleEvaluator.kt`)로 한 겹 더 계층화해 `weaknessesByCategory`/`recommendedCategory`를 계산(여전히 ADR-0011 방식, 읽기 시점 재계산, 저장 스키마 무변경). 원안의 "9개 상위 역량 + 세부 skill" 그래프(더 정교한 타이포노미, 별도 모델링)는 아직 아님 — 지금은 25개 riskKey를 6개 고정 카테고리로 수동 분류한 첫 슬라이스 | **완료(첫 슬라이스)** — 파이프라인 재사용 그대로, 계층 추가까지 완료. 9-역량 원안 수준의 세분화는 신호 확인 후 후속 |
| Postmortem/Replay/Expert Replay | `getTimeline` replay(`SimulationService.kt`)로 액션 이력 재생은 이미 있음. MTTD/MTTR 집계, Expert Replay 비교, Community Median은 없음 — **ROADMAP.md Phase 3에 "Incident Replay, Postmortem 작성"으로 이미 예정** | **부분**, Phase 3와 병합 대상 |
| System Sandbox / What-if | **2026-09-11 갱신**: `design/[sessionId]/page.tsx`가 COMPLETED 세션에서도 "샌드박스에서 계속 실험하기" 토글로 `WargameLive`를 재마운트 — `SimulationService`의 시뮬레이션 엔드포인트가 애초에 `Session.status`를 전혀 확인하지 않아(신규 엔터티 불필요, 프론트 렌더링 조건만 변경) `AppliedAction`/`SimulationStateStore`/저장된 `SystemTopology`를 그대로 재사용. `submit`/`advance`/재채점 경로는 무변경(계속 COMPLETED 종결) | **완료(첫 슬라이스)** |
| Drill Map(의존성 그래프) | **2026-09-11 갱신**: 조사 결과 실제 기반이 전무함을 재확인(prerequisite 필드 없음, 공식 시나리오 난이도가 전부 동일값이라 진행 경로 데이터 자체가 없음) — 사용자 확인 하에 보류, System Sandbox를 먼저 진행 | **전무, 보류** |
| Build↔Design 컴포넌트 재사용 | Build Mode는 6개 챌린지(`rate-limiter`/`queue`/`circuit-breaker`/`distributed-lock`/`retry-backoff`/`event-bus`, `BuildChallenge` 데이터 기반 — 1개가 아니라 이미 6개, 일반화도 돼 있음). 단, 완료한 구현이 Design/Incident 세션의 실제 컴포넌트로 재사용되는 연결은 없음(Bridge Mode가 `buildSubmissionId` FK로 세션에 연결하는 정도까지만) | **부분** |

**한 가지 발견**: Build Mode는 요약에 있던 "1개 챌린지"보다 이미 진전돼 있다(6개, 데이터 기반 일반화 완료) — 이 설계서의 다른 항목에도 참고할 만한 선례("하드코딩 대신 데이터 기반 확장"이 이미 이 코드베이스에서 실제로 일어난 사례).

## 3. 기존 ADR/로드맵과 충돌하는 지점

이 비전에서 가장 근본적인 지점은 **Architecture Canvas를 "실행 가능한 시스템 모델"로 만드는 것**이다. 이는 현재 명시적으로 반대 방향으로 결정된 두 ADR과 정면으로 충돌한다.

- **[ADR-0036](adr/0036-diagram-canvas-is-an-input-method-that-still-serializes-to-mermaid-text.md)**: "다이어그램 캔버스는 입력 도구일 뿐, **두 번째 소스가 아니다**"를 명시적으로 결정했다. 7종 고정 노드는 라벨 텍스트 필드 외 config가 없고, 저장은 localStorage 임시 draft일 뿐 백엔드 필드가 아니다. 새 비전이 요구하는 "노드별 instances/CPU/pool/TTL을 설정하고 시뮬레이션이 그걸 읽는" 구조는 이 ADR이 명시적으로 피한 바로 그것이다.
- **[ADR-0035](adr/0035-diagrams-are-mermaid-text-embedded-in-the-existing-answer-not-a-new-field-or-editor.md)**: 다이어그램은 free-text 답안에 삽입되는 Mermaid 텍스트이며, `Session.submit`의 `rawText`는 그대로 유지된다는 원칙. Canvas가 구조화된 토폴로지 데이터를 별도 필드로 저장하기 시작하면 이 원칙 자체가 깨진다.
- **[ADR-0011](adr/0011-derived-values-are-never-persisted.md)**: "파생값은 항상 재계산, 절대 영속화 안 함" 원칙은 유지 가능하다 — 노드별 config(토폴로지)는 `DesignTraits`와 마찬가지로 **입력**이지 파생값이 아니기 때문. 다만 지금의 "세션당 도메인 하나의 flat traits"보다 훨씬 세분화된 입력 모델(`SystemTopology`)이 새로 필요하다는 점에서 이 ADR의 정신은 지키되 스코프는 크게 늘어난다.

**결론**: Architecture Canvas를 살아있는 시뮬레이션 모델로 바꾸는 것은 ADR-0036의 명시적 반전(supersede)이 필요한 결정이다 — "되돌리기 어렵고, 맥락 없이 보면 놀랍고, 진짜 대안(지금처럼 라벨-only 유지)이 있는" 세 조건을 모두 만족하므로, **채택하기로 결정하는 순간 새 ADR을 써야 한다.** 이 설계서 자체는 그 결정을 내리지 않는다(§4, §8).

또한 Observability/Chaos/Postmortem·Replay는 이미 [ROADMAP.md Phase 3](ROADMAP.md#phase-3--real-runtime--실전-시뮬레이션-강화)가 계획하고 있던 항목과 크게 겹친다 — 새 로드맵을 병행 신설하는 대신 Phase 3에 흡수하는 것이 이 저장소의 "로드맵 운영 원칙"(§6)과 맞는다.

## 4. 이 설계서가 취하는 입장

원본 문서 22절의 "모든 기능을 최종 구현한다는 전제"는 이 프로젝트의 로드맵 운영 원칙("각 Phase는 이전 Phase의 핵심 검증 질문에 긍정적 신호가 있어야 다음으로 진행한다", [ROADMAP.md](ROADMAP.md) 하단)과 충돌한다 — 지금은 Phase 1(MVP)조차 가치 검증 전이다.

따라서 이 문서는:

1. 두 원본 문서를 **North Star(최종 형태)**로 채택하고 문서화한다(§1, §5) — 방향 자체를 부정하지 않는다.
2. **즉시 전체 착수를 제안하지 않는다.** 대신 이미 계획된 [ROADMAP.md Phase 3](ROADMAP.md#phase-3--real-runtime--실전-시뮬레이션-강화)에 흡수 가능한 부분(Observability/Chaos/Replay)과, Phase 3 이후로 미뤄야 할 부분(Architecture Canvas as live model, Skill Graph, AI 5역할, Sandbox, Drill Map)을 나눈다(§6).
3. Architecture Canvas를 실행 가능한 모델로 만들지 여부는 **ADR-0036을 뒤집는 결정**이므로, 이 문서가 대신 결정하지 않고 §8에서 사용자 결정 사항으로 남긴다.

## 5. 제안 아키텍처 (North Star, 채택 시 설계)

### 5.1 신규/확장 데이터 모델

| 원본 엔터티 | 현재 대응 | 필요 작업 |
|---|---|---|
| DrillTemplate | `Scenario` + `ScenarioVersion` | 확장 — prerequisite/skill mapping 필드 추가 |
| MissionRun | `Session` | 재사용 가능 — 이미 `seed`, `status`, `currentPhase` 보유 |
| SystemTopology | **없음** | 신규 — 노드별 config를 담는 엔터티. Architecture Canvas 채택 시에만 필요(§3) |
| SimulationEvent | `AppliedAction`(액션만 기록) | 확장 — deploy/failure/recovery 등 비-사용자 이벤트도 포괄하도록 |
| UserAction | `AppliedAction` | 재사용 가능 |
| TelemetrySnapshot | **없음**(클라이언트 합성) | 신규 — 서버가 실제 시계열 telemetry를 생성·저장 |
| DecisionRecord | **없음** | 신규 — 세션 내 ADR 미니 로그 |
| Evaluation | `Evaluation` | 재사용, 루브릭 차원 확장 필요(Reasoning/Cost/Observability 축) |
| ReplayFrame | `getTimeline` 응답(즉석 계산) | 재생용으로는 충분, MTTD/MTTR 집계 로직만 추가 |
| SandboxSystem | **없음** | 신규 |

### 5.2 Simulation Engine 9모듈 매핑

| 원본 모듈 | 현재 대응 | 필요 작업 |
|---|---|---|
| Component Model | `DesignTraits`(도메인당 flat) | 노드별 세분화는 Canvas 채택 여부에 종속 |
| Dependency Graph | **없음** | 신규 |
| Traffic Engine | `RuleBasedSimulationEngine`의 트래픽 관련 수식 | 확장 |
| Event Engine | `SimulationActionType` + `AppliedAction` | 확장 — 사용자 액션 외 스케줄/랜덤 이벤트 추가 |
| State Engine | `SimulationSessionState`(Redis) | 확장 |
| Telemetry Generator | 프론트엔드 클라이언트 합성(`WargameLive.tsx`) | **서버로 이동** — Phase 3 Observability와 동일 작업 |
| Action Engine | `SimulationActionType`(도메인 결합 21종) | 범용화 — 실제로는 §6에서 후순위 |
| Scenario Engine | `ScenarioStep.triggerCondition/content`(jsonb) | **이미 씨앗 존재** — DSL 문법/에디터만 얹으면 됨 |
| Scoring Engine | `HybridRuleAiEvaluator` | 확장 — Safety/Cost/Observability 평가축 추가 |

### 5.3 Architecture Canvas 연동 (조건부 — §8 결정 이후)

채택 시: `DiagramCanvas.tsx`의 7종 고정 노드에 config 패널 추가 → 노드별 config를 `SystemTopology`에 구조화 저장 → `RuleBasedSimulationEngine`이 도메인 flat traits 대신 이 토폴로지를 읽어 계산 → ADR-0036을 supersede하는 새 ADR 작성. 이 경로는 지금 착수하지 않는다(§4).

## 6. 단계별 작업계획 ([ROADMAP.md](ROADMAP.md) 확장 제안)

기존 로드맵 운영 원칙(신호 확인 후 다음 단계)을 그대로 따른다. 새 Phase를 신설하지 않고, 겹치는 부분은 **Phase 3에 흡수**하고, 신규 영역만 **Phase 3 이후 후보**로 추가한다.

### Phase 3 확장 (겹치는 부분 흡수 — 기존 Phase 3 검증 신호 확인 후)

- **3-A Telemetry Generator 서버 이전**: `LogViewer`/`WargameLive`의 클라이언트 합성 로직을 백엔드로 옮겨 실제 `/metrics`, `/logs` 엔드포인트로 노출. OTel 도입과 자연스럽게 묶임(기존 Phase 3 항목).
- **3-B Chaos/Traffic Lab 일반화**: 현재 쿠폰·알림 2개 도메인에만 있는 `realinfra/` 파일럿을 Lab UI 형태로 나머지 도메인까지 확장.
- **3-C Postmortem/Replay 고도화**: 기존 `getTimeline` 위에 MTTD/MTTR 집계 + Postmortem 작성 UI(이미 Phase 3 항목).
- 검증 질문(기존과 동일): 면접/실습/팀 훈련으로 확장 가능한가?

### Phase 3 이후 후보 (신규 영역)

~~**Architecture Canvas as live model**~~ — **완료(2026-09-10)**. ADR-0037로 채택, Slice 1~4(매핑 → 영속화 → 엔진 직접 읽음 → 엣지 인식)로 구현 완료. §8 참고.

**우선순위 결정 완료(2026-09-10)** — `docs/PRD.md`의 핵심 가설(시도-피드백 루프 품질, "반복되는 사고 패턴" 장기 기억, Interview 유료 티어)에 가까운 순서로 정렬. 아래 순번이 착수 순서다.

| 순위 | 항목 | 선행 조건 | 검증 질문 |
|---|---|---|---|
| 1 | ~~AI 4역할 추가(Mentor/Director/Interviewer/Postmortem Coach)~~ — **전체 완료(2026-09-11)**, [PLAN.md "AI 4역할 추가" Slice 1~4](../PLAN.md) 참고 | Evaluator 배관 재사용, `interviewMode`(면접형 타이머) 이미 존재 | 역할별 분리가 단일 Evaluator보다 학습 효과가 있는가? |
| 2 | Skill Graph(계층화) — 첫 슬라이스 **완료(2026-09-10)**, [PLAN.md "Skill Graph(계층화) — 첫 슬라이스"](../PLAN.md) 참고 | SkillProfile 파이프라인 재사용 | 상위 역량 계층이 추천 품질을 실제로 개선하는가? |
| 3 | Scenario Engine → DSL/Authoring — 첫 슬라이스 **완료(2026-09-11)**, [PLAN.md "Scenario DSL/Authoring"](../PLAN.md) 참고. **정정**: `ScenarioStep.triggerCondition`은 실제로는 어디서도 읽히지 않는 죽은 데이터였다(재사용 가능한 "기반"이 아니었음) — 실제로 구현한 건 커스텀 시나리오(ADR-0024)가 기존 7개 도메인 중 선택 시 INCIDENT 단계를 추가할 수 있게 한 것(ADR-0038). 진짜 조건부 분기 엔진은 여전히 미구현, 별도 후속 후보로 남음 | ~~`ScenarioStep` jsonb 확장(이미 기반 있음)~~, 조직 커스텀 시나리오 API(ADR-0024)로 일부 선행 구현 존재 | 콘텐츠 제작자가 코드 없이 시나리오를 늘릴 수요가 있는가? |
| 4 | Drill Map(의존성 그래프) — **보류(2026-09-11)**, 실제 prerequisite/난이도 데이터가 없어 가짜 데이터로 그래프 UI만 만드는 상황이라 사용자 확인 하에 순서를 미룸 | Marketplace 확장 | 평면 목록보다 그래프 탐색이 실제로 더 쓰이는가? |
| 5 | System Sandbox / What-if — 첫 슬라이스 **완료(2026-09-11)**, [PLAN.md "System Sandbox / What-if"](../PLAN.md) 참고. **정정**: 신규 엔터티가 필요하다는 전제가 틀렸음 — 시뮬레이션 엔드포인트는 애초에 세션 상태를 안 봄 | ~~SandboxSystem 신규 모델(완전 신규, 5개 중 유일하게 새 엔터티가 필요)~~ | "완료 후 계속 실험"하고 싶다는 수요가 실제로 있는가? |

1·2번(AI 4역할/Skill Graph)은 기존 파이프라인을 재사용해 리스크가 낮고 PRD 핵심 가치(피드백 품질·장기 추적)에 가장 가깝다는 게 선정 이유 — "핵심 루프 품질" 축을 우선한 선택이다. 3번(Scenario DSL)은 콘텐츠 확장, 4·5번(Drill Map/Sandbox)은 핵심 루프와 거리가 멀거나(디스커버리 UX) 신규 모델이 필요해(Sandbox) 리스크가 커 뒤로 미뤘다.

이 후보들은 Phase 4(Team/B2B), Phase 6(Architecture Linter)와도 자원을 다툰다 — 위 순서는 고정이 아니라, 1·2번 진행 중 새 신호가 나오면 다시 조정할 수 있다.

## 7. 하지 않는 것 / 리스크

- 9개 Simulation Engine 모듈, 10개 엔터티, AI 5역할을 한 번에 만들지 않는다 — Phase 3 흡수분 외에는 전부 "신호 확인 후" 후보다.
- per-node(노드별) 시뮬레이션 상태·Architecture Canvas의 second-source-of-truth화는 이 문서만으로 결정하지 않는다 — ADR-0036 반전은 되돌리기 비용이 크므로 별도 결정이 필요하다(§3, §8).
- Scenario DSL을 처음부터 새로 설계하지 않는다 — `ScenarioStep`의 기존 jsonb 필드를 확장하는 점진적 접근을 택한다.
- Build 챌린지를 6개에서 더 늘리는 것과 이 문서는 무관하다(이미 데이터 기반으로 일반화돼 있어 별도 스코프).

## 8. 다음 액션 — 사용자 결정 필요

1. ~~**Architecture Canvas를 실행 가능한 시뮬레이션 모델로 만들 것인가?**~~ **결정 완료(2026-09-09)** — 실행 가능한 모델로 전환. [ADR-0037](adr/0037-architecture-canvas-becomes-the-simulation-topology-source-of-truth.md)로 기록(ADR-0036 supersede). 1차 슬라이스는 완전 자유형 `SystemTopology` 대신 기존 `DesignTraits`에 캔버스 노드 config를 매핑하는 작은 범위로 구현 완료 — [PLAN.md "Drills 고도화" Slice 1](../PLAN.md) 참고. 2차 슬라이스(2026-09-10)로 `SystemTopology` 엔터티를 추가해 캔버스 그래프를 세션당 서버에 영속화했다 — [PLAN.md "Drills 고도화" Slice 2](../PLAN.md) 참고. 3차 슬라이스(2026-09-10)로 `SimulationService.startIncident`가 저장된 토폴로지를 서버에서 직접 읽어 `DesignTraits`를 계산하게 했다(같은 kind 노드가 여러 개면 필드별 SUM/LAST 집계, ADR-0037이 "작업계획 단계에서 결정"으로 미뤘던 마지막 질문) — [PLAN.md "Drills 고도화" Slice 3](../PLAN.md) 참고. 4차 슬라이스(2026-09-10)로 §5.2 "Dependency Graph"의 가장 작은 형태(엣지로 연결된 노드만 집계에 참여, 고립 노드 제외)를 추가했다 — [PLAN.md "Drills 고도화" Slice 4](../PLAN.md) 참고. 방향성 있는 진입점 기반 도달 가능성 추적은 여전히 후속 후보로 남아 있다.
2. ~~**Phase 3 확장(§6 3-A/3-B/3-C)부터 순서대로 진행할 것인가?**~~ **결정 완료(2026-09-10)** — 3-A(로그 심각도 백엔드 이전)/3-B(Traffic Lab)/3-C(Postmortem 집계) 전부 구현 완료. [PLAN.md](../PLAN.md) 해당 라운드 참고.
3. ~~Phase 3 이후 후보(§6 표) 중 우선순위를 매길 것인가?~~ **결정 완료(2026-09-10)** — "핵심 루프 품질" 축 우선, §6 표에 순번 기록: AI 4역할 → Skill Graph → Scenario DSL → Drill Map → Sandbox.

결정되는 대로 이전 UI/UX 리뉴얼 작업과 동일한 방식(Round 단위 조사 → 계획 → 구현 → 검증 → `PLAN.md` 기록)으로 착수할 수 있다 — 다음 착수 대상은 §6 표 1순위, **AI 4역할 추가**다.
