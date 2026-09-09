가능합니다. 지금 SysDrill의 `Drills`가 **“문제를 고르고 → 풀고 → AI 피드백을 받는다”** 정도라면 아직은 학습 플랫폼에 가깝습니다.

SysDrill이 Build Your Own X나 CodeCrafters보다 한 단계 위에 있으려면 Drill 자체를 **하나의 살아있는 시스템을 처음부터 만들고, 규모를 키우고, 깨뜨리고, 복구하고, 다시 설계하는 장기 시뮬레이션**으로 만드는 편이 좋습니다.

핵심 구조를 다음처럼 바꾸는 것을 권합니다.

> **Learn → Design → Build → Test → Load → Observe → Break → Diagnose → Recover → Improve → Explain**

즉, “구현할 수 있는가?”에서 끝나지 않고 **“운영 가능한 시스템을 만들고 책임질 수 있는가?”**까지 평가하는 것입니다.

------

# 1. Drill 자체의 개념부터 확장

현재의

```text
Drill
├─ System Design
├─ Build
└─ Incident
```

구조보다는 각각을 별개 문제 유형으로만 취급하지 않는 것이 좋습니다.

하나의 Drill을 다음과 같은 **System Mission**으로 정의합니다.

```text
Drill: URL Shortener

Mission 1
Requirements
↓
Design

Mission 2
Build Core
↓
Tests

Mission 3
Productionize
↓
Load Test

Mission 4
Scale
↓
Architecture Evolution

Mission 5
Incident
↓
Diagnosis
↓
Mitigation

Mission 6
Postmortem
↓
Redesign

Mission 7
Expert Review
```

예를 들어 사용자가 `대규모 상품 조회 시스템` Drill을 시작했다고 하겠습니다.

처음부터 Redis/Kafka/CDN을 선택하게 하지 않습니다.

```text
요구사항

상품: 1억 개
DAU: 100만
Peak RPS: 3,000
P99 < 300ms
상품 가격 변경 가능
재고 실시간 변경
```

정도만 줍니다.

사용자가 설계합니다.

그러면 시스템이 다음 상황을 추가합니다.

> 서비스가 성장했습니다.
>
> DAU 1,000만
> Peak RPS 30,000
> 특정 상품에 전체 트래픽의 15% 집중

사용자는 기존 설계를 수정해야 합니다.

그리고 실제 부하를 발생시킵니다.

```text
RPS       28,342
P50       43ms
P95       312ms
P99       1.8s
Error     3.2%
DB CPU    94%
Cache Hit 71%
```

그 다음 장애가 발생합니다.

```text
INCIDENT #01

Redis node-3 unavailable

14:03 Cache hit rate ↓
14:04 DB CPU 98%
14:05 P99 4.2s
14:06 Error rate 14%
```

이제 사용자가 대응합니다.

이 전체가 **하나의 Drill**이어야 합니다.

------

# 2. Drill Workspace를 제품의 중심으로

Drill에 들어가면 일반적인 문제 페이지가 아니라 **개발/운영 Workbench**가 나오는 것이 좋습니다.

```text
┌──────────────────────────────────────────────────────────────┐
│ Product Search                              ● RUNNING  01:32 │
├───────────┬───────────────────────────────┬──────────────────┤
│           │                               │                  │
│ Mission   │         Workspace             │    Context       │
│           │                               │                  │
│ ✓ Req     │  Architecture / Code /        │ Requirements     │
│ ✓ Design  │  Metrics / Logs / Traces      │ Constraints      │
│ ● Scale   │                               │ Events           │
│ ○ Chaos   │                               │ Objectives       │
│ ○ Review  │                               │                  │
│           │                               │                  │
├───────────┴───────────────────────────────┴──────────────────┤
│ Timeline                                                     │
│ 12:30 deploy → 12:34 traffic spike → 12:35 DB saturation     │
└──────────────────────────────────────────────────────────────┘
```

중요한 점은 **Mission이 바뀌어도 화면을 떠나지 않는 것**입니다.

사용자는 같은 시스템을 계속 운영하고 있다는 느낌을 받아야 합니다.

------

# 3. Workspace에는 7개의 핵심 도구가 필요합니다

