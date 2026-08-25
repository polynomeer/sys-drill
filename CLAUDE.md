# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

SysDrill은 백엔드 개발자가 핵심 컴포넌트를 직접 구현하고(Build), 시스템을 설계하고(Design), 트래픽·장애 상황에 워게임처럼 대응하며(Wargame) 반복 훈련하는 플랫폼입니다.

핵심 문서:

- [docs/PRD.md](docs/PRD.md) — 제품 요구사항 정의서 (제품 정의, 타깃, 4개 모드, MVP 범위, 비즈니스 모델)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 시스템 아키텍처 설계서 (기술 스택, 도메인 모델, 시뮬레이션/평가 파이프라인)
- [docs/ROADMAP.md](docs/ROADMAP.md) — Phase 1~5 단계별 로드맵
- [PLAN.md](PLAN.md) — **Claude Code가 순서대로 실행할 작업계획서 (Phase 1 MVP)**. 코드 작업은 이 문서의 단계를 따른다.
- [docs/FUTURE_EXPLORATIONS.md](docs/FUTURE_EXPLORATIONS.md) — 검토했으나 채택하지 않은 피봇/확장 아이디어 아카이브
- [docs/archive/](docs/archive/) — 초기 브레인스토밍 원본 문서 (참고용, 서로 상충하는 내용 포함)
- [docs/adr/](docs/adr/README.md) — 아키텍처 결정 기록 (ADR). "왜 이렇게 했는가"가 필요한 결정들의 기록 — 아래 "아키텍처 결정 기록" 절 참고

코드/설계 작업 전에는 PRD.md와 ARCHITECTURE.md를 먼저 확인하고, 실제 구현 순서는 PLAN.md를 따른다.

## 커밋 규칙

이 저장소는 [Conventional Commits](https://www.conventionalcommits.org/) 규칙을 따릅니다.

형식: `<type>(<scope>): <subject>`

| type | 의미 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 (기획서, README, CLAUDE.md 등) |
| `style` | 포맷팅 등 동작에 영향 없는 변경 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `perf` | 성능 개선 |
| `test` | 테스트 추가/수정 |
| `build` | 빌드 시스템, 의존성 변경 |
| `ci` | CI 설정 변경 |
| `chore` | 그 외 잡무성 변경 |

세부 규칙:
- subject는 한국어 또는 영어로, 명령형/현재형으로 간결하게 작성 (예: "기획서 초안 추가")
- 제목은 50자 내외, 필요 시 본문에 상세 설명 추가
- scope는 선택 사항 (예: `docs(design)`, `feat(simulator)`)
- 하나의 커밋은 하나의 논리적 작업 단위만 포함 (여러 목적이 섞인 변경은 커밋을 분리)

## 작업 방식

- **작업 단위별로 커밋한다.** 하나의 요청이라도 논리적으로 구분되는 변경이 여러 개면 각각 별도 커밋으로 나눈다.
- 커밋 전 `git status` / `git diff`로 변경 내용을 확인하고, 관련 없는 파일(`.DS_Store` 등)은 함께 커밋하지 않는다.
- 커밋은 사용자가 명시적으로 요청했을 때만 생성한다 (일반 작업 원칙과 동일).

## 아키텍처 결정 기록 (ADR)

**Claude Code는 이 저장소에서 작업하면서 ADR 작성 여부를 매번 스스로 판단하고, 필요하면 사용자에게 묻지 않고 바로 작성한다.** 이 지침 자체가 그 승인이다.

- 위치: `docs/adr/000N-slug.md`, 번호는 `docs/adr/`에서 가장 큰 번호 다음. 새로 쓸 때마다 `docs/adr/README.md`의 표에도 한 줄 추가한다.
- 형식/판단 기준: Claude Code의 `mattpocock-skills:domain-modeling` 스킬이 정의하는 ADR 포맷을 따른다 — 제목 + 1~3문장(맥락/결정/이유)이 기본형이고, 아래 세 조건이 **모두** 참일 때만 쓴다:
  1. 되돌리기 비용이 실제로 크다 (hard to reverse)
  2. 맥락 없이 보면 놀랍다 — 나중에 읽는 사람이 "왜 이렇게 했지?"라고 물을 만하다 (surprising without context)
  3. 진짜 대안이 있었고 그중 하나를 의도적으로 골랐다 (a real trade-off)
  하나라도 아니면 ADR을 만들지 않는다 — 되돌리기 쉬우면 그냥 되돌리면 되고, 놀랍지 않으면 아무도 궁금해하지 않고, 대안이 없었으면 "당연한 걸 했다"는 것 말고 기록할 게 없다.
- 언제 판단하나: 매 요청이 끝날 때마다 점검하는 게 아니라, 위 세 조건에 들어맞는 결정을 **내리는 바로 그 순간** 알아챈 쪽에서 바로 작성한다 — 별도 단계나 커밋으로 미루지 않는다.
- PLAN.md의 "진행 중 발견한 결정 사항"과는 역할이 다르다: PLAN.md는 각 단계의 전체 작업 기록(그 단계에서 뭘 했는지, 사소한 함정까지 포함)이고, ADR은 그중에서도 위 세 기준을 통과하는 결정만 골라 독립된 문서로 남긴 것이다. 같은 결정이 PLAN.md와 ADR 양쪽에 있어도 된다 — PLAN.md는 그 단계의 맥락 속에서, ADR은 재사용 가능한 독립 기록으로.
