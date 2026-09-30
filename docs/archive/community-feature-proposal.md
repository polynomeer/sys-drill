좋습니다. SysDrill의 Community는 일반적인 게시판으로 만들기보다 **“다른 엔지니어가 어떻게 판단하고 해결했는지를 학습하는 공간”**으로 정의하는 것이 좋습니다.

Drills가 **실전 경험**, Learning이 **지식과 mental model 형성**이라면 Community는 **집단의 다양한 접근법을 비교하고 검증하는 공간**이 되는 구조입니다.

> **Drill에서 해결 → 결과 공유 → 다른 접근법 비교 → 토론 → 개선 → 다시 Drill**

이 루프가 핵심입니다.

------

# 1. Community의 역할부터 재정의

현재:

```
Community
└── GitHub Issues
```

개선:

```
Community

├── Feed
├── Drill Discussions
├── Solutions
├── Incident Reviews
├── Architecture Reviews
├── Challenges
├── Study Groups
└── Open Source / GitHub
```

다만 메뉴를 실제로 8개 탭으로 만들 필요는 없습니다.

UI에서는 다음 정도가 적당합니다.

```
Community

[ Feed ] [ Discussions ] [ Reviews ] [ Challenges ]

                 Search...

Trending
────────────────────────────

Latest Discussions
────────────────────────────

Popular Solutions
────────────────────────────

Incident Reviews
────────────────────────────
```

그리고 GitHub Issues는 Community의 일부인 **Project Feedback** 정도로 내려가는 것이 좋습니다.

------

# 2. Community Home

Community 첫 화면부터 일반 게시판처럼 보이면 안 됩니다.

추천 구조는 다음과 같습니다.

```
Community

Learn how other engineers think.

[ Ask Question ] [ Share Solution ]


Trending this week
─────────────────────────────────

🔥 Cache Stampede
   128 discussions

🔥 Kafka Consumer Lag
   94 discussions

🔥 Flash Sale Architecture
   82 solutions


From your Drills
─────────────────────────────────

선착순 쿠폰 시스템

You completed this Drill.

238 engineers also completed it.

[ Compare Solutions ]


Recommended Discussion
─────────────────────────────────

"Redis Distributed Lock이 정말 필요한가요?"

32 replies · Senior
```

**사용자의 Drill 기록과 Community를 연결**하는 것이 중요합니다.

------

# 3. Drill Discussion

모든 Drill에는 자동으로 전용 Community가 생기도록 합니다.

예:

```
Flash Sale / 선착순 쿠폰

[ Overview ]
[ My Run ]
[ Community ]
```

Community를 누르면:

```
Flash Sale Community

Completed
12,482 engineers


Discussions

🔥 Redis Lock vs Lua Script
   128 replies

🔥 Queue를 언제 도입해야 할까요?
   83 replies

🔥 DB만으로 처리할 수 있을까요?
   41 replies


Solutions

Most Discussed
Most Recent
Different From Mine
```

이렇게 하면 Community가 제품 밖의 게시판이 아니라 **Drill 경험의 일부**가 됩니다.

------

# 4. Solution Sharing

SysDrill Community에서 가장 중요한 기능 중 하나로 만들 수 있습니다.

Drill 완료 후:

```
Share your solution?

[ Publish Solution ]
```

누르면 시스템이 자동으로 작성 초안을 만듭니다.

```
Flash Sale System

by @user


Architecture

Client
   ↓
Gateway
   ↓
Coupon API
   ↓
Redis
   ↓
Kafka
   ↓
DB


Key Decisions

• Redis Lua Script
• Kafka async processing
• DB unique constraint


Results

Peak RPS       82K
P99            182ms
Availability   99.97%

Incident

2 / 3 recovered


Skills

Concurrency    87
Scalability    91
Reliability    76
```

사용자는 여기에 설명만 추가하면 됩니다.

즉 **SysDrill이 자동으로 Engineering Write-up을 생성해주는 구조**입니다.

------

# 5. Architecture Comparison

이 기능은 꽤 강력한 차별화 포인트가 될 수 있습니다.

다른 사람의 Solution을 볼 때 단순 게시글만 보여주지 않습니다.

```
Compare Architecture

        YOURS                 @alex

        Redis                 Redis
          │                     │
        Kafka                 Kafka
          │                   ↙   ↘
         DB                 DB   Worker
```

그리고 차이를 자동 분석합니다.

