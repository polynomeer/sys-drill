# Observability(관측) UI 개선 계획안

> 작성 2026-09-30. 원본: [`archive/monitoring-ui-proposal.md`](archive/monitoring-ui-proposal.md) (28절).
>
> 관련 문서 — [DRILLS_SIMULATION_VISION.md](DRILLS_SIMULATION_VISION.md)(Phase 3-A 로그 심각도 이전 등 선행 작업), [ARCHITECTURE.md §6](ARCHITECTURE.md)(Simulation Engine), 같은 날 작성한 형제 계획안 [DRILLS_EXPANSION_PLAN.md](DRILLS_EXPANSION_PLAN.md) · [LEARNING_EXPANSION_PLAN.md](LEARNING_EXPANSION_PLAN.md) · [COMMUNITY_EXPANSION_PLAN.md](COMMUNITY_EXPANSION_PLAN.md).
>
> 이 문서는 **계획안**이며 아직 구현되지 않았습니다. 원본 제안을 현재 코드와 대조해 채택·축소·보류를 가르고, 채택분을 독립 배포 가능한 슬라이스로 나눕니다. 네 계획안 중 이 문서를 가장 먼저 읽기를 권합니다 — §4의 두 기반 작업(시간축, 조사 행위 기록)을 나머지 세 문서가 전제로 씁니다.

---

## 1. 원본 제안 한 줄 요약

"그래프 몇 개 추가"가 아니라 **관측 자체를 Drill의 조작 인터페이스이자 학습 대상**으로 만들자는 제안입니다. 사용자가 `Metrics → Logs → Traces → Events → Architecture → Hypothesis`를 오가며 스스로 원인을 추론하고, 관측 설계(알림 규칙, SLO, 샘플링)까지 평가받게 합니다. 일관된 원칙은 **"증상은 보여주되 원인은 알려주지 않는다"** 입니다.

## 2. 지금 Wargame 화면의 실제 상태

| 구성 | 위치 | 실제로 하는 일 |
|---|---|---|
| 상태 계산 | `simulation/RuleBasedSimulationEngine.kt` | 도메인별 순수 함수가 `SystemState` **스냅샷 하나**를 계산. 시계가 없어 **액션을 적용할 때만 값이 바뀐다** |
| 지표 차트 | `WargameLive.tsx` → `MetricsHistoryCharts` | 3초 폴링 결과를 **클라이언트가 40포인트 쌓아** recharts 라인 차트 2개(RPS, 에러율)로. 새로고침하면 사라진다 |
| 지표 타일 | `MetricsPanel` + `ui/Gauge.tsx` | CPU/메모리 게이지 + 공통·도메인별 지표 타일 |
| 로그 | `LogViewer.tsx` | 검색·레벨 필터·자동 스크롤. 단 **내용은 타임라인과 상태 변화로부터 클라이언트가 합성**한 것(심각도 계산만 3-A에서 백엔드로 이전됨) |
| 트레이스 | — | UI 없음. 실제 인프라 coupon 경로에만 OTLP → Jaeger 스팬이 있음(ARCHITECTURE §14) |
| 액션 | 액션 패널 | 도메인당 3개 고정 액션(`SimulationActionType`), 파라미터·되돌리기 없음 |
| 리플레이 | `design/[sessionId]/replay` | 액션 단위 스냅샷 재생(`GET …/simulation/timeline`, 액션 이력으로 재계산) |
| 포스트모템 | `design/[sessionId]/postmortem` | MTTD/MTTR(인시던트 시작 → 첫/마지막 액션), 전후 지표, AI 코칭, 커뮤니티 벤치마크 |

원본의 28개 항목과 대조하면:

