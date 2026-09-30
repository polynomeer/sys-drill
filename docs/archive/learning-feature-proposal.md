네. SysDrill의 `Learning`은 일반적인 **강의/문서 모음**으로 만들면 제품의 강점이 약해집니다. Drills가 이미 강력한 실전 환경이므로 Learning은 그 앞뒤를 연결하는 **“엔지니어링 지식 체계 + 실험실 + 개인화 학습 시스템”**으로 설계하는 것이 좋습니다.

핵심 관계는 이렇게 잡는 것을 권합니다.

```
Learning
   ↓ 지식 습득
Concept
   ↓ 직접 관찰
Interactive Lab
   ↓ 작은 실험
Micro Drill
   ↓ 실제 적용
Full Drill
   ↓ 실패/약점 발견
Postmortem
   ↓
Learning으로 자동 연결
```

즉 **Learning → Drills → Learning**이 하나의 폐쇄 루프가 되어야 합니다.

------

# 1. Learning의 최상위 구조

Learning 메뉴에 들어오면 단순 강의 목록 대신 다음 구조를 권합니다.

```
Learning

├── My Learning
│   ├── Continue Learning
│   ├── Weak Skills
│   ├── Recommended Next
│   └── Recently Learned
│
├── Knowledge Map
│
├── Topics
│   ├── Fundamentals
│   ├── Networking
│   ├── Database
│   ├── Caching
│   ├── Messaging
│   ├── Distributed Systems
│   ├── Scalability
│   ├── Reliability
│   ├── Observability
│   ├── Performance
│   ├── Security
│   └── Incident Response
│
├── System Components
│   ├── PostgreSQL
│   ├── Redis
│   ├── Kafka
│   ├── Load Balancer
│   ├── CDN
│   └── ...
│
├── Patterns
│
└── Labs
```

여기서 가장 중요한 것은 **Knowledge Map**입니다.

------

# 2. Knowledge Map을 Learning의 중심으로

SysDrill이 단순 콘텐츠 사이트와 가장 크게 차별화할 수 있는 부분입니다.

예를 들어 사용자가 `Caching`을 선택하면:

```
                    Caching
                       │
          ┌────────────┼─────────────┐
          │            │             │
       Cache Aside   Write Through   Write Back
          │
          ▼
         TTL
          │
    ┌─────┼─────────┐
    │     │         │
Eviction Stampede  Hot Key
    │                │
    └──────┬─────────┘
           ▼
      Distributed Cache
           │
      ┌────┴─────┐
      │          │
Replication   Sharding
      │
      ▼
     Redis
```

각 노드에는 사용자 상태를 표시합니다.

```
● Mastered
◐ Learning
○ Not Started
⚠ Weak
```

예:

```
Caching
  │
  ├─ Cache Aside        ● 91
  ├─ TTL                ● 87
  ├─ Eviction           ◐ 72
  ├─ Cache Stampede     ⚠ 43
  ├─ Hot Key            ⚠ 38
  └─ Distributed Cache  ○
```

Drill 결과와 자동으로 연결됩니다.

사용자가 Incident Drill에서 Cache Stampede를 제대로 진단하지 못했다면:

> ⚠ Cache Stampede
> 최근 Drill 2개에서 취약점이 발견되었습니다.

가 표시됩니다.

------

# 3. Knowledge Node가 Learning의 기본 단위

강의 하나가 아니라 **Knowledge Node**를 기본 단위로 정의하는 것이 좋습니다.

예:

> ```
> Cache Stampede
> ```

노드에 들어가면 다음 구조입니다.

```
Cache Stampede

01 Understand
02 Visualize
03 Experiment
04 Implement
05 Break
06 Apply
07 Verify
```

이 일곱 단계가 중요합니다.

------

# 4. Understand — 개념을 짧고 정확하게

처음부터 30분짜리 강의를 보여주지 않습니다.

핵심 설명부터 시작합니다.

### Cache Stampede

> 많은 요청이 동일한 캐시 키의 만료 직후 동시에 DB로 유입되는 현상입니다.

그리고 바로:

```
Before expiration

Client ─┐
Client ─┼─→ Redis ─→ HIT
Client ─┘


After expiration

Client ─┐
Client ─┼─────────→ DB
Client ─┼─────────→ DB
Client ─┼─────────→ DB
Client ─┘─────────→ DB
                   ↑
                overload
```

설명은 다음 순서로 통일합니다.

