지금까지 기획한 Drill은 이미 `Design → Build → Load → Observe → Incident → Recover → Postmortem`까지 있어서 기본 골격은 상당히 깊습니다. 여기서 더 개선하려면 기능 수를 단순히 늘리기보다 **“현실에서는 엔지니어가 무엇 때문에 어려움을 겪는가”**를 추가해야 합니다.

제가 추가한다면 Drill을 최종적으로 다음 구조까지 확장하겠습니다.

```
                 SysDrill Mission

Requirements
     ↓
Clarification
     ↓
Estimation
     ↓
Design
     ↓
Decision / ADR
     ↓
Build
     ↓
Test
     ↓
Deploy
     ↓
Observe
     ↓
Load / Scale
     ↓
Incident
     ↓
Investigate
     ↓
Mitigate
     ↓
Recover
     ↓
Verify
     ↓
Postmortem
     ↓
Redesign
     ↓
Replay / Alternative Run
```

특히 아래 기능들이 현재 기획을 한 단계 더 올릴 수 있습니다.

## 1. Requirements를 일부러 불완전하게 제공

현재 Drill에서 요구사항을 너무 친절하게 주면 실제 System Design이나 업무와 차이가 납니다.

예를 들어 처음에는 이것만 줍니다.

> **선착순 쿠폰 시스템을 설계하세요.**
>
> 이벤트 시작 시 약 100만 명이 접속할 것으로 예상됩니다.

그리고 바로 설계하게 하지 않고:

**Ask a question** 기능을 제공합니다.

사용자가 질문해야 합니다.

```
Q. 쿠폰 수량은 얼마인가요?
→ 100,000개입니다.

Q. 한 사용자가 여러 장 받을 수 있나요?
→ 1장만 가능합니다.

Q. 중복 발급은 허용됩니까?
→ 절대 허용되지 않습니다.

Q. 발급 결과를 즉시 알려줘야 합니까?
→ 3초 이내면 됩니다.
```

질문하지 않으면 해당 요구사항을 모르는 상태에서 설계하게 됩니다.

결과에서는:

```
Requirements Discovery

Critical questions asked     7 / 9
Useful questions             6
Irrelevant questions         3

Missed requirement

⚠ Duplicate issuance must never occur.
```

를 평가합니다.

**Requirement clarification 자체가 Skill이 되는 것입니다.**

------

## 2. Capacity Estimation 단계 추가

Architecture Canvas 전에 반드시 규모를 추정하게 하는 것을 권합니다.

```
Users                    5M
Peak concurrent users    ?

Peak RPS                 ?
Write RPS                ?
Storage / day            ?
Bandwidth                 ?
```

사용자가 계산합니다.

그 후 실제 workload와 비교합니다.

```
Your estimate

Peak RPS
30K

Simulation
72K

Difference
-58%
```

중요한 것은 정확한 숫자보다 **order of magnitude를 잡는 능력**입니다.

Skill Graph에도:

```
Capacity Planning
Traffic Estimation
Storage Estimation
Bandwidth Estimation
```

을 추가합니다.

------

## 3. Architecture Constraint 시스템

사용자가 항상 원하는 기술을 선택할 수 있게 하면 현실성이 떨어집니다.

Mission마다 제약을 줍니다.

예:

```
Company Constraints

Cloud
AWS

Database
PostgreSQL already in use

Team
5 Backend Engineers

Deadline
6 weeks

Budget
$8,000 / month

Operational Experience

Kafka       Low
Redis       High
Kubernetes  Medium
```

이 상태에서 사용자가:

```
Kafka
Cassandra
ElasticSearch
Redis
Kubernetes
```

를 다 집어넣으면 SysDrill이 평가합니다.

> 시스템은 확장 가능하지만 현재 팀 규모와 운영 경험에 비해 operational complexity가 높습니다.

이렇게 해야 **“좋은 아키텍처 = 기술을 많이 쓰는 것”이라는 잘못된 습관**을 교정할 수 있습니다.

------

# 4. Complexity Budget

위 개념을 시스템적으로 발전시킬 수 있습니다.

Architecture에 일종의 Complexity Score를 둡니다.

```
Operational Complexity

████████░░ 82 / 100

Services          12
Databases          4
Queues             3
Caches             2

Operational Skills Required

Kafka
Redis
PostgreSQL
ElasticSearch
Kubernetes
```

그리고:

```
Team Capacity

████░░░░░░ 43
```

처럼 비교합니다.

즉 SysDrill이 단순히 확장성만 평가하지 않고:

> **이 팀이 이 시스템을 실제로 운영할 수 있는가?**

까지 평가합니다.