| 원본 항목 | 현재 | 판정 |
|---|---|---|
| 1 Mission Control Bar | 없음 (가장 가까운 것은 `PhaseTimer` + `StageList`) | **채택 — O1** |
| 2 Observe 워크스페이스로 묶기 | 차트·타일·로그가 한 화면에 세로로 나열 | **채택 — O1** |
| 3 Overview 대시보드 / 4 Golden Signals / 6 RED·USE | 지표는 있으나 신호 체계로 묶이지 않음 | **채택 — O1** (재배치 + 라벨링) |
| 5 Service Map | 캔버스 토폴로지는 있으나 설계 시점 전용 | **채택 — O3** |
| 7 Metrics Explorer / 8 상관 비교 | 없음 | **축소 채택 — O2** (오버레이·시간 범위까지. PromQL 없음) |
| 9 Logs Explorer / 10 Log Context | 합성 로그 뷰어 | **채택 — O4** (서버 생성 로그로 교체) |
| 11 Trace Explorer / 12 Metrics→Logs→Trace 이동 | 없음 | **축소 채택 — O4·O6** |
| 13 Change Overlay | 없음 (액션은 로그 줄로만) | **채택 — O2** |
| 14 Alert Center / 15 사용자 Alert Rule | 없음 | **채택 — O5** |
| 16 SLO 대시보드 · Burn Rate | 없음 (availability 지표만) | **채택 — O5** (burn rate는 단일 창) |
| 17 Business Metrics | 없음 | **보류** — 새 메커니즘 필요(§7) |
| 18 Synthetic / 19 Dependency Health / 20 Infra View | 없음 | **보류** — 멀티리전·파드 모델 없음 |
| 21 DB / 22 Kafka / 23 Cache 상세 | 도메인별 지표 타일 일부 | **축소 채택 — O3** (노드 상세 패널로. 파티션 skew·top query는 모델에 없음) |
| 24 Profiling / Flame Graph | 없음 | **기각(당분간)** — 코드 수준 병목을 모델링하지 않음 |
| 25 Monitoring Setup Phase | 없음 | **채택 — O7** |
| 26 Observability Cost / 27 Cardinality Incident | 없음 | **보류** — 새 인시던트 도메인 후보로 기록(§7) |
| 28 Monitoring Quality Score | 루브릭 Observability 10점(답안 텍스트 기준) | **채택 — O7** (근거 있는 규칙 판정만) |

---

## 3. 이 계획안이 취하는 입장

1. **원인을 칠하지 않는다.** 원본이 반복 강조한 원칙을 그대로 채택합니다. 서비스 맵·알림·Overview 어디에서도 "이게 원인"이라는 표시를 하지 않습니다. 임계값 색칠은 **모든 지표에 같은 규칙**(ARCHITECTURE §6.3의 utilization 밴드)으로만 합니다.
2. **새 관측 데이터는 전부 결정론적으로 파생한다.** 로그·트레이스·알림·SLO 모두 `(시드, 인시던트 시작, 액션 이력, 경과 시간)`에서 계산하고 저장하지 않습니다([ADR-0011](adr/0011-derived-values-are-never-persisted.md)). 리플레이와 포크(형제 문서의 Counterfactual / Fork My Run)가 같은 결과를 재현하려면 이것이 필수입니다.
3. **실제 인프라 모드는 따라오지 않아도 된다.** coupon·notification real-infra 세션은 스냅샷 기반([ADR-0016](adr/0016-incident-replay-snapshots-only-for-real-infra.md))이라 시간축 파생이 불가능합니다. 새 패널은 규칙 기반 세션에서 먼저 완성하고, real-infra에서는 "이 모드에서는 제공되지 않음"을 명시합니다. 예외는 트레이스 — real-infra coupon은 **진짜 Jaeger 스팬**이 있으므로 그쪽이 오히려 먼저 붙을 수 있습니다(O6).
4. **실시간 전송은 계속 폴링이다.** "LIVE ●"는 3초 폴링으로 충분합니다([ADR-0026](adr/0026-game-day-spectating-is-scoped-by-shared-org-membership-and-still-defers-websocket.md) 연장).

### 하지 않는 것

| 하지 않음 | 이유 |
|---|---|
| PromQL/LogQL 등 쿼리 언어 | 학습 목표는 "어느 신호를 볼지"이지 쿼리 문법이 아님. 필터·그룹·오버레이 UI로 충분 |
| Grafana/Prometheus 임베드 | 시뮬레이션 지표는 실제 수집 대상이 아님. 플랫폼 자체 운영 지표(ARCHITECTURE §14)와 혼동 금지 |
| 대시보드 빌더(자유 배치) | 비용 대비 학습 가치 낮음. 고급 모드는 "패널 켜기/끄기"까지 |
| 자동 원인 분석·이상 탐지 표시 | §3-1 위반 |