```
What
Why
When
Symptoms
Solutions
Trade-offs
```

이 구조는 모든 Knowledge Node에서 동일하게 사용합니다.

------

# 5. Visualize — 애니메이션 기반 동작 원리

SysDrill에서는 이 기능이 상당히 중요합니다.

예를 들어 `Consistent Hashing`을 공부한다면 글만 보여주지 않습니다.

```
          Node A

      ● ● ● ● ●
   ●             ●
Node D           Node B
   ●             ●
      ● ● ● ● ●

          Node C
```

슬라이더:

```
Nodes

[-]  4  [+]

Keys

100,000

Virtual Nodes

[ 128 ]
```

Node B를 제거합니다.

사용자가 직접 봅니다.

```
Node B removed

Moved keys

Normal Hashing
74.8%

Consistent Hashing
24.1%
```

이렇게 해야 **왜 이 기술이 존재하는지** 체감합니다.

------

# 6. Interactive Explainer

단순 애니메이션보다 한 단계 더 나갑니다.

예를 들어 `Replication` 학습입니다.

사용자가 설정합니다.

```
Primary

        ┌── Replica A
        │
        └── Replica B
```

Replication delay:

```
[────────●────]

0ms          5000ms
```

사용자가 Write합니다.

```
SET price = 100
```

그리고 즉시 Replica에서 읽습니다.

```
Primary
price = 100

Replica
price = 90
```

SysDrill:

> 지금 관찰한 현상이 **Replication Lag**입니다.

그리고 바로 다음 개념으로 연결합니다.

```
Related

→ Read-after-write consistency
→ Eventual consistency
→ Quorum
```

------

# 7. Experiment — Mini Sandbox

Knowledge Node마다 작은 실험 환경을 제공합니다.

예를 들어 Connection Pool입니다.

```
Connection Pool Lab

Requests       1,000 RPS
Query Latency  100ms

Pool Size

[-] 10 [+]

Run
```

결과:

```
Pool Size       10

Throughput      98 req/s
Waiting         902
P99             8.4 sec
```

Pool을 100으로 올립니다.

```
Throughput      820 req/s
P99             1.1 sec
DB CPU          91%
```

500으로 올립니다.

```
DB CPU          100%

Throughput      610 req/s ↓
P99             4.8 sec ↑
```

사용자가 자연스럽게 배웁니다.

> Connection Pool은 클수록 좋은 것이 아니다.

이게 SysDrill Learning의 중요한 철학이 되어야 합니다.

**설명을 읽어서 이해하는 것이 아니라 값을 바꿔서 현상을 발견하게 합니다.**

------

# 8. Implement — 직접 구현

개념을 이해했다면 작은 구현으로 넘어갑니다.

예:

```
Knowledge

Rate Limiting
       ↓

Implementation Lab

Token Bucket
```

코드 에디터:

```
class TokenBucket {

    boolean allow(String key) {
        // TODO
    }

}
```

하지만 Full Build Drill보다 짧습니다.

10~30분 정도가 좋습니다.

이를 **Micro Build**라고 부를 수 있습니다.

------

# 9. Break — 일부러 깨뜨리기

이 기능을 Learning에도 넣는 것을 추천합니다.

예를 들어 Retry를 공부했습니다.

```
Retry Lab

Timeout
500ms

Retries
3

Backoff
None
```

정상 상태:

```
RPS
1,000
```

Dependency 장애를 발생시킵니다.

```
[ Fail Dependency ]
```

갑자기:

```
Original requests
1,000 RPS

Retry requests
3,000 RPS

Total
4,000 RPS
```

그리고 downstream이 죽습니다.

> Retry Storm

사용자가 직접 발견합니다.

그 다음:

```
Add

✓ Exponential Backoff
✓ Jitter
✓ Retry Budget
```

을 적용해봅니다.

이런 학습은 텍스트 몇 페이지보다 훨씬 강합니다.

------

# 10. Compare — 기술 선택 비교

시스템 디자인에서 굉장히 중요한 기능입니다.

예:

## Kafka vs RabbitMQ

단순 표가 아닙니다.

```
Compare

Kafka            RabbitMQ

Throughput
██████████       ██████

Retention
██████████       ███

Replay
✓                 △

Routing
△                 ✓

Operational
Complexity
High              Medium
```

그리고 사용자가 상황을 선택합니다.