------

# 5. Cost Simulator 강화

Cost Awareness를 실제 Drill 요소로 만듭니다.

화면 상단에:

```
Estimated Monthly Cost

$12,420

Budget
$10,000

⚠ 24% over budget
```

Architecture Node를 클릭하면:

```
Redis Cluster

3 × cache.r7g.large

$612 / month
```

Scale-out하면:

```
3 → 6 nodes

Latency
-31%

Capacity
+87%

Cost
+$612/month
```

이렇게 trade-off를 바로 보여줍니다.

그리고 Mission 중간에:

> CFO 요청: 인프라 비용을 30% 절감해야 합니다.

같은 이벤트도 넣을 수 있습니다.

------

# 6. Deploy 단계 추가

현재 Build와 Incident 사이에 **Deployment**가 들어가면 좋습니다.

```
Deployment

Version
payment-api v2.3

Strategy

○ Rolling
○ Blue / Green
● Canary

Canary Traffic
5%

Duration
10 min

Rollback Threshold

Error Rate > 2%
```

사용자가 직접 배포 전략을 결정합니다.

그리고 실제로:

```
5% Canary

Error Rate
0.4%

10%

1.2%

25%

5.8% ⚠
```

가 됩니다.

여기서 사용자가 판단합니다.

```
[ Continue ]

[ Pause ]

[ Rollback ]
```

이건 상당히 실무적인 훈련입니다.

------

# 7. Change Risk Analysis

Deploy 전에 SysDrill이 변경사항을 보여줍니다.

```
Release v2.3

Changes

+ Retry payment request
+ New DB index
+ Redis TTL 300 → 60
+ Kafka consumer concurrency 8 → 32
```

사용자에게 묻습니다.

> 가장 위험하다고 생각되는 변경은 무엇입니까?

그리고 실제 Incident와 연결합니다.

이를 통해 **Change Review → Deploy → Incident**가 하나의 흐름이 됩니다.

------

# 8. SLO / SLI를 직접 정의

Mission 시작 시 사용자가 시스템 목표를 설정하게 합니다.

```
Define SLO

Availability

[ 99.95 ] %

Latency

P99 < [ 300 ] ms

Error Rate

< [ 0.5 ] %

Data Freshness

< [ 5 ] sec
```

Incident가 발생하면 단순히 서버가 죽었는지가 아니라:

```
SLO Status

Availability    ✓
Latency         ✕
Freshness       ✓

Error Budget

Monthly
43.2 min

Consumed
31.8 min
```

처럼 보여줍니다.

Reliability Engineering 학습까지 자연스럽게 연결됩니다.

------

# 9. Runbook 기능

사용자가 장애 대응 절차를 직접 작성하게 합니다.

예:

```
Runbook

High DB CPU

1. Check query latency
2. Check connection pool
3. Check cache hit rate
4. Check recent deployments
5. ...
```

나중에 실제 Incident가 발생합니다.

SysDrill이 비교합니다.

```
Runbook Followed

Step 1 ✓
Step 2 ✕
Step 3 ✓

Missing

Replica lag investigation
```

Drill이 반복될수록 사용자의 개인 Runbook이 발전합니다.

------

# 10. On-call Handoff

Incident를 혼자 해결하는 것에서 한 단계 더 나갑니다.

시나리오 중간에:

```
03:14 AM

You are now on-call.

Previous engineer note:

"Latency started increasing around 02:50.
DB looks okay.
Possibly Redis."
```

그런데 이전 엔지니어의 판단이 틀릴 수도 있습니다.

즉 사용자는 **handoff 내용을 참고하되 독립적으로 검증**해야 합니다.

반대로 Drill 종료 시 사용자가 다음 엔지니어에게 handoff를 작성하게 할 수도 있습니다.

------

# 11. Incident Communication

실제 장애 대응에서 기술적 해결만큼 중요한 부분입니다.

Incident 도중:

```
Customer Support asks:

"What should we tell customers?"
```

사용자가 status update를 작성합니다.

예:

```
We are investigating elevated
latency affecting checkout...
```

AI가 평가합니다.

```
Communication

Clarity          88
Accuracy         94
Overclaiming     12
Actionability    76
```

그리고 상황이 바뀌면 새로운 업데이트를 요구합니다.

SRE/시니어 백엔드 훈련으로 갈수록 꽤 가치 있는 기능입니다.

------

# 12. Multi-role Incident

더 고급 Drill에서는 혼자 모든 것을 하지 않습니다.

```
Incident Team

You
Incident Commander

AI Agent
Database Engineer

AI Agent
SRE

AI Agent
Application Engineer
```

