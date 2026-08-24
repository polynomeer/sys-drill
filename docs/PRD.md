# SysDrill 제품 요구사항 정의서 (PRD)

> 이 문서는 `docs/archive/`의 원본 기획 문서 10건을 종합해 SysDrill의 공식 제품 방향을 정리한 문서입니다. 원본 간 상충하는 내용은 가장 최근이고 가장 구체적으로 수렴한 방향을 채택했습니다. 세부 근거는 [archive/README.md](archive/README.md) 참고.

## 1. 제품 정의

**SysDrill**은 백엔드 개발자가 핵심 시스템 컴포넌트를 직접 구현하고(Build), 실제 도메인 요구사항을 시스템으로 설계한 뒤(Design), 조건 변경과 트래픽·장애 상황에 워게임처럼 대응하며(Wargame), AI와 규칙 기반 평가 엔진으로 실무 피드백을 받는 반복 훈련 플랫폼입니다.

핵심 차별점은 시스템 설계·구현 학습·장애 대응을 개별 기능으로 나열하지 않고 하나의 연속 학습 루프로 묶는 것입니다. 사용자는 "정답을 맞히는 사람"이 아니라 "조건이 바뀌고 시스템이 흔들려도 판단하고 복구할 수 있는 사람"이 되는 것을 목표로 합니다.

**한 줄 정의**: 직접 만들고, 설계하고, 조건 변화에 적응하고, 시스템을 터뜨리고, 복구하며 배우는 백엔드 실무 훈련 플랫폼.

## 2. 문제 정의: 백엔드 개발자의 실무 경험 공백

CRUD·API·DB 개발 경험은 있지만 대용량 트래픽, 분산 시스템 운영, 장애 대응 경험이 부족한 개발자가 많습니다. 이 공백은 책이나 강의로 메우기 어렵고, 실제 장애는 조직에서 의도적으로 재현할 수 없습니다.

| 경험 공백 | 알고 있는 수준 | 실무에서 필요한 수준 |
|---|---|---|
| Redis | 캐시로 쓰면 빠르다 | hit ratio, invalidation, hot key, stampede, 장애 시 DB 전이 판단 |
| Kafka/Queue | 비동기 메시징에 쓴다 | lag, retry, DLQ, 중복 처리, ordering, backpressure 판단 |
| 대용량 트래픽 | scale-out 하면 된다 | 병목 위치·SLO·rate limit·degradation·비용 동시 고려 |
| MSA | 서비스를 나눈다 | 분리 이유, 데이터 정합성, 장애 전파, 운영 비용 판단 |
| 장애 대응 | 로그를 보고 고친다 | MTTD/MTTR, 임시 완화와 근본 해결 구분, postmortem |

**핵심 가설**: 수백만 사용자 규모의 실무 경험 자체를 복제할 수는 없지만, 고트래픽·분산 시스템 환경에서 필요한 사고방식과 장애 대응 루프는 통제된 시뮬레이션으로 충분히 훈련할 수 있습니다. 학습의 핵심은 절대적인 RPS 수치가 아니라 "부하가 늘 때 어떤 컴포넌트가 먼저 깨지는지, 어떤 신호가 나타나는지, 어떤 조치가 어떤 부작용을 만드는지"를 반복 경험하는 것입니다.

## 3. 브랜드: SysDrill

- **Sys**: System/Systems을 개발자 친화적으로 축약.
- **Drill**: 반복 훈련, 실전 대비 연습, 비상 상황 대응 훈련(fire drill, incident response drill)을 의미.
- SysDrill은 System Design(설계) · System Implementation(구현) · Incident Response(장애 대응) 세 영역을 하나의 훈련 체계로 포괄합니다.
- 서브카피 후보: "System Design · Build · Incident Training", "Design. Build. Break. Recover."

## 4. 타깃 사용자

### 4.1 1차 타깃: 2~7년차 백엔드 개발자
- CRUD·기본 실무 경험은 있지만 대규모 운영 경험이 적음
- Redis/Kafka 등을 써봤지만 운영 실패 모드까지 경험하지 못함
- 상향 이직 또는 시니어 성장 욕구, 시스템 설계 면접 대비 필요
- 회사 코드를 학습 도구에 넣기 어려운 실무 코드 반출 제약이 있음