```
Scenario

○ Event Streaming
○ Task Queue
○ Payment Event
○ Log Pipeline
○ Notification
```

`Payment Event`를 선택하면:

> 단순히 어느 기술이 “더 좋다”고 결정할 수 없습니다.

그리고 조건을 변경합니다.

```
Ordering required
YES

Replay required
YES

Traffic
500K events/sec
```

그 조건에서 고려할 trade-off를 보여줍니다.

------

# 11. Decision Lab

이 기능은 System Design 학습에 특히 좋습니다.

질문:

> 상품 조회 시스템에 캐시를 도입하려고 합니다.

조건:

```
Read RPS       30K
Write RPS      200
Freshness      < 10 sec
DB P99         120ms
```

선택:

```
○ Cache Aside
○ Write Through
○ Write Back
```

사용자가 선택합니다.

예를 들어 Cache Aside를 선택하면:

> 왜 이 전략을 선택했습니까?

짧게 reasoning을 입력합니다.

그 다음 SysDrill이 새로운 조건을 줍니다.

> 가격 변경은 사용자에게 1초 이내 반영되어야 합니다.

사용자가 기존 결정을 다시 검토합니다.

이것이 **System Design 사고 훈련**입니다.

------

# 12. Failure Encyclopedia

Learning 안에 반드시 넣었으면 하는 기능입니다.

일종의 **장애 패턴 백과사전**입니다.

```
Failure Patterns

Cache
├─ Cache Stampede
├─ Hot Key
├─ Cache Penetration
└─ Eviction Storm

Database
├─ Connection Exhaustion
├─ Lock Contention
├─ Slow Query
├─ Replica Lag
└─ Disk Saturation

Messaging
├─ Consumer Lag
├─ Poison Message
├─ Duplicate Delivery
└─ Rebalance Storm

Network
├─ Timeout
├─ Packet Loss
├─ Partition
└─ DNS Failure
```

각 항목에 들어가면:

```
Symptoms
Typical Metrics
Typical Logs
Common Causes
Bad Fixes
Mitigation
Prevention
Related Drills
```

가 나옵니다.

특히 **Bad Fixes**가 중요합니다.

예:

### DB Connection Exhaustion

흔히 하는 잘못된 대응:

> Connection Pool을 무작정 크게 만든다.

왜 위험한지 설명합니다.

------

# 13. Pattern Library

반대로 해결 패턴도 별도로 구성합니다.

```
Architecture Patterns

Reliability
├─ Circuit Breaker
├─ Bulkhead
├─ Retry + Backoff
├─ Graceful Degradation
└─ Failover

Data
├─ CQRS
├─ Event Sourcing
├─ Saga
├─ Outbox
└─ CDC

Scalability
├─ Sharding
├─ Read Replica
├─ Caching
├─ Queue
└─ Load Shedding
```

각 Pattern은 다음과 같이 구성합니다.

```
Problem

↓

Pattern

↓

How it works

↓

When to use

↓

When NOT to use

↓

Trade-offs

↓

Failure modes

↓

Interactive Lab

↓

Related Drills
```

**When NOT to use**를 반드시 넣는 것을 권합니다.

------

# 14. Real Incident Case Studies

Learning의 고급 콘텐츠로 매우 좋습니다.

실제 업계 장애 사례를 교육용으로 재구성합니다.

예:

```
Incident Case Study

Database Connection Exhaustion

Timeline
──────────────────────────

14:01 Deploy
14:04 Latency ↑
14:06 Error Rate ↑
14:08 DB Connections 100%
14:12 Rollback
14:18 Recovery
```

사용자에게 처음부터 원인을 알려주지 않습니다.

> 이 장애에서 어떤 신호를 먼저 확인하시겠습니까?

Case Study를 읽는 것도 작은 Incident Drill처럼 만듭니다.

단, 실제 기업 사례를 활용할 경우 공개된 postmortem에 근거해 사실과 교육용 변형을 명확히 구분해야 합니다.

------

# 15. Capacity Planning Lab

System Design Learning에 특히 강력합니다.

예:

> Instagram-like Feed

```
DAU
10M

Posts / user / day
2

Image Size
2MB

Read / Write
100 : 1
```

사용자가 계산합니다.

```
Average RPS
Peak RPS
Storage / Day
Storage / Year
Bandwidth
Cache Size
```

숫자를 입력하면 즉시 검증합니다.

그리고:

> DAU가 10M → 100M으로 증가했습니다.