상단 탭을 다음 정도로 구성할 수 있습니다.

```text
Architecture
Code
Traffic
Metrics
Logs
Traces
Terminal
```

### Architecture

시스템 설계 Canvas입니다.

```text
Client
  │
  ▼
Load Balancer
  │
  ▼
API Server ───── Redis
  │
  ▼
PostgreSQL
```

각 컴포넌트가 단순 그림이면 안 됩니다.

노드를 클릭하면 실제 속성이 나옵니다.

```text
Redis Cluster

Nodes              3
Memory              4 GB
Eviction            allkeys-lru
TTL                 300 sec
Replication         1
Max Connections     10,000

Observed
Memory              78%
Hit Rate            92.4%
P99                  12ms
```

즉 **다이어그램 자체가 시뮬레이터 UI**가 됩니다.

------

# 4. Architecture Canvas를 살아있는 시스템으로

이 부분이 SysDrill의 가장 중요한 차별점이 될 수 있습니다.

DB 아이콘을 클릭하면:

```text
PostgreSQL

Instance
8 vCPU
32 GB RAM

Connections
500

Replication
Primary + 2 replicas

Indexes
products_pkey
idx_category
idx_created_at
```

그리고 실제 상태가 표시됩니다.

```text
CPU        █████████░ 91%
Conn       ██████████ 498 / 500
Disk       ███░░░░░░░ 31%
```

장애가 발생하면 다이어그램에서도 바로 보입니다.

```text
API Server → Redis → PostgreSQL
              ⚠
         latency 850ms
```

따라서 사용자가

**Architecture → Metrics → Logs**

를 왔다 갔다 하면서 추론하게 됩니다.

------

# 5. 구현 Drill도 “코딩 문제”에서 벗어나야 합니다

예를 들어 Rate Limiter Drill이라면 단순히

> Rate Limiter를 구현하세요.

가 아닙니다.

### Stage 1 — Basic

```text
100 requests / minute
```

구현합니다.

### Stage 2 — Concurrency

```text
10 application instances
```

테스트를 돌립니다.

결과:

```text
Expected allowed: 100
Actual allowed:   173

Race condition detected
```

### Stage 3 — Distributed

새 요구사항입니다.

```text
50 API Servers
1M users
```

### Stage 4 — Redis Failure

```text
Redis unavailable for 15 sec
```

질문:

> Rate limiting을 fail-open 할 것인가, fail-closed 할 것인가?

### Stage 5 — Hot Key

```text
user_id = 381923

32,000 req/sec
```

### Stage 6 — Production

Metrics를 추가하게 합니다.

```text
rate_limit_allowed_total
rate_limit_rejected_total
rate_limit_latency
redis_error_total
```

이렇게 해야 **“Rate Limiter를 구현했다”가 아니라 “Rate Limiter를 운영할 줄 안다”**가 됩니다.

------

# 6. Test Lab

Build Your Own X와 크게 차별화할 수 있는 영역입니다.

사용자가 자신의 구현을 다양한 조건에서 시험할 수 있습니다.

```text
Test Lab

Functional
✓ Basic requests
✓ Expiration
✓ Boundary conditions

Concurrency
✓ 100 threads
✗ 10,000 threads

Failure
✓ Redis timeout
✗ Redis unavailable

Load
RPS     12,000
P99     420ms
Errors  0.3%

Chaos
[ Inject Redis Failure ]
[ Add 500ms Network Latency ]
[ Kill Node ]
```

단순 Unit Test보다 훨씬 실무적입니다.

------

# 7. Traffic Lab

사용자가 직접 트래픽을 설계할 수 있게 합니다.

```text
Traffic Generator

Pattern

○ Constant
○ Ramp
● Spike
○ Seasonal
○ Flash Crowd
○ Custom

Baseline
2,000 RPS

Peak
40,000 RPS

Ramp
60 sec
```

더 중요한 것은 **트래픽 특성**입니다.

```text
Read / Write

████████░░ 80 / 20

Hot Key Distribution
Zipf 1.2

Payload
P50   2KB
P99   150KB
```

이걸 실행하면 실제 시스템 시뮬레이션이 시작됩니다.

