# SysDrill 작업계획서 (Claude Code 실행 순서)

> 제품 정의: [docs/PRD.md](docs/PRD.md) · 구현 기준: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · 단계별 로드맵: [docs/ROADMAP.md](docs/ROADMAP.md)

이 문서는 Claude Code가 SysDrill MVP([ROADMAP.md](docs/ROADMAP.md) Phase 1 — Core Loop)를 구현할 때 따를 순서를 정의합니다. **각 단계는 이전 단계가 동작하는 상태로 완료된 후 [CLAUDE.md](CLAUDE.md)의 커밋 규칙에 따라 커밋하고 다음 단계로 진행합니다.** 한 단계 안에서도 논리적으로 나뉘면 여러 커밋으로 분리합니다.

단계 순서를 바꾸거나 건너뛰어야 할 이유가 생기면(예: 기술적 제약 발견) 이 문서를 먼저 갱신한 뒤 진행합니다.

---

## 0단계 — 프로젝트 스캐폴딩

- [ ] 모노레포 구조 결정: `backend/`(Kotlin+Spring Boot), `frontend/`(Next.js)
- [ ] `backend/`: Gradle 기반 Spring Boot 프로젝트 초기화. 모듈 패키지 경계를 [ARCHITECTURE.md](docs/ARCHITECTURE.md) §2 기준으로 미리 만든다 (`identity`, `content`, `scenario`, `session`, `submission`, `evaluation`, `simulation`, `reporting`).
- [ ] `frontend/`: Next.js 프로젝트 초기화 (TypeScript, Tailwind CSS)
- [ ] 로컬 개발 인프라: `docker-compose.yml`로 PostgreSQL + Redis 기동
- [ ] 루트 `.gitignore`에 Gradle(`build/`, `.gradle/`)과 Node(`node_modules/`, `.next/`) 항목 보강
- [ ] 두 프로젝트 모두 헬스체크 엔드포인트/페이지로 기동 확인

**완료 기준**: `docker-compose up`으로 DB/Redis가 뜨고, backend가 DB에 연결되며, frontend가 backend 헬스체크를 호출해 표시한다.

## 1단계 — 도메인 스키마 및 마이그레이션

- [ ] Flyway(권장) 또는 Liquibase 도입
- [ ] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §4.1 테이블 중 MVP 최소 집합부터 마이그레이션 작성: `users`, `content_items`, `scenarios`, `scenario_versions`, `scenario_steps`, `sessions`, `session_phases`, `submissions`, `evaluations`, `evaluation_risk_flags`, `reports`
- [ ] JPA 엔티티/리포지토리 또는 QueryDSL 설정

**완료 기준**: 마이그레이션이 깨끗한 DB에 적용되고, 각 엔티티에 대한 기본 CRUD 리포지토리 테스트가 통과한다.

## 2단계 — Scenario 콘텐츠 시딩 + 세션 상태 머신

- [ ] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §5 세션 상태 머신 구현 (`IN_PROGRESS → SUBMITTED → EVALUATING → FEEDBACK_READY/EVALUATION_FAILED → COMPLETED`), 상태 조건부 UPDATE로 동시성 제어
- [ ] [PRD.md](docs/PRD.md) §8.1 "선착순 쿠폰" 시나리오 1개를 Admin 시딩 데이터로 등록 (Scenario/ScenarioVersion/ScenarioStep)
- [ ] API: `POST /sessions`, `GET /sessions/{id}`, `POST /sessions/{id}/submissions`(평가 큐잉 없이 저장만), `POST /sessions/{id}/advance`

**완료 기준**: 시나리오 1개로 세션을 시작하고, 텍스트 답안을 제출하고, 상태가 올바르게 전이되는 것을 API 테스트로 확인한다. (이 단계에서는 AI 평가 없이 상태 전이만 검증)

## 3단계 — 비동기 평가 파이프라인 골격

- [ ] Redis 기반 Job Queue 연동, Evaluation Worker 프로세스/모듈 분리
- [ ] `POST /sessions/{id}/submissions` → Submission 저장 + `evaluation_requested` 이벤트 발행 (Outbox 또는 트랜잭션 후 발행)
- [ ] Worker: `submission_id` 기반 idempotency 확인 → (이 단계에서는 Rule Evaluator 스텁만) → Evaluation 저장 → 세션 상태를 `FEEDBACK_READY`로 전이
- [ ] 실패 재시도 + 한도 초과 시 `EVALUATION_FAILED` 처리

**완료 기준**: 제출 후 Worker가 비동기로 평가(스텁 결과)를 만들어 저장하고, 클라이언트는 Polling으로 완료를 확인할 수 있다.

## 4단계 — Rule 기반 Simulation Engine v1

- [ ] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §6 `SystemState`/`DesignTraits` 모델 구현
- [ ] "선착순 쿠폰" 시나리오의 워게임 이벤트 템플릿 1개 구현 (트래픽 20배 + Redis latency 증가 → DB write hotspot)
- [ ] 사용자 액션(`applied_actions`) 처리 및 §6.1 인과/부작용 규칙 최소 3개 구현 (Rate Limit 강화, Cache TTL 조정, DB pool 증가)
- [ ] `utilization` 기반 병목 계산 로직 ([ARCHITECTURE.md](docs/ARCHITECTURE.md) §6의 0~60/60~80/80~95/95+/100+ 구간)