다시 계산하게 합니다.

단순 공식 암기가 아니라 **규모 감각(back-of-the-envelope estimation)**을 훈련합니다.

------

# 16. Query / Index Lab

백엔드 개발자에게 매우 실용적입니다.

```
SELECT *
FROM orders
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20;
```

현재:

```
Rows
500M

Index
user_id
```

실행 계획:

```
Index Scan

Rows scanned
82,391

Latency
840ms
```

사용자가 composite index를 추가합니다.

```
(user_id, created_at)
```

결과:

```
Rows scanned
20

Latency
8ms
```

여기서:

- Index
- Cardinality
- Selectivity
- Covering Index
- B-Tree
- Query Planner

를 연결해서 배울 수 있습니다.

------

# 17. Distributed Systems Visual Lab

이 영역은 SysDrill의 대표 Learning 콘텐츠로 만들 만합니다.

예를 들어:

### Leader Election

```
Node A
Node B
Node C

Term 3

Leader: A
```

버튼:

```
[ Kill Leader ]
```

그러면:

```
A ✕

B → Candidate
C → Candidate

RequestVote
...
```

사용자가 election 과정을 직접 봅니다.

다른 추천 콘텐츠는:

- Raft
- Quorum
- Replication
- Consistent Hashing
- Vector Clock
- Lamport Clock
- Gossip
- Split Brain
- Distributed Lock
- Leader Election
- CAP
- Eventual Consistency

등입니다.

------

# 18. “Why did this happen?” 모드

개인적으로 매우 추천하는 기능입니다.

사용자에게 결과를 먼저 보여줍니다.

```
System

API      CPU 42%
Redis    CPU 21%
DB       CPU 97%

RPS      unchanged

P99
120ms → 4.8s
```

질문:

> 무엇이 일어났다고 생각하십니까?

사용자가 조사합니다.

힌트:

```
[ View Metrics ]
[ View Logs ]
[ View Trace ]
[ View Architecture ]
```

즉 **개념 → 문제**가 아니라 **현상 → 원리** 방향으로 학습합니다.

이를 `Diagnostic Puzzle` 같은 짧은 Learning 콘텐츠로 만들 수 있습니다.

5~10분짜리여서 출퇴근 중에도 가능합니다.

------

# 19. “Predict Before Run”

이 기능은 학습 효과가 좋습니다.

실험 전에 항상 묻습니다.

> Redis TTL을 300초에서 30초로 변경하면 어떻게 될까요?

```
Cache Hit Rate

○ Increase
○ Similar
● Decrease

DB Load

● Increase
○ Similar
○ Decrease
```

그리고:

```
[ Run Simulation ]
```

실제 결과를 보여줍니다.

```
Your Prediction

Cache Hit ↓       ✓
DB Load ↑         ✓
P99 ↑             ✗

Actual

P99
82ms → 340ms
```

이렇게 하면 단순히 결과를 보는 것보다 훨씬 잘 학습됩니다.

------

# 20. Misconception Detection

AI를 여기 활용하면 좋습니다.

사용자가 여러 Drill에서 반복적으로:

> Queue를 넣으면 무조건 성능이 좋아진다.

와 같은 판단을 한다면 Learning에 표시합니다.

```
Potential Misconception

"Queue always improves performance."

최근 3개 Drill에서
비슷한 판단 패턴이 발견되었습니다.

[ Explore ]
```

들어가면 작은 실험을 시킵니다.

```
Without Queue

Latency  100ms

With Queue

Latency  1.2s
Throughput ↑
```

그리고:

> Queue는 throughput과 burst absorption에는 도움이 될 수 있지만 latency를 증가시킬 수 있습니다.

처럼 사용자의 잘못된 mental model을 교정합니다.

------

# 21. Learning과 Drill을 강하게 연결

이게 가장 중요합니다.

Learning 페이지마다:

```
Practice this concept

Micro Lab
5 min

Build Drill
30 min

System Drill
2 hr

Incident Drill
45 min
```

를 제공합니다.

예를 들어:

```
Cache Stampede

Learn
   ↓
Interactive Lab
   ↓
Cache Implementation
   ↓
Product Search Drill
   ↓
Cache Stampede Incident
```

반대로 Drill에서도:

```
You struggled with

Cache Stampede
Connection Pool
Retry Storm

Recommended Learning

[ Cache Stampede Lab ]
[ Connection
```