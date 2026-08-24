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
- **정적 분석/시스템 그래프 확장** — 사용자의 실제 리포지토리(코드/OpenAPI/IaC)를 분석해 워게임 시나리오를 자동 생성하는 기능. 상세는 [FUTURE_EXPLORATIONS.md](FUTURE_EXPLORATIONS.md#a-정적-분석시스템-그래프-확장-architecture-linter) 참고 — **현재는 확정 로드맵이 아닌 후보**이며, Phase 1~4에서 확보한 리스크 라이브러리·평가 루브릭이 충분히 축적된 이후 재검토한다.

## 로드맵 운영 원칙

- 각 Phase는 이전 Phase의 핵심 검증 질문에 긍정적인 신호가 있어야 다음으로 진행한다.
- MVP(Phase 1)는 기능 수보다 핵심 루프의 완결성과 AI 피드백 품질에 집중한다.
- Phase 3의 "실제 인프라 기반 시뮬레이션"은 비용이 크므로, Phase 1~2에서 규칙 기반 시뮬레이션으로 충분히 학습 가치를 검증한 뒤에만 투자한다.
