# SysDrill 장기 확장 후보 (미확정)

> 이 문서는 초기 기획 과정(`docs/archive/`)에서 검토했지만 현재 공식 로드맵([ROADMAP.md](ROADMAP.md))에는 포함하지 않은 아이디어를 보존합니다. Build/Design/Wargame/Bridge 워게임 학습 플랫폼을 1차 목표로 확정한 뒤에도 참고 가치가 있어 요약해둡니다. 여기 있는 내용은 **실행 계획이 아니라 재검토 후보**입니다.

## A. 정적 분석/시스템 그래프 확장 (Architecture Linter)

출처: `archive/런타임 오류를 사전에 발견하기 위한 정적 분석·계약 검증·시스템 리스크 분석 설계.md`

**아이디어**: 사용자가 만든 가상 시나리오뿐 아니라, 실제 Git 리포지토리(코드/OpenAPI/SQL/application.yml/Docker/Kubernetes/Terraform)를 분석해 System Graph를 만들고, 여기서 발견한 리스크(SPOF, timeout 역전, retry amplification, API contract drift, Kafka schema drift 등)를 기반으로 워게임 시나리오를 **자동 생성**하는 기능.

```
Repository Import → Static Analysis → System Model 생성 → Risk Detection
→ Scenario Generation → Simulation → User 대응 → 결과 분석
```

제품을 크게 세 영역(Analyzer / Simulator / Drill)으로 재구성하는 구상이었습니다. LLM은 판정의 단일 소스가 아니라 Rule Engine이 찾은 결정론적 사실 위에서 설명·시나리오 생성을 담당하는 구조(§21 원본 문서)는 [ARCHITECTURE.md](ARCHITECTURE.md) §1의 "Rule + AI 하이브리드" 원칙과 일치하므로, 채택 시 기존 평가 파이프라인과 자연스럽게 연결될 수 있습니다.

**왜 지금 채택하지 않는가**: MVP 단계에서 실제 리포지토리 파싱(Kotlin/TS/OpenAPI/Terraform 등 다중 언어·포맷 지원)까지 구현하면 범위가 지나치게 커집니다. 또한 보안(고객 소스코드 반출) 문제가 추가로 발생합니다.

**재검토 시점**: Phase 5 이후, 사용자가 이미 SysDrill의 시나리오/리스크 라이브러리에 익숙해지고 "내 실제 코드베이스로도 해보고 싶다"는 수요가 확인된 시점.

## B. B2B 피봇 후보 13개 비교

출처: `archive/시스템 디자인 워게임 아이디어 피봇 검토 문서.md`

교육 서비스보다 기업 구매 명분이 강한 B2B 아이템으로 완전히 피봇하는 방향을 검토했던 문서입니다. 상위 5개만 요약합니다.

| 아이디어 | 한 줄 정의 | 총평 |
|---|---|---|
| API Monetization Intelligence | endpoint별 매출·비용·마진·고객 의존도를 분석해 요금제/제품 전략을 최적화 | 매출과 직접 연결되어 세일즈가 쉬움 (원본 문서 1위 추천) |
| Release Risk Copilot | GitHub PR에 붙는 AI SRE Reviewer, 배포 전 운영 장애 가능성 리뷰 | 기존 워게임 아이디어와 가장 자연스럽게 연결 |
| Engineering Due Diligence AI | 투자·인수 전 코드/인프라/보안/조직 의존도를 분석해 기술 리스크 점수화 | 고단가 리포트형 사업 가능 |
| B2B Integration Risk Radar | 고객사별 API/Webhook/SFTP 연동 상태로 매출·장애 리스크 사전 감지 | 엔터프라이즈 SaaS에 적합 |
| Architecture Drift Detector | 문서와 실제 코드/인프라/API의 불일치를 자동 탐지 | 보편적 문제, 확장성 좋음 |

나머지 8개(Production Data Flow Mapper, Engineering Decision Memory, Codebase Onboarding Simulator, Engineering Interview Workbench, Technical Debt Marketplace, Platform Team ROI Meter, AI PM for Backend Teams, Dependency Exit Planner)는 원본 문서에 상세 설명이 있습니다.

**왜 지금 채택하지 않는가**: SysDrill이라는 이름과 워게임 UX, 그리고 이후 작성된 모든 상세 기획 문서(통합 기획, 상세 제품 기획, 시스템 설계 기준)가 교육/훈련 플랫폼 방향으로 수렴했습니다. B2B 피봇은 완전히 다른 제품을 처음부터 만드는 것에 가까워 현재 축적된 설계 자산과 연결되지 않습니다.

**재검토 시점**: Phase 4(B2B) 진입 시, "훈련"보다 강한 구매 명분이 필요하다고 판단되면 Release Risk Copilot 방향을 SysDrill Enterprise의 부가 기능으로 검토할 수 있습니다.

## C. Operational Risk / Production Reliability 피봇 제안

출처: `archive/sysdrill_operational_risk_platform_plan.docx`

SysDrill을 교육용 워게임이 아니라 **기업의 실제 배포를 사전 검증하는 엔터프라이즈 플랫폼**("Production Reliability Validation Platform")으로 전환하자는 제안. IaC+코드+계약/스키마+배포 버전+트래픽 정보를 통합해 Failure Graph를 만들고, Change Risk Engine으로 배포 전 위험을 탐지하며, 장애 대응 훈련은 "검증 수단"으로 재정의합니다.

핵심 문구: "배포하기 전에, 당신의 시스템이 어떻게 실패할지 확인하십시오."

**왜 지금 채택하지 않는가**: 이 제안은 SysDrill의 정체성을 "훈련"에서 "Production Reliability"로 완전히 이동시키는 것을 전제로 합니다. 그러나 이후 작성된 문서(네이밍 정의서, 통합 기획, 상세 제품 기획, 시스템 설계 기준)는 모두 "SysDrill = 훈련 플랫폼" 정체성을 유지한 채 세부 사양을 구체화했습니다. 즉 이 문서의 제안은 채택되지 않고 다른 방향으로 수렴했습니다.

**재검토 시점**: 위 §A(정적 분석 확장)가 Phase 5에서 실제로 구현되고 기업 고객이 "우리 실제 시스템도 분석해달라"는 요구를 반복적으로 낼 경우, 이 문서의 Failure Graph / Change Risk Engine 설계를 참고해 Enterprise 전용 트랙으로 확장할 수 있습니다. 단, 이 경우에도 B2C 훈련 플랫폼 정체성을 대체하는 것이 아니라 별도 상위 트랙으로 추가하는 형태를 권장합니다.

---

이 세 방향(A/B/C)은 서로 겹치는 부분이 있습니다: A(정적 분석)와 C(운영 리스크 플랫폼)는 기술적으로 유사하고, B의 "Release Risk Copilot"은 A·C와 거의 동일한 문제의식을 공유합니다. 향후 재검토 시 세 문서를 함께 참고하는 것을 권장합니다.