------

# 8. Chaos Lab

여기서 워게임 성격이 본격적으로 살아납니다.

사용자가 직접 장애를 주입할 수도 있습니다.

```text
Chaos Lab

Infrastructure

[ Kill Instance ]
[ Kill AZ ]
[ Network Partition ]

Database

[ Slow Query ]
[ Connection Exhaustion ]
[ Replica Lag ]

Cache

[ Redis Failure ]
[ Cache Flush ]
[ Hot Key ]

Messaging

[ Kafka Lag ]
[ Consumer Failure ]
[ Duplicate Message ]
```

하지만 학습 중에는 일부 Chaos를 숨기는 것이 좋습니다.

사용자가 모르는 상태에서 장애가 발생해야 **Incident Drill**이 됩니다.

------

# 9. Observability를 실제 학습 영역으로 만들기

대부분의 시스템 디자인 학습에서 빠지는 부분입니다.

SysDrill에서는 사용자가 직접 관측성을 설계하게 하는 것이 좋습니다.

### Metrics

```text
HTTP

request_rate
error_rate
latency_p50
latency_p95
latency_p99
```

### Infrastructure

```text
CPU
Memory
Network
Disk IO
Connections
```

### Application

```text
Thread Pool
Connection Pool
Queue Depth
GC
```

사용자가 dashboard를 직접 구성하게 할 수도 있습니다.

이것 자체가 학습입니다.

------

# 10. Logs / Trace도 가짜 장식이어서는 안 됩니다

예를 들어 장애가 발생했습니다.

```text
14:02:14 INFO  request started
14:02:15 WARN  redis timeout
14:02:15 WARN  cache fallback
14:02:17 ERROR database connection timeout
```

Trace를 보면:

```text
GET /products

API Gateway     4ms
Product API   842ms
 ├ Redis      501ms
 └ DB         330ms
```

사용자는 여기서 병목을 추론합니다.

즉 SysDrill은 자연스럽게

> **Observability Training Platform**

역할까지 하게 됩니다.

------

# 11. Incident에서는 “정답 버튼”을 없애는 것이 좋습니다

이 부분은 특히 중요합니다.

다음처럼 만들면 안 됩니다.

```text
장애 원인은?

A. Redis
B. DB
C. Kafka
D. Network
```

실제 장애 대응이 아닙니다.

대신 사용자가 자유롭게 조사해야 합니다.

```text
Metrics
Logs
Trace
Architecture
Deploy History
Feature Flags
Configuration
```

그리고 액션합니다.

```text
Scale API Servers

8 → 16

Estimated cost
+$420 / month

[ Execute ]
```

실행하면 시스템 상태가 실제로 변합니다.

------

# 12. 모든 대응에는 Trade-off가 있어야 합니다

이것이 SysDrill의 깊이를 결정합니다.

예를 들어 DB CPU가 98%입니다.

사용자가

```text
Scale DB
```

를 선택합니다.

장애는 완화됩니다.

하지만 결과 리포트에서는:

```text
Incident resolved

MTTR
8m 42s

However

Root cause was cache stampede.

DB scaling reduced symptoms,
but did not resolve the root cause.

Estimated unnecessary cost:
$2,400 / month
```

이라고 평가합니다.

즉 **“살렸느냐”와 “좋은 엔지니어링 판단이었느냐”를 분리해서 평가**합니다.

------

# 13. Deployment History도 넣는 것을 추천합니다

장애 분석 화면에 다음이 있습니다.

```text
Deployments

13:32 product-api v1.41
13:48 cache-service v2.12
14:01 recommendation v3.8

Incident started
14:04
```

사용자가 변경사항을 조사합니다.

```text
cache-service v2.12

TTL

Before
300 sec

After
30 sec
```

이런 단서가 실제 장애 원인과 연결됩니다.

이 정도가 되면 상당히 실제적인 Incident Investigation이 됩니다.

------

# 14. 시스템에 “시간” 개념을 넣어야 합니다

시뮬레이션 세계의 시간이 흐릅니다.

```text
09:00

RPS
4K

12:00

RPS
12K

18:00

RPS
34K

20:14

⚠ latency increasing
```

