# 원본 기획 문서 아카이브

이 폴더는 SysDrill 초기 기획 과정에서 작성된 원본 문서를 보관합니다. 하루(2026-08-24) 동안 여러 방향 전환을 검토한 브레인스토밍 기록이며, 서로 다른 결론을 내는 문서가 섞여 있습니다.

현재 공식 방향과 요약된 내용은 다음 문서를 참고하세요.

- [../PRD.md](../PRD.md) — 제품 요구사항 정의서
- [../ARCHITECTURE.md](../ARCHITECTURE.md) — 시스템 아키텍처 설계서
- [../ROADMAP.md](../ROADMAP.md) — 단계별 로드맵
- [../FUTURE_EXPLORATIONS.md](../FUTURE_EXPLORATIONS.md) — 채택하지 않은 피봇/확장 아이디어 아카이브

## 원본 목록

| 파일 | 내용 |
|---|---|
| 시스템 디자인 워게임 기획서.md | 최초 컨셉: 시스템 디자인 워게임 서비스 기획안 |
| 시스템 디자인 워게임 아이디어 피봇 검토 문서.md | B2B 피봇 후보 13개 비교 검토 |
| 런타임 오류를 사전에 발견하기 위한 정적 분석·계약 검증·시스템 리스크 분석 설계.md | 정적 분석/시스템 그래프 확장 아이디어 |
| SysDrill_네이밍_정의서.docx | 서비스명을 'SysDrill'로 확정한 네이밍 문서 |
| sysdrill_operational_risk_platform_plan.docx | 운영 리스크/Production Reliability B2B 피봇 제안 |
| sysdrill_similar_services_and_business_model.docx | 유사 서비스 분석 및 수익모델 설계 |
| sysdrill_system_architecture_design.docx | 워게임 아키텍처 설계서 (Kotlin/Spring Boot 기준) |
| backend_wargame_integrated_plan_and_design.docx | AI 멘토링 아이디어 → 백엔드 워게임 플랫폼 통합 기획 |
| backend_wargame_platform_product_plan.docx | 가장 상세한 제품 기획서 (IA, 와이어프레임 포함) |
| 멘토링_서비스_시스템_설계_문서.docx | 실제 구현 기준에 가장 가까운 시스템 설계 기준 문서 |

원본은 `.docx`/`.md` 그대로 두었으며, 필요 시 `pandoc -t markdown` 또는 `unzip`으로 `word/document.xml`을 읽어 원문을 확인할 수 있습니다.