사용자가 요청합니다.

> DB slow query 확인해주세요.

AI Engineer:

> 최근 5분간 `orders` query P99가 2.8초까지 상승했습니다. 실행 계획을 확인할까요?

사용자가 업무를 위임합니다.

즉 **Incident Commander 역할**까지 훈련합니다.

------

# 13. Multiplayer War Room

향후 가장 강력한 기능 중 하나가 될 수 있습니다.

3~5명이 같은 Incident에 들어갑니다.

```
WAR ROOM

Incident Commander    Alice
Backend               Bob
Database              Kim
SRE                    You
```

각자 볼 수 있는 도구나 역할이 다릅니다.

SRE:

```
Metrics
Infrastructure
Deployment
```

Backend:

```
Logs
Code
Trace
```

DB:

```
Queries
Locks
Replication
```

Discord/Slack처럼 채팅하면서 해결합니다.

이건 실제 회사의 GameDay에 상당히 가까워집니다.

------

# 14. Unknown Unknowns

고급 Drill에서는 무엇을 훈련하는지조차 숨기는 것이 좋습니다.

초급:

> Cache Stampede Incident

고급:

> **Checkout degradation**

뿐입니다.

사용자는 Redis 문제인지, DB인지, network인지, deploy인지 모릅니다.

더 고급:

```
Symptoms

Checkout P99 ↑
Error Rate ↑

Everything else unknown.
```

이게 실제 Incident에 가깝습니다.

------

# 15. Compound Failure

초급 Drill은 원인 하나면 됩니다.

고급은 여러 원인이 동시에 존재해야 합니다.

예:

```
Primary

Bad Redis TTL config

+

Contributing Factor

DB connection pool too small

+

Amplifier

Retry without jitter
```

겉으로 보면:

```
DB CPU 100%
```

이라 DB 문제처럼 보입니다.

실제 root cause chain은:

```
TTL ↓
  ↓
Cache misses ↑
  ↓
DB traffic ↑
  ↓
Timeout
  ↓
Retry storm
  ↓
DB collapse
```

이런 **causal chain을 찾아내는 것**이 고급 Drill의 핵심이 됩니다.

------

# 16. Recovery Verification

장애가 사라졌다고 바로 Drill을 끝내면 안 됩니다.

```
Service recovered.

Verify recovery.

□ Error rate normal
□ Queue drained
□ Data consistency verified
□ Replicas synchronized
□ No stuck jobs
□ Customer impact stopped
```

예를 들어 API는 복구됐지만:

```
Kafka Lag

1.2M messages
```

가 남아있다면:

> ⚠ Partial Recovery

입니다.

**Mitigation과 Recovery를 분리**해서 가르치는 것입니다.

------

# 17. Data Integrity Check

특히 Payment/Order/Coupon에서 중요합니다.

장애가 복구된 후:

```
Integrity Audit

Orders created       98,281
Payments completed   98,317

Mismatch
36
```

사용자가 reconciliation을 수행해야 합니다.

```
[ Find Missing Orders ]

[ Retry Events ]

[ Compensate ]

[ Manual Review ]
```

그러면 Incident Response가 단순 서버 복구를 넘어갑니다.

------

# 18. Security Incident Drill

현재 기획은 reliability 중심인데 Security도 자연스럽게 확장할 수 있습니다.

예:

```
Security Drill

Credential Leak
SQL Injection
API Abuse
DDoS
Privilege Escalation
Secret Exposure
Suspicious Traffic
```

예를 들어:

```
Traffic

Normal
12K RPS

Current
84K RPS

Source

3 IP        61%
```

사용자가:

```
Rate Limit
WAF
Block IP
Rotate Key
Disable Endpoint
```

등을 판단합니다.

다만 SysDrill의 중심은 시스템 엔지니어링으로 유지하고 Security는 고급 Track으로 두는 편이 좋습니다.

------

# 19. Failure Injection을 사용자가 설계

고급 사용자는 장애를 해결하는 것뿐 아니라:

> **“이 시스템이 정말 안전한지 어떻게 검증할 것인가?”**

를 훈련해야 합니다.

사용자가 GameDay를 설계합니다.

```
GameDay Plan

Hypothesis

"Redis node 하나가 죽어도
checkout은 정상 동작한다."

Experiment

Kill Redis replica

Success Criteria

P99 < 500ms
Error < 1%

Abort Condition

Error > 5%
```

그리고 실행합니다.

이렇게 되면 **Chaos Engineering 자체를 배우는 Drill**이 됩니다.

------

# 20. Design Review Gate

설계가 끝났다고 바로 Build로 넘어가지 않습니다.