```
Architecture Differences

Your Design
────────────────
Redis Lua Script
Single Kafka Consumer Group
DB Primary

Alex's Design
────────────────
Redis Atomic Counter
Partitioned Consumer
DB + Replica


Major Difference

Queue Processing Strategy
```

AI가 설명합니다.

> 두 설계의 가장 큰 차이는 쿠폰 발급 결과를 언제 확정하는지입니다.

이것은 상당히 좋은 학습 경험입니다.

------

# 6. “Different From Mine”

Community Solution 필터에 반드시 넣어볼 만합니다.

보통 커뮤니티에서는 인기순만 봅니다.

SysDrill에서는:

```
Sort

○ Popular
○ Expert
○ Recent
● Different From Mine
```

를 제공합니다.

예를 들어 사용자가 Redis를 사용했다면:

> Redis를 사용하지 않은 설계

를 우선 보여줍니다.

사용자가 Kafka를 사용했다면:

> Queue를 사용하지 않은 접근

을 보여줍니다.

이 기능은 **confirmation bias를 줄이는 데 유용**합니다.

------

# 7. Architecture Review

별도의 Review 공간을 두는 것도 좋습니다.

사용자가 자신의 설계를 올립니다.

```
Architecture Review

Payment System

Scale
10K TPS

Requirements

99.99% availability
No duplicate payments


[ Architecture Diagram ]


Question

"Kafka를 Payment와 Ledger 사이에
넣는 것이 적절한지 고민입니다."

[ Request Review ]
```

다른 사용자들이 다이어그램에 직접 코멘트를 남깁니다.

```
              Kafka
                │
                ▼
              Ledger
                ↑
                │
             💬 @kim

"Kafka 장애 시 Payment 상태는
어떻게 관리하시나요?"
```

Figma/GitHub PR 리뷰처럼 **Architecture Node 단위 댓글**을 제공하는 것입니다.

------

# 8. Review Template

댓글 품질을 높이려면 자유 댓글만 두면 안 됩니다.

Review 버튼:

```
Review

[ Question ]
[ Risk ]
[ Suggestion ]
[ Alternative ]
[ Trade-off ]
```

예:

```
⚠ Risk

Redis가 SPOF로 보입니다.

현재 구조에서 Redis 장애 시
coupon issuance 전체가 중단될 수 있습니다.
```

이렇게 하면

> “좋네요”

같은 댓글보다 엔지니어링 토론을 유도할 수 있습니다.

------

# 9. Incident Review

이건 SysDrill에 특히 잘 맞습니다.

사용자가 Incident Drill을 완료하면 anonymized timeline을 공유할 수 있습니다.

```
Incident Review

Cache Stampede #2841


Timeline

14:02 Alert

14:04
YOU
Scale API
8 → 16

14:06
DB CPU
98%

14:08
YOU
Check Redis

14:11
Increase TTL

14:14
Recovered
```

질문:

> 여러분이라면 14:04에 무엇을 했을까요?

Community가 특정 시점에 댓글을 답니다.

```
14:04

💬 @engineerA

API CPU가 40%였기 때문에
저라면 API scale보다 DB dependency부터
확인했을 것 같습니다.
```

이건 일반 개발 커뮤니티에서 하기 어려운 형태입니다.

------

# 10. Fork My Run

더 발전시키면 아주 재미있는 기능이 됩니다.

다른 사용자의 Incident Replay를 보다가:

```
14:04

User chose:
Scale API

[ Fork From Here ]
```

를 누릅니다.

그 시점의 Simulation State를 복제합니다.

그리고 나는 다른 행동을 합니다.

```
14:04

Instead:

[ Inspect Redis ]
```

Simulation이 다시 진행됩니다.

결과:

```
Original

MTTR
14m 32s

Your Fork

MTTR
7m 18s
```

이것은 SysDrill의 Simulation Engine을 Community까지 확장하는 기능입니다.

**상당히 강한 기능 후보입니다.**

------

# 11. “What Would You Do?”

짧은 커뮤니티 콘텐츠도 만들 수 있습니다.

매일 하나의 상황을 제공합니다.

```
What Would You Do? #128

Friday 18:03

Payment API

RPS        normal
CPU        42%
P99        3.8s ↑
DB CPU     38%
Redis      normal

Deployment
17:58 payment-api v3.82


What do you check first?

[ Write your approach ]
```

답변 후에만 다른 사람의 답을 보여줍니다.

```
Community

42%  Trace
27%  Deployment Diff
18%  Logs
8%   DB
5%   Scale API
```

그리고 토론합니다.