### 4.2 핵심 JTBD (Jobs-to-be-Done)
- "실무에서 고트래픽을 겪어본 적 없지만, 면접과 다음 직장에서 필요한 운영 사고력을 미리 훈련하고 싶다."
- "Redis, Kafka, Rate Limiter를 단순히 써보는 게 아니라 왜 필요한지와 어디서 깨지는지 알고 싶다."
- "시스템 설계 문제를 풀고 나서 조건이 바뀌었을 때 내 설계가 살아남는지 확인하고 싶다."
- "내가 반복해서 놓치는 운영 리스크를 스스로는 모르기 때문에 AI가 찾아주길 원한다."

### 4.3 확장 타깃
- 취업 준비생/주니어: 실무 감각을 미리 체험하는 트랙
- 기업/팀: 온보딩, 미들급 역량 강화, Game Day, 장애 대응 교육

## 5. 제품 원칙: 팔아야 할 것 vs 팔지 말아야 할 것

| 팔지 말아야 할 것 | 팔아야 할 것 |
|---|---|
| Redis 사용법 | 캐시를 적용할 조건, 실패 모드, 정합성·비용 트레이드오프 판단 |
| Kafka 문법 | 비동기 경계, lag, 재처리, 중복·순서 보장의 운영 판단 |
| MSA 패턴 암기 | 서비스 분리 이유·비용, 장애 격리, 계약·관측 문제 |
| 시스템 설계 모범답안 | 요구사항 변경과 장애 이벤트에 따라 설계를 수정한 경험 |

## 6. 핵심 학습 루프

```
[Build] 핵심 컴포넌트 구현
    ↓
[Design] 실제 도메인 시스템 설계
    ↓
[Tail Design] 요구사항·제약 변경(꼬리설계)
    ↓
[Wargame] 트래픽/장애 이벤트 발생
    ↓
[Response] 관측·완화·복구
    ↓
[Evaluation] 리스크·트레이드오프·대응 평가
    ↓
[Retrospective] 회고·재설계·다음 학습 추천
```

강의보다 시도-피드백 루프를 우선합니다: 설명은 최소한만 제공하고 사용자가 먼저 판단하게 하며, 실패 시 왜 위험했는지를 실제 운영 증상과 연결해 설명합니다.

## 7. 핵심 모드

| 모드 | 입력 | 주요 활동 | 평가 결과 |
|---|---|---|---|
| **Build Mode** | 과제 스펙, 로컬 코드 | stage별 구현·git/CLI 제출·자동 테스트 | 기능 정확성, 성능, edge case, 리스크 피드백 |
| **System Design Mode** | 요구사항, 규모, 제약 | 아키텍처·데이터·캐시·비동기·관측 설계 | 요구사항 적합성, trade-off, 리스크 |
| **Wargame Mode** | 운영 중인 가상 시스템 | 지표/로그 확인, 액션 적용, 복구 | MTTD/완화/복구, SLO, 부작용 |
| **Bridge Mode** | Build+Design 결과 | 설계를 실제 워게임으로 검증 | 구현·설계·운영 통합 평가 |

**Bridge Mode가 핵심 차별화**입니다. Build한 메커니즘을 Design에서 선택하고, Tail Design에서 제약 변화에 적응한 뒤, Wargame에서 그 선택의 운영 결과를 직접 경험합니다. "알고 있다"와 "운영할 수 있다" 사이의 간극을 연결합니다.

예시 흐름: `Build your own Rate Limiter → 선착순 쿠폰 시스템 설계 → 트래픽 6배 상향 꼬리설계 → Redis latency 증가 + 중복 요청 폭주 워게임 → Rate limit/degradation/idempotency 대응 → 리스크 리뷰 → 재도전`

### 7.1 Build Mode — 초기 과제

| 과제 | 핵심 학습 | 연결 시나리오 |
|---|---|---|
| Build your own Rate Limiter | Token/Leaky Bucket, burst, 분산 한계 | 쿠폰 이벤트, API 보호 |
| Build your own Queue | ack/retry, visibility, at-least-once | 알림/이벤트 처리 |
| Build your own Cache | TTL, eviction, invalidation | 상품 조회, hot key |
| Build your own Idempotency Layer | dedupe, key lifecycle | 결제/쿠폰 중복 방지 |
| Build your own Circuit Breaker | failure threshold, half-open | 외부 API 장애 |

