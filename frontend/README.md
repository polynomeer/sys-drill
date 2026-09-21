# SysDrill Frontend

Next.js 16 (App Router) · React 19 · TypeScript · Tailwind CSS 4. 백엔드 REST API(`http://localhost:8081`)를 호출하는 순수 클라이언트 앱입니다 — 서버 컴포넌트에서 DB에 직접 접근하는 코드는 없습니다.

전체 프로젝트 개요는 [루트 README](../README.md), 개발 규칙은 [docs/DEVELOPMENT.md](../docs/DEVELOPMENT.md)를 보세요.

## 실행

```bash
npm install
npm run dev          # http://localhost:3000 — 백엔드가 8081에 떠 있어야 합니다
```

보통은 저장소 루트의 `./scripts/run.sh`로 Docker 스택·백엔드·프론트엔드를 한 번에 띄웁니다. 프론트엔드만 따로 띄울 때 백엔드 주소가 다르면 `NEXT_PUBLIC_API_BASE_URL`로 지정합니다 (`.env.local` 또는 셸).

```bash
npm run lint         # eslint
npx tsc --noEmit     # 타입 검사
npm run build        # output: "standalone" — Dockerfile이 이 결과만 복사
```

CI는 위 세 가지 + `npm audit --audit-level=high`를 돌립니다. 단위 테스트는 없습니다.

## 구조

```
src/
  app/
    layout.tsx                  Noto Sans KR + Geist Mono, 공통 AppHeader
    page.tsx                    랜딩 (헬스체크)
    onboarding/ login/          가입 · 로그인 · Google OAuth 콜백 · 비밀번호 재설정 · 이메일 인증
    dashboard/                  시나리오 목록, Bridge Mode 진입, 최근 진행
    design/[sessionId]/         ★ 핵심 — 세션 워크스페이스
      page.tsx                    phase에 따라 Design Workspace ↔ Wargame Live 전환, 제출/피드백/타이머
      DiagramCanvas.tsx           React Flow 캔버스. 노드 config → 시뮬레이션 입력 (ADR-0037)
      DiagramPreview.tsx          답안 속 Mermaid 블록 렌더링
      WargameLive.tsx             실시간 지표(3초 폴링) · RPS/에러율 차트 · 로그 · 대응 액션 · 채팅
      LogViewer.tsx
      replay/                     인시던트 타임라인 스크러빙
      postmortem/                 MTTD/MTTR 자동 계산 + 직접 작성
    report/[sessionId]/         세션 리포트 (단계별 점수 · 실무 리스크)
    bridge/                     Build Mode — CodeMirror 에디터, stage별 채점 결과
    learning/ profile/ certifications/ marketplace/ community/
    organizations/[orgId]/      팀 대시보드 · 커리큘럼 · 평가(assessment) · 감사 로그
    architecture-analysis/      OpenAPI 스펙 업로드 → 아키텍처 린터
    admin/                      프롬프트 템플릿 관리 (PLATFORM_ADMIN)
  components/
    ui/                         Button · Card · Badge · Input · Alert · Gauge · EmptyState · LoadingState
    AppHeader · BridgeProgress · FeedbackDetail · MermaidDiagram · PhaseTimer
  lib/
    api.ts                      ★ 백엔드 호출 단일 진입점 (함수 58개). 토큰 자동 첨부, ApiError
    localSession.ts             localStorage 키 (sysdrill:token, sysdrill:draft:*, sysdrill:canvas:* …)
    designGuidance.ts           도메인별 "답안에 포함하면 좋은 항목" 텍스트
    metrics.ts                  utilization → 색상/상태, 포맷터
    riskLabels.ts · skillCategoryLabels.ts
```

30개 페이지 중 26개가 `"use client"`입니다. 인증 상태가 `localStorage`의 JWT에 있고 대부분의 화면이 폴링·폼·캔버스 같은 상호작용 위주라서, 서버 컴포넌트의 이점이 거의 없다고 판단했습니다.

## 알아둘 것

**API 호출은 `lib/api.ts`만 거칩니다.** `apiFetch<T>()`가 `sysdrill:token`을 `Authorization: Bearer`로 붙이고, 실패 시 `ApiError(status, message)`를 던집니다. 컴포넌트에서 `fetch`를 직접 부르지 마세요.

**초안은 브라우저에, 확정본은 서버에.** 답안 텍스트(`sysdrill:draft:<sessionId>`)와 캔버스 그래프(`sysdrill:canvas:<sessionId>`)는 타이핑할 때마다 `localStorage`에 저장되고, 제출 시에만 서버로 갑니다. 단, 캔버스 토폴로지는 예외로 변경 즉시 `PUT /sessions/{id}/topology`로도 저장됩니다 — 시뮬레이션 엔진이 인시던트 시작 시 읽어야 하기 때문입니다. 서버에 저장된 그래프가 있으면 로컬 초안보다 우선합니다.

**캔버스는 Mermaid 텍스트로도 직렬화됩니다.** `DiagramCanvas`는 그래프가 바뀔 때마다 `flowchart TD` Mermaid 블록을 만들어 답안 텍스트의 `<!-- sysdrill-canvas:start/end -->` 마커 사이에 끼워 넣습니다. LLM 평가는 이 텍스트를 읽고, 시뮬레이션 엔진은 `SystemTopology`의 구조화된 노드 config를 읽습니다 ([ADR-0035](../docs/adr/0035-diagrams-are-mermaid-text-embedded-in-the-existing-answer-not-a-new-field-or-editor.md) → [ADR-0036](../docs/adr/0036-diagram-canvas-is-an-input-method-that-still-serializes-to-mermaid-text.md) → [ADR-0037](../docs/adr/0037-architecture-canvas-becomes-the-simulation-topology-source-of-truth.md)).

**`NODE_TRAIT_CONFIG`는 백엔드와 미러링됩니다.** `DiagramCanvas.tsx`의 도메인별 노드 config 스키마(예: coupon의 `cache.cacheTtlSeconds`, `db.dbPoolSize`)는 백엔드 `SystemTopologyService.TOPOLOGY_FIELDS`와 키 단위로 같아야 합니다. 한쪽만 바꾸면 값이 조용히 무시됩니다.

**Wargame Live의 차트는 클라이언트 누적입니다.** 3초마다 `GET .../simulation/state`를 폴링해 rolling history를 쌓고 Recharts로 그립니다. 페이지를 새로 열면 차트는 비어 있고, 로그 뷰어만 `GET .../simulation/timeline`으로 한 번 시딩됩니다. 과거 상태를 되짚어 보는 화면은 `replay/`입니다.

**Sentry는 DSN이 있을 때만 켜집니다.** `NEXT_PUBLIC_SENTRY_DSN`(클라이언트) / `SENTRY_DSN`(서버)이 비어 있으면 SDK가 no-op입니다. 소스맵 업로드 플러그인은 연결하지 않았습니다 (auth token 필요).

## 주요 의존성

| 패키지 | 용도 |
|---|---|
| `@xyflow/react` | Architecture Canvas |
| `mermaid` | 답안 속 다이어그램 렌더링 |
| `@uiw/react-codemirror` + `@codemirror/lang-python` | Build Mode 에디터 |
| `recharts` | 워게임 실시간 차트, 프로필 레이더 |
| `@sentry/nextjs` | 에러 리포팅 (선택) |