사용자가 아무 행동도 하지 않으면 장애가 악화될 수 있습니다.

```text
20:16
DB CPU 87%

20:18
DB CPU 96%

20:19
Connection pool exhausted

20:20
Error rate 18%
```

이렇게 해야 워게임이 됩니다.

------

# 15. Drill에 “Information Fog”를 넣는 것도 좋습니다

실무에서는 모든 정보가 처음부터 주어지지 않습니다.

예를 들어 Incident 시작 시에는:

```text
Customer Support

"결제가 가끔 두 번 되는 것 같습니다."
```

이것만 줍니다.

사용자가 조사합니다.

```text
Metrics
→ 정상

Logs
→ 일부 retry

Trace
→ timeout

Kafka
→ duplicate delivery
```

점점 문제를 좁혀갑니다.

이를 **Information Fog** 또는 **Progressive Disclosure** 메커니즘으로 만들 수 있습니다.

------

# 16. AI는 정답 생성기가 아니라 “Scenario Director”가 되어야 합니다

이 부분이 SysDrill의 핵심 AI 기능이 될 수 있습니다.

AI 역할을 세 가지로 나눕니다.

### AI Mentor

사용자가 요청할 때 힌트를 줍니다.

```text
현재 상황에서 어떤 지표를 먼저 봐야 할까요?
```

### AI Evaluator

설계와 행동을 평가합니다.

```text
DB Read Replica 추가

✓ Read scalability
✓ Availability

Potential issues

⚠ replication lag
⚠ read-after-write consistency
```

### AI Scenario Director

가장 중요한 역할입니다.

사용자의 설계에 따라 **다음 사건을 생성합니다.**

예를 들어 사용자가 Queue를 도입했습니다.

그러면 다음 Drill에서:

> Consumer Lag이 증가합니다.

사용자가 Cache를 넣었습니다.

> Hot Key 문제가 발생합니다.

Multi-region을 선택했습니다.

> Network Partition이 발생합니다.

즉 **사용자의 선택이 다음 문제를 만들어냅니다.**

------

# 17. 평가 방식도 훨씬 깊게 가져가야 합니다

단일 점수 대신 **Engineering Skill Matrix**를 구축하는 것을 추천합니다.

```text
                    Current

System Design       ████████░░ 82
Scalability         ███████░░░ 74
Reliability         ██████░░░░ 66
Observability       ████░░░░░░ 43
Incident Response   ████████░░ 81
Distributed Systems █████░░░░░ 58
Performance         ███████░░░ 72
Cost Awareness      ████░░░░░░ 45
```

더 세부적으로는:

```text
Caching
Kafka
Database
Concurrency
Consistency
Networking
Rate Limiting
Idempotency
Backpressure
Replication
Sharding
```

까지 Skill Graph를 만들 수 있습니다.

------

# 18. 결과 화면은 Postmortem 형태가 좋습니다

Incident Drill이 끝나면 단순히

> 85점입니다.

가 아니라 실제 Postmortem처럼 보여줍니다.

```text
INCIDENT REPORT

Duration
18m 32s

MTTD
4m 12s

MTTR
14m 20s

Impact

18,241 failed requests
3.2% users affected

Root Cause

Cache stampede caused
DB connection exhaustion.

Your actions

14:04 Checked CPU
14:07 Scaled API
14:09 Checked Redis
14:12 Increased TTL
14:15 Recovered
```

그리고 평가합니다.

```text
Strong

✓ Metrics investigation
✓ Fast mitigation

Weak

⚠ Root cause identification
⚠ Cost awareness
⚠ Observability
```

이 화면은 **포트폴리오로 공유할 수 있게 만들어도 좋습니다.**

------

# 19. Replay 기능

상당히 강력한 기능입니다.

```text
Replay Incident
```

누르면 시간축을 재생합니다.

```text
14:02 Traffic spike

14:04 DB CPU 95%

14:05

YOU:
Scale API 8 → 16

14:06

Result:
DB load increased
```

사용자는

> “여기서 잘못 판단했구나.”

를 정확히 알 수 있습니다.

------

# 20. Expert Replay