CodeCrafters에서 가져올 것은 콘텐츠 목록이 아니라 "작은 stage로 쪼개고 로컬 환경에서 구현 → 자동 검증 → 즉시 피드백"이라는 학습 메커니즘입니다. Build 과제는 반드시 Design/Wargame과 연결되어야 하며, 독립 콘텐츠로 끝나지 않습니다.

### 7.2 System Design Mode — 꼬리설계

한 번의 정답 제출로 끝나지 않고, 서비스가 요구사항·트래픽·비용·정합성·운영 제약을 추가/변경해 사용자가 다시 설계하도록 만듭니다.

**설계 입력 항목**: 기능/비기능 요구사항, 트래픽/데이터 규모 가정, 아키텍처와 컴포넌트 경계, 저장소 선택과 읽기/쓰기 패턴, 캐시 키·TTL·무효화·fallback, 동기/비동기 경계와 재시도·DLQ, 트랜잭션·멱등성·동시성, metrics/logs/traces·alerting, 예상 병목·트레이드오프.

**꼬리설계 유형 예시**:

| 유형 | 예시 | 평가 의도 |
|---|---|---|
| 규모 변경 | 평시 300 RPS → 이벤트 3,000 RPS | capacity 가정과 병목 재평가 |
| 정합성 강화 | 중복 허용 가능 → 중복 절대 불가 | idempotency/locking/transaction 판단 |
| 최신성 완화/강화 | 리뷰는 stale 허용, 가격은 최신 필수 | 데이터별 consistency 분리 |
| 비용 제약 | Redis Cluster 무한 확장 불가 | 기술 만능주의·과설계 방지 |
| 운영 제약 | 야간 대응 인력 최소화 | 자동 복구, runbook, alert 설계 |
| 외부 장애 | 결제/알림 provider p95 악화 | timeout budget, circuit breaker, degradation |

### 7.3 Wargame Mode — 트래픽/장애 이벤트

워게임은 "랜덤하게 서버를 죽이는 기능"이 아니라, 학습 목표가 명확한 시나리오 템플릿에 통제된 랜덤성을 넣는 게임 마스터 구조입니다.

**이벤트 축**: 트래픽(ramp/burst/spike/특정 endpoint 집중), DB(slow query/lock contention/pool 고갈), Redis(latency/hit ratio 급락/hot key/장애), Kafka/Queue(consumer lag/poison message/broker 지연), 외부 의존성(timeout/partial failure/SLA 급락), 컴퓨트/네트워크(pod crash/CPU 압박/packet loss), 운영 제약(예산 상한/alert noise).

**난이도**:

| 난이도 | 구성 |
|---|---|
| Lv1 | 단일 원인 + 명확한 신호 |
| Lv2 | 트래픽 변화 + 장애 1개 |
| Lv3 | 복합 장애 + 일부 노이즈 알람 |
| Lv4 | 잘못된 조치에 따른 연쇄 부작용 |
| Lv5 | 원인 은폐, 부분 복구 후 재발, 비용/운영 제약 동시 적용 |

**평가에서 중요한 것은 액션이 아니라 판단 과정**입니다. 같은 액션도 언제, 무엇을 보고, 왜 선택했는지에 따라 점수가 달라집니다. 무조건적인 scale-out, 무분별한 retry 증가는 오히려 감점될 수 있습니다.

**워게임 정량 지표**: MTTD(이상 감지 시간), Time to First Useful Action, MTTM(완화 시간), MTTR(복구 시간), SLO Violation Duration, Wrong Action Count, Root Cause Accuracy.

## 8. 초기 콘텐츠 — 우선 도메인 6개