하루 5분짜리 Community engagement가 됩니다.

------

# 12. Architecture Challenge

주간 Challenge를 운영할 수 있습니다.

```
Weekly Architecture Challenge

Design YouTube Comments


Requirements

500M DAU
100K comments/sec
Global
Spam filtering
Real-time ranking


Deadline
3 days

Participants
2,842

[ Join Challenge ]
```

제출 후 Community에서 서로 비교합니다.

하지만 단순 인기투표보다는:

```
Community Feedback

Scalability
Reliability
Simplicity
Cost Awareness
Explanation
```

같은 **구체적인 review dimension**을 제공하는 것이 좋습니다.

------

# 13. Incident Challenge

SysDrill만의 Community 이벤트로 만들 수 있습니다.

```
INCIDENT WAR GAME

"The Friday Deployment"

Unknown Incident

Duration
30 min

Participants
1,823


Rules

No hints
Production mode
Hidden failures


[ Enter War Game ]
```

종료 후에는:

```
Community Debrief
```

가 열립니다.

여기서 사람들이

- 어떤 신호를 먼저 봤는지
- 어떤 가설을 세웠는지
- 어떤 대응을 했는지

토론합니다.

실제 SRE GameDay에 가까운 경험입니다.

------

# 14. Community Post 타입을 명확히 분리

일반 게시판처럼 모든 글을 한 종류로 만들지 않는 것이 좋습니다.

```
Post Type

💬 Question
🏗 Architecture
🔧 Implementation
🔥 Incident
📊 Benchmark
📝 Postmortem
💡 Insight
```

각 타입마다 입력 UI도 달라집니다.

예를 들어 `Incident` 글에는:

```
Impact
Timeline
Root Cause
Mitigation
Prevention
```

템플릿을 자동 제공합니다.

Architecture에는:

```
Requirements
Scale
Diagram
Decisions
Trade-offs
Questions
```

를 제공합니다.

------

# 15. Engineering Postmortem 공유

사용자가 실제 경험을 익명화해서 공유할 수도 있습니다.

```
Community Postmortem

Kafka Consumer Lag

Impact

Notification delay
18 minutes

Scale

2.1M messages

Root Cause

Slow downstream API

Mitigation

Consumer scaling
Rate limiting

Lessons

...
```

SysDrill Failure Encyclopedia와 연결합니다.

```
Related Learning

→ Consumer Lag
→ Backpressure
→ Retry Storm
```

Community 콘텐츠가 Learning 콘텐츠로 연결됩니다.

------

# 16. 답변 품질을 Reputation과 연결

단순 좋아요만 사용하지 않는 편이 좋습니다.

예를 들어:

```
Helpful
Insightful
Good Trade-off
Good Explanation
```

등으로 평가합니다.

프로필에는:

```
Community Reputation

Architecture Review     842
Incident Analysis       612
Distributed Systems     381
Database                294
```

처럼 **분야별 reputation**을 둡니다.

그러면 특정 분야에서 실제로 좋은 답변을 하는 사용자를 찾기 쉬워집니다.

------

# 17. Expert / Verified Engineer

나중에는 전문가 프로그램으로 확장할 수 있습니다.

```
@alice

Senior SRE

Expertise

Reliability
Observability
Kubernetes

✓ SysDrill Verified
```

다만 직급이나 회사 이름만으로 Expert를 지정하기보다는 **SysDrill 내 실제 활동과 리뷰 품질**도 함께 보는 것이 좋습니다.

------

# 18. Follow 기능

사람뿐 아니라 **Topic을 Follow**하는 것이 중요합니다.

```
Following

People
@alice
@bob

Topics
Kafka
PostgreSQL
Incident Response
Distributed Systems

Drills
Flash Sale
Payment System
```

그러면 Community Feed가 개인화됩니다.

------

# 19. Community Feed

최종적으로 Home은 이런 느낌이 좋습니다.

```
Community

[ For You ] [ Following ] [ Trending ]


🔥 Incident Review

Cache Stampede

"API를 Scale-out 했더니
오히려 DB가 죽었습니다."

128 comments


🏗 Architecture Review

Payment System

Kafka between
Payment and Ledger?

42 reviews


💡 Solution

Flash Sale

No Redis approach

83 helpful


⚔ Weekly Challenge

Design Slack

1,284 participants
```

일반 SNS Feed와 다르게 **모든 콘텐츠가 엔지니어링 학습 객체**여야 합니다.

------

# 20. Spoiler Control