---

## 4. 기반 작업 — 나머지 모든 슬라이스의 전제

### O0-a. 시뮬레이션 시간축 (Telemetry Timeline)

**문제**: 지금 엔진에는 시계가 없습니다. 인시던트를 켜 두고 5분을 기다려도 값이 같습니다. 그래서 알림의 "for 2m", 에러 버짓 소진, 변경 오버레이, 탐지 지연(Detection Delay) 같은 **시간이 들어간 개념을 하나도 표현할 수 없습니다.** 차트도 클라이언트가 폴링값을 쌓는 방식이라 새로고침·관전자·리플레이마다 모양이 다릅니다.

**설계**: 엔진 시그니처에 경과 시간을 넣고, 시계열은 서버가 샘플링해 돌려줍니다.

```
SystemState(t) = engine.computeState(sessionState(액션 이력 중 t 이전 것까지), elapsed = t − incidentStart)

GET /sessions/{id}/simulation/series?from=&to=&step=10s
  → [{ t, trafficRps, p95LatencyMs, errorRate, … }]     // 저장하지 않고 매번 계산
```

- 도메인 함수는 `elapsed`를 **선택적으로** 씁니다. 1차에는 램프업(인시던트 시작 후 60~120초에 걸쳐 부하가 증가)과 누적형 지표(queueLag, 미처리 레코드 수)만 시간에 반응하게 하고 나머지 도메인 수식은 그대로 둡니다. 이렇게 하면 기존 손계산 단언 테스트([TESTING.md](TESTING.md))가 `elapsed = ∞`(정상 상태) 케이스로 그대로 살아남습니다.
- 인시던트 시작 시각은 이미 타임라인 첫 항목의 `appliedAt`으로 남아 있습니다(`PostmortemService.mttdMttr`). 새 저장 필드가 필요 없습니다.
- 계산 비용: 도메인 함수가 마이크로초 단위라 15분 × 10초 간격 = 90포인트는 요청당 무시할 수준입니다.

**ADR 후보** — 엔진 입력에 시간을 넣는 것은 7개 도메인 함수 전부와 테스트 관행에 번지는, 되돌리기 비싼 결정이고 "상태는 액션으로만 바뀐다"는 지금의 단순함을 버리는 진짜 트레이드오프입니다. 착수하는 순간 ADR을 씁니다(대안: 클라이언트 누적 유지 / 서버에 틱 워커를 두고 저장).

### O0-b. 조사 행위 기록 (Investigation Events)

**문제**: 지금 기록되는 사용자 행동은 완화 액션 3종뿐입니다. 사용자가 로그를 먼저 봤는지, DB 노드를 열어봤는지는 어디에도 남지 않습니다. 그래서 진단 과정을 평가할 수 없고, 형제 문서의 Runbook 대조·On-call handoff·Incident Review 타임라인 댓글도 근거가 없습니다.

**설계**: `applied_actions`와 나란히 `investigation_events(session_id, kind, target, at)`를 둡니다. `kind`는 `OPEN_PANEL`(logs/traces/metrics/alerts), `INSPECT_NODE`(서비스 맵 노드), `QUERY_LOGS`, `OPEN_TRACE` 정도로 작게 시작합니다.

- **평가에 바로 쓰지 않습니다.** 1차 용도는 포스트모템 타임라인에 "무엇을 보고 → 무엇을 했나"를 함께 보여주는 것뿐입니다. 점수화는 신호를 본 뒤(§8 열린 질문 2).
- 별도 테이블로 두는 이유: `AppliedAction`은 상태를 바꾸는 입력이고 리플레이 재계산의 원천입니다. 조사 이벤트는 상태를 바꾸지 않으므로 섞으면 재계산 경로가 오염됩니다.

---

## 5. 슬라이스

가치/비용 순이며, 각 슬라이스는 독립 배포 가능합니다. **O0-a 없이 가능한 것**을 표시했습니다.