| 도메인 | 핵심 학습 | 주요 장애/부하 |
|---|---|---|
| 선착순 쿠폰/재고 | 동시성, 멱등성, rate limit, write hotspot | 중복 요청, Redis latency, DB contention |
| 주문/결제 | transaction boundary, outbox/saga, idempotency | 외부 결제 timeout, retry storm, partial failure |
| 대규모 상품 조회 | cache, stale policy, read scaling | hot key, cache miss 폭증, replica lag |
| 알림/이벤트 처리 | queue, retry/DLQ, provider isolation | consumer lag, poison message, provider outage |
| 예약 시스템 | locking, inventory consistency, timeout | 경합, 중복 예약, lock wait |
| 배치/정산 | chunking, restartability, reconciliation | partial failure, long transaction, 재처리 |

### 8.1 MVP 시나리오 1 — 선착순 쿠폰
초기 조건: 100만 사용자 대상 1만 장 쿠폰, 중복 발급 불가. 꼬리설계: 오픈 직전 예상 트래픽 20배, Redis 확장 예산 제한. 워게임: Redis latency 증가 → DB write hotspot 및 timeout 증가.
평가 포인트: 멱등성 키, 동시성 제어, rate limiting, hot key, degraded mode, p95/error rate/DB lock 관측.

### 8.2 MVP 시나리오 2 — 알림 이벤트 처리
초기 조건: 주문·결제·배송 이벤트를 이메일/푸시/SMS로 전달. 꼬리설계: 주문량 10배 증가, 일부 메시지 중복 불가. 워게임: provider timeout → retry backlog → Kafka consumer lag 증가.
평가 포인트: 비동기 경계, idempotent consumer, retry/backoff, DLQ, provider별 circuit breaker.

### 8.3 MVP 시나리오 3 — 대규모 상품 조회
초기 조건: 상품 상세에 가격·재고·리뷰 노출. 꼬리설계: 트래픽 20배, 가격은 최신성 필수·리뷰는 stale 허용. 워게임: cache miss 폭증 + hot key + DB read latency 증가.
평가 포인트: 데이터별 캐시 정책 분리, key 분산, single-flight/lock, read replica.

## 9. AI 평가 엔진

AI의 역할은 "모범답안 생성"이 아니라 **맹점 발견**입니다. 결정 가능한 사실(요구사항 누락, 임계값 위반 등)은 규칙 엔진이 판정하고, AI는 그 결과를 기반으로 설명·비판·꼬리질문을 생성합니다.

| AI 역할 | 기능 |
|---|---|
| Blind Spot Detector | 답안에서 누락된 설계 영역과 반복 약점 탐지 |
| Mentor Interrogation Engine | "왜?", "실패하면?", "10배면?" 꼬리질문 생성 |
| Risk Reviewer | 실무에서 먼저 발생할 가능성이 높은 장애와 증상 예측 |
| Trade-off Reviewer | 대안과 포기한 것을 명시했는지 평가 |
| Incident Coach | 관측 순서·조치 이유·부작용 평가 |
| Learning Planner | 약점 히스토리 기반 다음 Build/Design/Wargame 추천 |

**피드백 4계층**: (1) 요구사항 정합성, (2) 아키텍처·트레이드오프, (3) 실무 리스크(hot key, retry storm, duplicate processing 등), (4) 운영 판단(감지→완화→복구 순서, 부작용 관리).

**사용자 장기 메모리**: 지식 수준보다 "반복되는 사고 패턴"을 기억합니다. 예: 관측 가능성을 자주 빠뜨리는지, Kafka를 과도하게 도입하는지, scale-out을 만능 대응처럼 쓰는지.

## 10. 평가 루브릭 (100점)

| 항목 | 배점 | 평가 질문 |
|---|---:|---|
| 요구사항 해석력 | 15 | 무엇을 보장하고 무엇을 포기할 수 있는지 정의했는가? |
| 아키텍처 적합성 | 20 | 문제 대비 과도하거나 부족하지 않은 구조인가? |
| 트레이드오프 설명 | 15 | 대안·비용·부작용을 명시했는가? |
| 운영 리스크 인식 | 15 | 병목·장애 전파·실패 모드를 예상했는가? |
| 장애 대응 판단 | 20 | 관측→가설→완화→복구 순서가 적절한가? |
| Observability | 10 | 핵심 metrics/logs/traces와 alert 기준이 있는가? |
| 커뮤니케이션 | 5 | 설명과 의사결정이 추적 가능한가? |