더 강력한 학습 기능입니다.

같은 상황을

```text
Your Run
Expert Run
Community Median
```

으로 비교합니다.

예:

```text
             You     Expert

MTTD         6m       2m
MTTR        18m       7m
Actions      12        5
Cost        +$840     +$0
```

그리고 Expert가 어떤 순서로 조사했는지 재생합니다.

이 기능은 단순 해설보다 학습 효과가 큽니다.

------

# 21. Drill은 작은 문제부터 “Production System”까지 이어져야 합니다

커리큘럼을 이렇게 구성할 수 있습니다.

```text
LEVEL 1
Components

Rate Limiter
Cache
Queue
Circuit Breaker
Connection Pool
Retry

        ↓

LEVEL 2
Services

Notification
Payment
Search
Reservation
Feed

        ↓

LEVEL 3
Distributed Systems

Kafka-like Broker
Redis-like Cache
Distributed Lock
KV Store
Job Queue

        ↓

LEVEL 4
Production Systems

E-commerce
Streaming
Payment
Messaging
Social Feed

        ↓

LEVEL 5
Production Incidents

Traffic Spike
DB Failure
Cache Stampede
Kafka Lag
Network Partition
Bad Deployment
```

이

이 방향으로 가면 중요한 차이가 생깁니다.

**CodeCrafters가 “Build it yourself”라면 SysDrill은 “Own it in production”을 목표로 해야 합니다.**

즉,

> **구현할 수 있는가 → 설계할 수 있는가 → 부하에서 버티는가 → 관측할 수 있는가 → 장애를 진단할 수 있는가 → 복구할 수 있는가 → 왜 그런 판단을 했는지 설명할 수 있는가**

까지 가는 것입니다.

그리고 이를 위해서는 Drills를 단순 문제 목록이 아니라 **상태를 가진 시뮬레이션 세계**로 구현하는 것이 핵심입니다.

------

# 22. 개인적으로 가장 중요하다고 보는 기능: System Sandbox

여기서 한 단계 더 나갈 수 있습니다.

Drill을 완료한 뒤 시스템을 버리지 않습니다.

사용자가 만든 시스템을 **Sandbox**로 저장합니다.

예:

```text
My Systems

Product Search v3

Architecture
12 components

Peak Capacity
32K RPS

Estimated Cost
$1,420/month

Reliability
99.93%

[ Open Sandbox ]
```

Sandbox에 들어가면 자유롭게 실험할 수 있습니다.

```text
What if?

Traffic
10K → 100K

Redis
3 → 6 nodes

DB
Primary → Sharded

Region
1 → 3

[ Simulate ]
```

결과:

```text
Before              After

P99     420ms        81ms
Cost    $1,420       $4,820
Avail   99.93%       99.97%

New Risk

⚠ Cross-region consistency
⚠ Operational complexity
```

이 기능은 **System Design 학습과 실제 엔지니어링 사고를 연결하는 핵심**이 될 수 있습니다.

------

# 23. “What-if Simulator”를 핵심 기능으로

사용자가 설계를 바꿀 때마다:

> “그래서 이 변경이 실제로 무엇을 개선하는가?”

를 보여줘야 합니다.

예:

```text
Change

PostgreSQL
        ↓
PostgreSQL + 3 Read Replicas
```

SysDrill:

```text
Estimated Impact

Read Capacity
12K → 38K RPS       ↑ 216%

P99
320 → 110ms         ↓ 65%

Availability
99.9 → 99.95%

Cost
$420 → $1,180       ↑ 181%

New Risks

⚠ Replication Lag
⚠ Read-after-write inconsistency
⚠ Failover complexity
```

물론 이런 수치가 허위 정밀도를 가져서는 안 됩니다. 초기에는 **모델 기반 추정치임을 명확히 표시**하고, Drill의 시뮬레이션 모델에서 계산 가능한 범위만 보여주는 것이 좋습니다.

------

# 24. 사용자가 “결정을 기록”하게 해야 합니다

시스템 디자인에서 중요한 것은 정답보다 **trade-off reasoning**입니다.

그래서 주요 변경마다 작은 ADR(Architecture Decision Record)을 작성하게 할 수 있습니다.