**완료 기준**: 시나리오를 시작하고 시간에 따라 SystemState가 악화되다가, 올바른 액션을 적용하면 지표가 회복되는 흐름을 통합 테스트로 재현한다.

## 5단계 — Rule Evaluator + AI 평가 연동

- [ ] Rule Evaluator: 요구사항 누락, scenario-specific invariant 체크 구현 (LLM 없이 판정 가능한 것부터)
- [ ] PromptTemplate 저장/버전 관리 테이블 및 관리 API
- [ ] LLM Provider 연동 (Context Assemble → LLM Critique → Follow-up Generation), 구조화 JSON(`top_risks`, `missed_points`, `followup_questions` 등) 스키마 검증
- [ ] [PRD.md](docs/PRD.md) §10 평가 루브릭(100점) 반영

**완료 기준**: "선착순 쿠폰" 시나리오 제출에 대해 Rule+AI 하이브리드 평가 결과가 구조화된 형태로 저장되고 조회 API로 확인 가능하다.

## 6단계 — 프론트엔드: System Design Workspace

- [ ] 온보딩(연차/스택/목표) → Dashboard → 시나리오 목록 → Design Workspace 화면 구현
- [ ] Design Workspace: 요구사항 표시, 구조화 섹션 입력, 자동저장, 제출
- [ ] 제출 후 평가 대기 상태(Polling) UI

**완료 기준**: 브라우저에서 시나리오를 선택해 설계를 제출하고 평가 결과를 볼 수 있다.

## 7단계 — 프론트엔드: 꼬리설계 + Wargame Live 콘솔

- [ ] 꼬리설계 카드 UI (조건 변경 표시 + 재설계 입력)
- [ ] Wargame Live: MetricsPanel, EventStreamPanel, ActionPanel, TimelinePanel ([PRD.md](docs/PRD.md) §7.3, 원본 와이어프레임 참고)
- [ ] 액션 제출 → Simulation Engine 결과 반영 → 실시간(Polling 또는 SSE) 갱신

**완료 기준**: 사용자가 꼬리설계를 거쳐 워게임에 진입하고, 액션을 취하며 지표 변화를 관찰할 수 있다.

## 8단계 — 결과 리포트 + 대시보드

- [ ] Report 생성 (Synthesis 단계): 점수 분해, 강점/약점, 실무 리스크 Top N, 다음 추천
- [ ] SkillProfile 갱신 로직 (반복 약점 태깅)
- [ ] Dashboard: 최근 진행, 약점 TOP 3, 점수 추이, 추천 다음 세션

**완료 기준**: 세션 완료 후 리포트 페이지에서 결과를 확인하고, 대시보드에 약점/추천이 반영된다.

## 9단계 — Build Mode (Rate Limiter)

- [ ] Build Runner: Docker 기반 격리 워커, CPU/메모리/timeout 제한, outbound network 차단
- [ ] Rate Limiter 챌린지 stage 1~6 정의 ([PRD.md](docs/PRD.md) §7.1, 원본 문서 stage 표 참고)
- [ ] 제출(Job Queue 적재) → Sandbox 실행/테스트 → 결과 저장 → 피드백

**완료 기준**: 로컬 템플릿 repo에서 Rate Limiter를 구현해 CLI/git으로 제출하면 stage별 테스트 결과와 리스크 피드백을 받는다.

## 10단계 — Bridge Mode 연결

- [ ] Build(Rate Limiter) 완료 → 선착순 쿠폰 Design → 꼬리설계 → Wargame으로 이어지는 단일 흐름 구현
- [ ] Bridge 진행률 UI, 통합 평가(구현+설계+운영) 리포트

**완료 기준**: 하나의 연속된 세션에서 Build→Design→Wargame 전체 루프를 완주할 수 있다.

## 11단계 — MVP 콘텐츠 완성 및 통합 테스트

- [ ] 나머지 2개 시나리오(알림 이벤트 처리, 대규모 상품 조회) 콘텐츠화 ([PRD.md](docs/PRD.md) §8.2, §8.3)
- [ ] Queue Build 과제 추가
- [ ] E2E 테스트: 회원가입 → 시나리오 3종 각각 Design→꼬리설계→Wargame→Report 완주
- [ ] [PRD.md](docs/PRD.md) §11 "MVP에서 검증할 세 가지" 기준으로 셀프 점검

**완료 기준**: MVP 범위([PRD.md](docs/PRD.md) §11 포함 항목) 전체가 시나리오 3종 기준으로 동작한다.

---

## 진행 방식 메모

- 각 단계 시작 전 해당 단계의 "완료 기준"을 재확인하고, 애매하면 [PRD.md](docs/PRD.md)/[ARCHITECTURE.md](docs/ARCHITECTURE.md)를 먼저 참고한다. 그래도 결정할 수 없는 제품 방향 질문이면 사용자에게 확인한다.
- 이 단계 구분은 Phase 1(MVP) 한정이다. Phase 2 이후는 [docs/ROADMAP.md](docs/ROADMAP.md)를 참고하고, 그 시점에 이 문서를 이어서 갱신한다.
- 테스트: 각 단계마다 최소한의 자동 테스트(단위 또는 통합)를 함께 작성한다. 프론트엔드 단계는 가능하면 브라우저로 직접 동작을 확인한다.