이 기능은 반드시 필요합니다.

사용자가 아직 Drill을 완료하지 않았는데 Community에서 정답을 봐버리면 학습 경험이 망가집니다.

따라서:

```
Flash Sale Community

⚠ You haven't completed this Drill.

Discussion contains solution spoilers.

[ Enter Without Solutions ]

[ Reveal Solutions ]
```

또는 콘텐츠 단위로:

```
🔒 Solution hidden

Complete Phase 4 to unlock.
```

처럼 처리합니다.

------

# 21. Community와 Learning 연결

예를 들어 Kafka Consumer Lag 토론을 읽고 있습니다.

오른쪽에:

```
Related Learning

Consumer Lag
12 min

Backpressure
18 min

Consumer Group
15 min
```

를 제공합니다.

반대로 Learning 페이지에는:

```
Community Discussions

"Consumer를 늘렸는데
lag가 안 줄어듭니다."

42 replies
```

를 표시합니다.

------

# 22. Community와 Drill 연결

Community에서 좋은 글을 읽으면 바로 실행해볼 수 있어야 합니다.

예:

> “Timeout을 늘리는 것이 오히려 장애를 악화시킬 수 있습니다.”

아래에:

```
Try it yourself

Timeout Cascade Lab

10 min

[ Launch Lab ]
```

이 있습니다.

즉,

```
Community
     ↓
Learning
     ↓
Lab
     ↓
Drill
```

로 이동합니다.

반대 방향도 연결합니다.

```
Drill 실패
   ↓
Community
   ↓
다른 접근 확인
   ↓
Learning
   ↓
Retry
```

------

# 23. GitHub Issues는 이렇게 남기면 됩니다

현재 GitHub 링크를 없앨 필요는 없습니다.

역할을 명확하게 바꿉니다.

```
Community
│
├─ Discussions
├─ Solutions
├─ Reviews
├─ Challenges
│
└─ SysDrill Project
      ├─ Feature Requests
      ├─ Bug Reports
      ├─ Scenario Requests
      └─ GitHub ↗
```

GitHub는 **제품 개발 커뮤니티**, SysDrill Community는 **학습 커뮤니티** 역할을 하게 됩니다.

------

# 24. 특히 추천하는 기능 5개

기능이 많지만 SysDrill의 정체성을 가장 강하게 만드는 것은 다음 다섯 가지입니다.

| 기능                    | 가치                                             |
| ----------------------- | ------------------------------------------------ |
| **Solution Compare**    | 같은 문제의 서로 다른 아키텍처를 비교            |
| **Architecture Review** | 다이어그램에 직접 엔지니어링 리뷰                |
| **Incident Review**     | 장애 대응 판단을 타임라인 기준으로 토론          |
| **Fork My Run**         | 다른 사람의 장애 대응 시점에서 시뮬레이션을 분기 |
| **Weekly War Game**     | Community 전체가 같은 Incident를 해결            |

특히 **Fork My Run**은 상당히 중요하게 보고 싶습니다.

예를 들어 다른 사용자의 Replay에서:

```
14:07

DB CPU 94%
Redis hit rate 21%

User chose:

Scale DB

          ↓

[ Fork From Here ]
```

를 누르면 **그 순간의 전체 시스템 state가 복제**됩니다.

나는 Cache를 조사합니다.

결과가 달라집니다.

이렇게 되면 Community에서 토론하는 것을 넘어,

> **“네가 말한 방법이 더 낫다고? 그럼 같은 상태에서 직접 해봐.”**

가 가능해집니다.

이것은 SysDrill의 `Simulation Engine + Community`를 결합한 기능이고, 일반 개발자 커뮤니티와 매우 다른 경험을 만들 수 있습니다.

결국 세 메뉴의 역할도 아주 명확해집니다.

```
                 SysDrill

        ┌──────────┼──────────┐
        │          │          │

     Learning    Drills    Community

       KNOW        DO        SHARE
        │          │          │
     이해한다    경험한다     비교한다
        │          │          │
     Experiment  Simulate    Discuss
        │          │          │
     Mental      Engineering Collective
     Model       Judgment    Intelligence

        └──────────┼──────────┘

             Skill Graph
```

**Learning에서 원리를 이해하고 → Drills에서 실제로 판단하고 → Community에서 다른 엔지니어의 판단과 비교하고 → 다시 Learning/Drill로 돌아오는 구조**로 만들면 SysDrill 전체 제품 구조가 상당히 단단해집니다.