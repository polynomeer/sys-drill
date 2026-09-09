# SysDrill 로드맵

> 제품 범위는 [PRD.md](PRD.md), 구현 기준은 [ARCHITECTURE.md](ARCHITECTURE.md) 참고. 각 Phase 안의 실제 구현 순서(Claude Code 실행 단위)는 [../PLAN.md](../PLAN.md)에 정리합니다.

## Phase 1 — Core Loop (MVP)

**목표**: "설계 → 꼬리설계 → 장애 대응 → 상세 피드백" 루프가 사용자가 돈을 낼 만큼 가치 있는지 검증한다.

- 범위: 시나리오 3개(선착순 쿠폰/알림 이벤트/대규모 상품 조회), Build 과제 2개(Rate Limiter/Queue), 텍스트 기반 설계 제출, 꼬리설계 1회 이상, 규칙 기반 Wargame 시뮬레이션, 비동기 AI+Rule 평가, 세션 종합 리포트, 기본 약점 프로필.
- 검증할 질문: 실무적이라고 느끼는가? 재도전하는가?

## Phase 2 — Personalization / 콘텐츠 확장

- 개인 약점 추적 고도화(장기 SkillProfile), adaptive 꼬리설계, 시나리오 seed 랜덤화
- Build 과제 확장: Circuit Breaker, Distributed Lock, Retry/Backoff Middleware, Event Bus
- 추가 시나리오: 주문/결제, 예약 시스템, 배치/정산
- 검증할 질문: 개인화가 리텐션을 높이는가?

## Phase 3 — Real Runtime / 실전 시뮬레이션 강화

- 실제 컨테이너 기반 의존성(Postgres/Redis/Kafka) 도입, k6/Locust 부하 생성, Toxiproxy 기반 네트워크 fault injection
- OpenTelemetry 기반 실제 metrics/logs/traces 파이프라인
- Incident Replay, Postmortem 작성 기능
- 고급 Kafka/Redis/DB/Kubernetes 시나리오, 면접형 타이머 모드
- 검증할 질문: 면접/실습/팀 훈련으로 확장 가능한가?

## Phase 4 — Team / B2B

- 조직/팀 관리, 팀 대시보드, 다인 훈련(Game Day)
- Private Scenario(사내 장애 익명화), 커스텀 루브릭, SSO/RBAC/Audit Log
- On-call Readiness, 신규 입사자 온보딩 트랙
- 검증할 질문: B2B 운영 훈련 시장으로 확장 가능한가?

## Phase 5 — Platform

- Scenario Marketplace (외부 제작자 70% / 플랫폼 30% 수익 배분)
- 채용/역량 평가 상품화, 실전형 인증(SysDrill Certified Incident Responder)
- 검증할 질문: 마켓플레이스·인증·채용 평가가 실제 시장에서 통하는가?

## Phase 6 — Architecture Linter (정적 분석/시스템 그래프 확장)

> Phase 5의 검증 질문에 대한 신호를 아직 확인하지 못한 상태에서, 로드맵 운영 원칙(아래)을 잠시 미뤄두고 방향만 미리 스케치해둔 잠정 항목이다. 실제 착수는 원칙대로 Phase 5 검증 이후로 미룬다.

**목표**: 가상 시나리오를 넘어, 사용자의 실제 리포지토리를 훈련 콘텐츠의 원천으로 삼는다. 상세 배경은 [FUTURE_EXPLORATIONS.md §A](FUTURE_EXPLORATIONS.md#a-정적-분석시스템-그래프-확장-architecture-linter) 참고.

- 범위: Repository Import → 정적 분석(코드/OpenAPI/IaC) → System Graph 생성 → 리스크 탐지(SPOF, timeout 역전, retry amplification, API contract drift, Kafka schema drift 등) → 시나리오 자동 생성 → 기존 Simulation/평가 파이프라인 재사용
- 기존 "Rule + AI 하이브리드" 원칙(ARCHITECTURE.md §1)을 그대로 적용 — Rule Engine이 결정론적 리스크를 찾고 LLM은 설명·시나리오 생성을 보조하는 역할에 머문다.
- 착수 전 반드시 좁혀야 할 것: 다중 언어/포맷 파싱 범위(Kotlin/TS/OpenAPI/Terraform 등), 고객 소스코드 반출·보관에 대한 보안·컴플라이언스 설계.
- 검증할 질문: "내 실제 코드베이스로도 해보고 싶다"는 수요가 실제로 있는가? 정적 분석에서 나온 시나리오가 가상 시나리오만큼(또는 그 이상) 훈련 가치가 있는가?

## 다이어그램 입력 고도화 — React Flow 하이브리드 (잠정 스케치)

> Mermaid DSL 기반 아키텍처 다이어그램 시각화(ADR-0035)의 v2 방향만 미리 스케치해둔 잠정 항목이다. v1(텍스트 DSL)이 실제로 쓰이는지 신호를 보기 전에는 착수하지 않는다.

**목표**: Mermaid 문법을 모르는 사용자도 마우스로 다이어그램을 그려 같은 결과를 낼 수 있게 한다.

- 방향: Design Workspace에 "그리기" 모드를 추가 — React Flow 캔버스에서 박스(Client/API/Cache/DB/Queue 등 타입별 노드)를 드래그하고 화살표로 연결하면, 그 결과를 Mermaid 텍스트로 직렬화해 기존 자유 텍스트 답안의 ` ```mermaid ` 블록에 그대로 삽입한다.
- **DSL 텍스트가 계속 유일한 저장/제출 포맷이다** — React Flow는 별도 데이터 모델이나 새 API 필드를 만들지 않는, 순수 "그리면 텍스트가 나오는" 입력 도구로만 존재한다(ADR-0035). 이 전제가 깨지면(예: 노드/엣지를 구조화된 형태로 별도 저장) 새로 설계해야 한다.
- 착수 전 확인할 것: v1 다이어그램 기능이 실제로 쓰이는가(사용자가 자발적으로 mermaid 블록을 답안에 포함하는가), 문법 장벽이 실제 이탈 요인으로 보고되는가.
- 검증할 질문: 그리기 모드가 있으면 다이어그램 사용률이 유의미하게 오르는가, 아니면 대부분 템플릿 삽입 정도로 충분한가?

## 로드맵 운영 원칙

- 각 Phase는 이전 Phase의 핵심 검증 질문에 긍정적인 신호가 있어야 다음으로 진행한다.
- MVP(Phase 1)는 기능 수보다 핵심 루프의 완결성과 AI 피드백 품질에 집중한다.
- Phase 3의 "실제 인프라 기반 시뮬레이션"은 비용이 크므로, Phase 1~2에서 규칙 기반 시뮬레이션으로 충분히 학습 가치를 검증한 뒤에만 투자한다.
