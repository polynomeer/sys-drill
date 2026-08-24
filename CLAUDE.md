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