```text
Decision

Add Redis Cache

Why?

[ DB read load를 줄이고 P99 latency를 개선하기 위해 ]

Trade-offs?

[ stale data를 허용하며 TTL 60초 사용 ]

[ Save Decision ]
```

나중에 장애가 발생합니다.

```text
INCIDENT

상품 가격 변경 후
일부 사용자에게 이전 가격이 노출되고 있습니다.
```

SysDrill은 과거 ADR과 연결합니다.

> 이 장애는 Stage 3에서 선택한 캐시 정책과 관련되어 있습니다.

이 경험은 상당히 교육적입니다.

------

# 25. 설계 면접 모드도 Drill 엔진 위에서 구현

별도의 서비스로 만들 필요가 없습니다.

```text
Mode

○ Guided
○ Practice
● Interview
```

### Guided

힌트 많음
AI 설명 가능
시간 제한 없음

### Practice

힌트 제한
피드백은 단계 종료 후 제공

### Interview

AI가 interviewer가 됩니다.

```text
Interviewer

현재 설계에서 트래픽이
10배 증가한다고 가정해보겠습니다.

어디가 먼저 병목이 될 것이라고 생각하시나요?
```

사용자가 답합니다.

AI가 그 답을 기반으로 꼬리 질문합니다.

```text
그렇다면 Redis를 추가하셨는데,
cache invalidation은 어떻게 처리하시겠습니까?
```

그리고 실제 시뮬레이션까지 연결합니다.

이렇게 하면 **System Design Interview + Simulator**가 됩니다.

------

# 26. Drill 상세 페이지 자체도 바꾸는 것이 좋습니다

현재처럼:

```text
선착순 쿠폰

coupon · MEDIUM

[ 시작 ]
```

이면 너무 문제은행처럼 보입니다.

대신:

```text
┌────────────────────────────────────────────────────┐

 FLASH SALE

 선착순 쿠폰 시스템

 100만 사용자가 동시에 접속하는
 쿠폰 발급 시스템을 구축하고 운영하세요.

 ─────────────────────────────────────────────────

 Mission

 Design → Build → Scale → Incident

 Starting Scale

 10K RPS
 1M users

 You'll practice

 Rate Limiting
 Redis
 Concurrency
 Idempotency
 Queue
 Observability

 Estimated
 4–6 hours

 Difficulty
 ███████░░░ Senior

 Production Incidents
 3 Unknown

                  [ Start Drill ]

└────────────────────────────────────────────────────┘
```

이렇게만 바꿔도 제품의 깊이에 대한 인식이 크게 달라집니다.

------

# 27. Drill Map도 추천합니다

단순 리스트 대신 dependency graph를 제공합니다.

```text
                     ┌─ Cache Stampede
Rate Limiter ────────┤
                     └─ Flash Sale
                            │
Concurrency ────────────────┤
                            ▼
                       Coupon System
                            │
Idempotency ────────────────┤
                            ▼
                      Payment System
                            │
Kafka ──────────────────────┤
                            ▼
                    E-commerce Drill
```

사용자가

> “왜 Rate Limiter를 공부해야 하지?”

라는 질문에 대한 답을 자연스럽게 얻게 됩니다.

------

# 28. Drill의 학습 단위를 Skill Graph와 연결

예를 들어 `선착순 쿠폰`은:

```text
Concurrency        +++
Redis              +++
Rate Limiting      ++
Atomic Operations  +++
Observability      ++
Incident Response  ++
```

를 훈련합니다.

사용자 프로필에서는:

```text
Distributed Systems

Consistency       72
Replication       45
Partitioning      61
Consensus         28

Recommended

→ Redis Replication Failure
→ Distributed Lock
→ Leader Election
```

이렇게 다음 학습을 추천합니다.

단순히 **“다음 챕터”가 아니라 “현재 역량의 빈 곳”**을 채우는 방식입니다.

------

# 29. 하나의 Drill을 예로 완전히 구성하면

## Flash Sale / 선착순 쿠폰

### Phase 0 — Brief

```text
Campaign

Coupon
100,000

Users
5,000,000

Start
14:00

Requirements

한 사용자당 하나
중복 발급 불가
재고 초과 발급 불가
```