```
Architecture Review

Before Production
```

AI 또는 Community/Expert가 질문합니다.

> DB가 SPOF 아닌가요?

> Kafka를 도입한 이유는 무엇인가요?

> Exactly-once가 정말 필요합니까?

> Redis 장애 시 어떻게 됩니까?

사용자는 자신의 설계를 **방어(defend)**해야 합니다.

좋은 System Design Interview 훈련이기도 합니다.

------

# 21. Assumption Tracker

System Design에서 굉장히 좋은 기능이 될 수 있습니다.

사용자가 설계하면서:

```
Assumptions

✓ Read traffic >> Write
✓ Stale data 30 sec acceptable
✓ Region failure unlikely
✓ Users < 10M
```

을 기록합니다.

나중에 Scenario Director가:

> 새로운 요구사항:
>
> 가격은 1초 이내 동기화되어야 합니다.

라고 합니다.

그러면:

```
Assumption Broken

"Stale data 30 sec acceptable"
```

이 표시됩니다.

사용자는 설계를 다시 검토해야 합니다.

**좋은 시스템 설계는 가정 위에 만들어진다는 사실**을 훈련할 수 있습니다.

------

# 22. Counterfactual Replay

기존 Replay를 한 단계 발전시킵니다.

Drill 종료 후:

```
What if you had...

○ Added Cache
○ Used Queue
○ Rolled Back Earlier
○ Not Scaled DB
○ Used Circuit Breaker
```

를 선택합니다.

Simulation이 해당 시점에서 다시 실행됩니다.

예:

```
Actual Run

MTTR          18m
Impact        14%
Cost          +$820

Counterfactual

Rollback at 14:04

MTTR           7m
Impact          5%
Cost            $0
```

이 기능은 **“왜 내 판단이 좋거나 나빴는지”를 체감**하게 합니다.

------

# 23. Hidden Score를 권장합니다

Drill 진행 중에는 점수를 너무 많이 보여주지 않는 것이 좋습니다.

나쁜 UX:

```
DB 확인 +10점
Redis 확인 +20점
```

사용자가 점수를 최적화하게 됩니다.

대신 진행 중에는:

```
System State

DEGRADED
```

만 보여주고 평가 기준은 종료 후 공개합니다.

```
Diagnosis       82
Mitigation      91
Recovery        73
Cost            42
Communication   88
```

사용자가 **게임 규칙이 아니라 시스템 자체를 최적화**하게 만드는 것이 중요합니다.

------

# 24. Scenario Variants

같은 Drill을 다시 풀어도 똑같으면 안 됩니다.

예를 들어 Payment Incident:

```
Run #1
DB Connection Exhaustion

Run #2
Kafka Lag

Run #3
Bad Deployment

Run #4
DNS Failure

Run #5
Compound Failure
```

하지만 동일한 Skill을 평가합니다.

따라서:

```
Payment System

Completed 4 times

Reliability Confidence

████████░░ 84%
```

처럼 한 번의 성공보다 **여러 변형에서 반복적으로 성공했는지**를 평가할 수 있습니다.

------

# 25. Mastery와 Confidence를 분리

Skill Graph도 한 단계 발전시킬 수 있습니다.

예:

```
Cache Stampede

Knowledge
█████████░ 91

Application
████████░░ 82

Diagnosis
██████░░░░ 64

Recovery
███████░░░ 72

Confidence
████░░░░░░ 41
```

Confidence가 낮은 이유:

```
Only 1 incident variant completed.
```

즉 한 번 맞혔다고 Mastered 처리하지 않습니다.

------

# 26. Drill Generator

콘텐츠 확장성을 위해 상당히 중요합니다.

사용자가 선택합니다.

```
Generate Drill

Domain
Payment

Focus
Kafka

Skill
Incident Response

Difficulty
Senior

Duration
45 min

Unknown Failure
✓

[ Generate ]
```

AI Scenario Director가 생성하되, Simulation Engine이 지원하는 검증된 failure primitive만 조합합니다.

즉 AI가 마음대로 장애를 소설처럼 만드는 것이 아니라:

```
Scenario Director
        ↓
Validated Components
        +
Failure Primitives
        +
Traffic Models
        +
Evaluation Rules
```

로 생성해야 합니다.

------

# 27. 실제 Drill 화면에서 가장 추가하고 싶은 UI

최종적으로 Workspace의 오른쪽 상단에 **Mission Control**을 두겠습니다.

```
MISSION CONTROL

System
● DEGRADED

SLO
P99       ✕
Errors    ✓
Avail     ✓

Incident
00:08:42

Impact
12.4K users
```