| # | 슬라이스 | 선행 | 새 저장소 | 핵심 |
|---|---|---|---|---|
| O1 | Mission Control Bar + Observe 탭 재배치 | 없음 ✦ | 없음 | 즉시 체감 |
| O0-a | 시간축 | 없음 | 없음 | 나머지의 기반 |
| O2 | 서버 시계열 차트 + Change Overlay + 비교 오버레이 | O0-a | 없음 | |
| O3 | Service Map (라이브 토폴로지) + 노드 상세(RED/USE) | 없음 ✦ | 없음 | 캔버스 재사용 |
| O0-b | 조사 행위 기록 | O1 | `investigation_events` | |
| O4 | 서버 생성 로그 + Log Context + 시간 범위 연동 | O0-a | 없음 | |
| O5 | Alert Rule · Alert Center · SLO/에러 버짓 | O0-a | 세션별 규칙(JSONB) | 탐지 지연 |
| O6 | 트레이스 (real-infra 실측 → 규칙 기반 합성) | O4 | 없음 | |
| O7 | Production Readiness 단계 + 관측 품질 판정 | O5, O6 | 세션별 설정(JSONB) | |

### O1 — Mission Control Bar + Observe 탭 ✦

- Drill 작업 화면 상단에 고정 바: `시나리오명 · 상태 · 인시던트 경과 시간 · RPS · P95 · 에러율 · 가용성`. 인시던트 전에는 단계 진행(`StageList`)만.
- 상태 5종 `HEALTHY / DEGRADED / CRITICAL / RECOVERING / RECOVERED`는 **백엔드가 `SystemState`에서 파생**해 응답에 싣습니다(프론트가 임계값을 따로 들고 있으면 3-A 이전처럼 이중 관리가 됨). `RECOVERING`은 "직전 스냅샷보다 나아지는 중", `RECOVERED`는 밴드 정상 복귀.
- `WargameLive`를 `Overview | Metrics | Logs | Changes` 탭으로 재배치. Overview는 Golden Signals 4칸(Latency/Traffic/Errors/Saturation — 기존 지표의 재라벨링)과 최근 변경 목록.
- **점수를 절대 띄우지 않는다** — 형제 문서 Hidden Score 원칙과 일치.

**완료 기준**: 상태 파생 단위 테스트(7개 도메인 × 인시던트 전/중/완화 후), `tsc`/`lint`/`build`, 375px에서 바가 두 줄로 접히는지 실브라우저 확인, 관전자(Game Day) 화면에도 같은 바.

### O2 — 서버 시계열 + Change Overlay

- 클라이언트 40포인트 누적을 `GET …/simulation/series`로 교체. 새로고침·관전자·리플레이가 같은 그래프를 봅니다.
- 차트에 액션 시점 세로선(recharts `ReferenceLine`) — `▲ 액션`, `⚡ 인시던트 시작`. 클릭 시 액션 이름과 전후 값.
- 지표 두 개를 한 차트에 겹치는 비교 모드(예: cache hit ratio × DB read load). 시간 범위 선택(최근 5/15분, 인시던트 전체).

### O3 — Service Map ✦

- 인시던트 중 `Observe`에 **사용자가 그린 캔버스 토폴로지**를 읽기 전용으로 띄우고, 노드 종류(kind)별로 기존 지표를 매핑해 표시합니다.

| 노드 kind | 표시 지표 (기존 `SystemState` 필드) | 방법론 |
|---|---|---|
| gateway / service | trafficRps, p95LatencyMs, errorRate, cpu | RED |
| db | dbReadLoad, dbWriteLoad, connectionPoolUsage | USE |
| cache | cacheHitRatio, cacheLatencyMs | USE |
| queue | queueLag, consumerThroughput | USE |
| (외부 의존성) | externalDependencyLatencyMs | RED |