점수는 사용자를 서열화하기보다 재도전 시 변화와 약점 추이를 보여주는 도구입니다.

## 11. MVP 범위

### 포함
- 회원가입/로그인
- 시스템 설계 모드: 위 3개 시나리오 + 꼬리설계 1회 이상
- Build 모드: Rate Limiter, Queue 2개 과제
- Wargame 모드: 각 설계와 연결되는 단일~복합 이벤트
- 비동기 AI 평가 + 규칙 기반 평가 하이브리드
- 결과 리포트(100점 루브릭 + 실무 리스크 + 다음 추천)
- 기본 약점 프로필/점수 추이

### 의도적으로 제외
- 실무 코드 업로드·정적 분석 (→ [FUTURE_EXPLORATIONS.md](FUTURE_EXPLORATIONS.md))
- 자유형 아키텍처 다이어그램 편집기
- 사용자별 실제 클라우드 인프라 자동 생성 (실제 Redis/Kafka/K8s 기반 고급 워게임)
- 실시간 멀티플레이/팀전
- 기업 관리자 기능, 시나리오 마켓플레이스, 인증 제도
- 음성 기반 시스템 설계 면접

### MVP에서 검증할 세 가지
1. 사용자가 "설계 → 조건 변경 → 장애 대응" 흐름을 기존 시스템 설계 학습보다 가치 있다고 느끼는가?
2. AI 피드백이 교과서적이지 않고 "실무에서 실제로 터질 문제"를 알려준다고 평가받는가?
3. 같은/유사 시나리오를 다시 풀고 싶을 만큼 반복 학습 동기가 생기는가?

## 12. 비즈니스 모델

| 플랜 | 대상 | 가치 |
|---|---|---|
| Free | 유입/체험 | 일부 Build stage, 설계 맛보기, 축약 피드백 |
| Individual/Pro | 개인 개발자 | 전체 기본 시나리오, 상세 리포트, 약점 추적 |
| Advanced/Interview | 이직/면접 고의도 사용자 | 고난도·랜덤 시나리오, 면접형 타이머, 심화 분석 |
| Team/Enterprise | 기업 | 팀 대시보드, Game Day, 온보딩, 커스텀 시나리오, SSO |

장기적으로 채용/역량 평가, 시나리오 마켓플레이스(제작자 70% / 플랫폼 30%), 실전형 인증("SysDrill Certified Incident Responder")으로 확장 가능하나 MVP 이후 단계입니다.

## 13. KPI

- **North Star (추천)**: 주간 "의미 있는 실전 훈련 완료 세션" 수 — 설계 제출 → 조건 변화 → 대응 → 리포트 확인까지 핵심 루프를 완주한 세션.
- Activation: 첫 설계 제출 완료율, 첫 Wargame 진입률
- Retention: 1주/4주 재방문, 재도전율
- 학습 성과: 동일 유형 재도전 시 리스크 인식 점수 향상, MTTD/MTTM/MTTR 개선
- Revenue: Free→Paid 전환율, 팀 플랜 전환율

## 14. 핵심 리스크와 대응

| 리스크 | 대응 |
|---|---|
| 장난감처럼 보임 | 점수보다 실무 리스크·incident timeline·SLO·postmortem 중심 구성 |
| LLM 피드백이 뻔함 | 시나리오별 리스크 라이브러리 + 루브릭 + 구조화된 증거 기반 생성 |
| Build/Design/Wargame이 분절됨 | Bridge Mode를 핵심 홈 경로로 배치 |
| 실제 인프라 비용 폭증 | MVP는 규칙 기반 상태 시뮬레이션, 실제 runtime은 고급 단계로 분리 |
| 정답이 여러 개인 설계를 획일 평가 | 단일 정답 대신 요구사항 적합성·trade-off·리스크로 평가 |
| 콘텐츠 제작 비용 | 공통 실패 패턴·이벤트 템플릿·루브릭 재사용 DSL 설계 |

관련 문서: [ARCHITECTURE.md](ARCHITECTURE.md) · [ROADMAP.md](ROADMAP.md) · [FUTURE_EXPLORATIONS.md](FUTURE_EXPLORATIONS.md) · [../PLAN.md](../PLAN.md)