------

### Phase 1 — Design

사용자가 설계합니다.

```text
Client
 ↓
API
 ↓
Redis
 ↓
DB
```

SysDrill이 질문합니다.

> Redis가 장애 나면 어떻게 됩니까?

------

### Phase 2 — Build

사용자가 핵심 로직을 구현합니다.

```text
issueCoupon(userId)
```

Test Lab 실행:

```text
Concurrency Test

100K requests

Expected
100K coupons

Issued
100,283

FAILED

Overselling detected
```

------

### Phase 3 — Fix

사용자가

```text
Redis INCR
Lua Script
Distributed Lock
DB Lock
```

등의 전략을 선택합니다.

각각 trade-off가 다릅니다.

------

### Phase 4 — Load

14:00 이벤트를 시뮬레이션합니다.

```text
13:59

1K RPS

14:00

83K RPS
```

------

### Phase 5 — Incident

갑자기:

```text
ALERT

P99 latency
4.2 sec

Redis CPU
97%
```

발생합니다.

------

### Phase 6 — Investigation

사용자가 Metrics를 봅니다.

Logs를 봅니다.

Trace를 봅니다.

Architecture를 봅니다.

------

### Phase 7 — Response

사용자가 판단합니다.

```text
[ Scale API ]

[ Scale Redis ]

[ Queue Requests ]

[ Reject Traffic ]

[ Increase Timeout ]

[ Rollback ]
```

각 선택이 실제 상태에 영향을 줍니다.

------

### Phase 8 — Second Incident

복구했다고 생각했는데:

```text
Kafka Consumer Lag

1.8M messages
```

이 발생합니다.

------

### Phase 9 — Recovery

사용자가 다시 대응합니다.

------

### Phase 10 — Postmortem

최종 결과:

```text
Flash Sale #2841

Reliability        78
Scalability        91
Concurrency        84
Observability      52
Incident Response  71
Cost Awareness     63
```

그리고 AI가 분석합니다.

------

# 30. 이렇게 되면 SysDrill의 포지션이 달라집니다

제가 생각하는 최종 구조는 다음입니다.

```text
                 SysDrill

                    │

            SYSTEM MISSIONS

                    │

      ┌─────────────┼─────────────┐
      │             │             │
    Design         Build       Operate
      │             │             │
      └─────────────┼─────────────┘
                    │
                Simulate
                    │
        ┌───────────┼───────────┐
        │           │           │
      Load        Chaos     Observability
        │           │           │
        └───────────┼───────────┘
                    │
                 Incident
                    │
            Diagnose / Recover
                    │
                Postmortem
                    │
                  Replay
                    │
              Skill Graph
                    │
              Next Mission
```

여기에 **Sandbox + What-if Simulator + AI Scenario Director**가 전체를 관통합니다.

------

## 결국 SysDrill의 핵심 루프

제품 전체를 한 문장으로 줄이면 이것입니다.

> **Design it → Build it → Run it → Break it → Fix it → Understand why.**

이 구조라면 Build Your Own X와 직접 비교할 필요도 상당히 줄어듭니다. Build Your Own X가 구현 자료의 큐레이션에 가깝다면, SysDrill은 **시스템 엔지니어링의 전체 lifecycle을 훈련하는 interactive environment**라는 별도의 제품 카테고리로 포지셔닝할 수 있기 때문입니다.

그리고 이 정도의 기능을 실제로 만들려면 **모든 것을 한꺼번에 구현해서는 안 됩니다.** MVP에서는 `Architecture Canvas + Simulation Engine + Observability + Incident Engine + Evaluation`의 다섯 가지가 제품의 핵심입니다. 코드 에디터나 Community보다 이쪽이 먼저입니다.

특히 **Simulation Engine**이 얕으면 위 기능들이 결국 AI가 그럴듯한 숫자와 장애를 만들어내는 역할극에 그칩니다. 반대로 시스템 컴포넌트별 capacity/latency/queue/failure 모델과 이벤트 엔진을 제대로 구축하면 SysDrill의 기술적 해자가 될 가능성이 있습니다.