- 토폴로지가 없는 세션(캔버스 미사용)은 도메인 기본 토폴로지를 보여줍니다 — 도메인별 기본 그래프를 설정값으로 둡니다([ADR-0006](adr/0006-config-as-data.md)).
- **정직하게 밝힐 한계**: 엔진은 노드별 상태가 아니라 시스템 전역 스냅샷 하나를 계산합니다([DRILLS_SIMULATION_VISION.md §2](DRILLS_SIMULATION_VISION.md)). 같은 kind 노드가 둘이면 같은 값을 보여줍니다. 노드별 상태 모델은 이 계획 범위 밖입니다.
- 노드 클릭은 O0-b 이후 `INSPECT_NODE` 이벤트로 기록됩니다.

### O4 — 서버 생성 로그

- 로그를 **시계열에서 결정론적으로 생성**: 지표가 밴드를 넘는 구간마다 해당 컴포넌트의 전형적 로그 템플릿(`Redis timeout`, `DB connection timeout pool=primary active=…`)을 시드 기반 빈도로 뿌립니다. 템플릿은 도메인별 설정 데이터.
- 각 줄에 `service`, `level`, `trace_id`(O6에서 사용), 구조화 필드. 로그 한 줄의 "앞뒤 5줄"과 "이 시각의 지표 보기" 링크.
- 차트 구간 드래그 → 로그 탭이 그 시간 범위로 필터(원본 §12의 `Investigate` 흐름).
- `LogViewer.tsx`의 클라이언트 합성 로직은 제거합니다.

### O5 — Alert · SLO

- **알림 규칙**: 인시던트 전(설계 단계 끝)에 사용자가 `지표 · 조건 · 지속 시간 · 심각도`로 규칙을 최대 N개 작성. 도메인 기본 규칙 세트를 "추천"으로 제공하되 기본 비활성.
- **Alert Center**: 규칙을 시계열에 적용해 발화 시각·지속·현재값 표시, `[조사하기]` → 해당 시간 범위의 Metrics/Logs.
- **탐지 지연**: 포스트모템에 `인시던트 시작 → 첫 알림 발화` 지표를 추가. 현재 MTTD는 "첫 액션"까지라 **탐지와 대응이 섞여 있습니다** — 알림 발화 시각을 따로 보여주면 둘이 분리됩니다(기존 MTTD 정의는 벤치마크 호환을 위해 유지하고 새 지표를 추가).
- **SLO**: 형제 문서 [DRILLS_EXPANSION_PLAN.md](DRILLS_EXPANSION_PLAN.md) M3에서 사용자가 정의한 SLO(없으면 시나리오 `baseRequirements`의 비기능 요구)를 시계열에 대입해 목표 대비 현재, 인시던트 구간 에러 버짓 소진량, burn rate(단일 창) 표시. multi-window burn-rate는 보류.
- 잘못된 규칙의 결과를 보여주되 감점하지 않습니다: "알림이 인시던트 7분 뒤에 발화", "정상 구간에서 3회 발화(과민)".

### O6 — 트레이스

- **1차: real-infra coupon** — 이미 Jaeger로 나가는 실제 스팬(JDBC 포함)을 세션 ID로 조회해 워터폴로 보여줍니다. 합성이 아닌 진짜 데이터가 있는 유일한 경로라 먼저 합니다.
- **2차: 규칙 기반** — `p95LatencyMs`를 컴포넌트 지연(cacheLatencyMs, DB, externalDependencyLatencyMs)으로 분해한 합성 워터폴. 분해 규칙이 도메인 함수 안에 새로 필요하므로 도메인별로 하나씩 추가합니다.
- 로그의 `trace_id` → 트레이스, 스팬 → 해당 노드의 Service Map 상세.

### O7 — Production Readiness + 관측 품질

- 인시던트 직전 체크 단계: `알림 규칙 설정 · SLO 정의 · 구조화 로그 · 트레이싱 활성화`. 각각 실제로 한 일(O5 규칙 존재 여부 등)에서 자동 체크되고, 트레이싱을 켜지 않았으면 인시던트 중 트레이스 탭이 **"No data — 배포 시 트레이싱이 비활성이었습니다"** 로 비어 있습니다. 원본 §25의 "준비하지 않은 결과를 장애에서 체험"을 그대로 구현.
- 관측 품질 판정은 **규칙으로 근거를 댈 수 있는 것만**: 탐지 지연, 과민 발화 수, SLO 정의 여부, 인시던트 중 열어본 패널 종류(O0-b). 결과는 루브릭 Observability(10점) 평가 프롬프트에 "사전 점검"으로 넣습니다 — 규칙 판정을 LLM 앞에 두는 기존 구조(ARCHITECTURE §8.2) 그대로.

---

## 6. 재사용 자산

| 자산 | 쓰임 |
|---|---|
| `RuleBasedSimulationEngine` 도메인 함수 | 시계열·로그·트레이스 전부의 원천 |
| `GET …/simulation/timeline` (액션 이력 재계산) | 시계열 API의 구조적 선례 |
| `system_topologies.graph` + `DiagramCanvas`(@xyflow/react) | Service Map |
| 3-A 로그 심각도 백엔드 계산 | O1 상태 파생, O4 로그 레벨 |
| Jaeger(OTLP) real-infra 스팬 | O6 1차 |
| `PostmortemService.mttdMttr` | O5 탐지 지연 추가 지점 |
| recharts, `Gauge`, `MetricsPanel` | 차트·타일 |

## 7. 보류 항목과 재개 조건

| 항목 | 막는 것 | 재개 조건 |
|---|---|---|
| Business Metrics / "기술 지표는 정상인데 전환율만 하락" | 도메인 함수에 비즈니스 지표가 없고, 그런 장애는 새 메커니즘([ADR-0012](adr/0012-new-incident-domains-get-distinct-mechanisms.md)) | 8번째 도메인으로 기획할 때. 쉬운 절반(쿠폰 발급/분, 결제 성공률 등 기존 수식에서 파생되는 값)은 O1 Overview에 먼저 넣을 수 있음 |
| Cardinality Incident / Observability Cost | 관측 백엔드 자체를 모델링해야 함 | 위와 같음 — 새 도메인 후보 1순위(메커니즘이 기존 7개와 확실히 다름) |
| Synthetic · Infra View · 파티션 skew · Top Query | 멀티리전·파드·파티션·쿼리 단위 모델 없음 | 노드별 상태 모델을 도입할 때 |
| Profiling / Flame Graph | 코드 수준 병목 비모델링 | Build 모드와 결합하는 별도 기획 시 |

## 8. 열린 질문

1. **시간축 1차에서 어느 지표가 시간에 반응해야 하는가.** 램프업과 누적형(lag, 미처리 레코드)만으로 알림·SLO가 의미 있게 보이는지, O0-a 착수 직후 한 도메인(coupon)으로 확인하고 나머지로 넓힙니다.
2. **조사 행위를 점수에 반영할 것인가.** 반영하면 "모든 패널을 한 번씩 클릭"하는 게이밍이 생깁니다. 1차는 포스트모템 표시만, 점수화는 분포를 본 뒤.
3. **알림 규칙 작성을 필수로 할 것인가.** 초급 Drill에서는 기본 규칙 세트를 켜 두고, 고급에서만 직접 작성하게 하는 난이도별 분기를 권합니다.

## 9. 성공 지표

| 지표 | 왜 |
|---|---|
| 인시던트 중 Logs/Service Map 탭 사용률 | 관측 도구가 실제 조사에 쓰이는가 |
| 첫 액션 전 조사 이벤트 수의 중앙값 변화 | "일단 scale-out"에서 "보고 판단"으로 바뀌는가 |
| 알림 규칙을 작성한 세션의 탐지 지연 분포 | 관측 설계가 결과로 이어지는가 |
| 잘못된 첫 액션(부작용 액션) 비율 | 원인 추론이 나아졌는가 |

## 10. 형제 계획안과의 접점

- **Drills** — Mission Control(M·O1 공유), SLO 정의(M3 → O5), Recovery Verification(M5는 O0-a 필요), Counterfactual(M6은 결정론적 시계열 필요), Deploy/Canary(M9는 시간축 필수).
- **Learning** — Lab은 같은 엔진을 세션 없이 호출(L5). 시간축이 있으면 "TTL을 줄이면 몇 초 뒤 DB가 무너지는가" 같은 시간 개념 실험이 가능해짐.
- **Community** — Incident Review 타임라인 댓글(C10)이 O0-b 조사 이벤트를 함께 보여줌.
