좋습니다. SysDrill에서는 모니터링을 단순히 Incident 화면에 그래프 몇 개 추가하는 수준으로 보면 아깝습니다.

**Observability 자체를 하나의 핵심 학습 영역이자 Drill의 조작 인터페이스**로 만드는 것이 좋습니다. 사용자가 실제 운영 환경처럼 `Metrics → Logs → Traces → Events → Architecture → Hypothesis`를 오가며 시스템 상태를 추론하도록 만드는 것입니다.

핵심 구조는 다음을 권합니다.

```
                    SysDrill
                       │
                 Mission Control
                       │
       ┌───────────────┼────────────────┐
       │               │                │
    System          Service          Business
     Health          Health           Health
       │               │                │
 Metrics / Logs / Traces / Events / Profiles
       │
       ▼
  Investigation
       │
       ▼
 Hypothesis → Action → Verify
```

## 1. Drill 상단에 Global Health Bar

모든 Drill에서 항상 시스템 상태가 보여야 합니다.

```
┌────────────────────────────────────────────────────────────────────┐
│ Flash Sale                     ● DEGRADED          00:18:42        │
│                                                                    │
│ RPS        P99         Errors      Availability     Cost           │
│ 42.8K      1.82s ↑     4.21% ↑     99.91% ↓         $2.31/h ↑      │
│                                                                    │
│ SLO  ⚠ 2 breached     Active Alerts 3      Deployments 1           │
└────────────────────────────────────────────────────────────────────┘
```

이것을 **Mission Control Bar**라고 하면 좋습니다.

어떤 탭에 있든 현재 시스템이 정상인지 알 수 있습니다.

상태는:

```
HEALTHY
DEGRADED
CRITICAL
RECOVERING
RECOVERED
```

정도로 통일합니다.

------

# 2. 별도의 `Observe` Workspace

현재 Workspace가

```
Architecture
Code
Traffic
Metrics
Logs
Traces
Terminal
```

이라면 조금 재구성하는 것을 권합니다.

```
Architecture
Code
Traffic
Observe
Changes
Terminal
```

그리고 `Observe` 안에서:

```
Overview | Metrics | Logs | Traces | Dependencies | Profiles
```

를 제공합니다.

Metrics/Logs/Traces를 최상위 탭으로 각각 분리하면 탭이 지나치게 많아집니다.

**Observe라는 하나의 조사 공간**으로 묶는 편이 UX가 좋습니다.

------

# 3. Overview Dashboard

Observe에 처음 들어오면 Grafana 같은 전체 현황 화면을 제공합니다.

```
OBSERVE                                         Last 15m ▼

┌─ Golden Signals ────────────────────────────────────────────┐
│                                                            │
│ Requests        Errors          P99            Saturation   │
│ 42.8K/s         4.2% ↑          1.82s ↑         91% ↑       │
│ ▁▂▃▅▇██         ▁▁▂▃▆██         ▁▂▃▄▆██         ▂▃▅▇███     │
└────────────────────────────────────────────────────────────┘

┌─ Service Health ───────────┐ ┌─ Active Alerts ─────────────┐
│ API Gateway       ● 99.9% │ │ CRITICAL DB Connections     │
│ Product API       ● 99.4% │ │ WARN Redis Hit Rate         │
│ Redis             ◐ 92.1% │ │ WARN Payment P99            │
│ PostgreSQL        ● 97.8% │ │                             │
│ Kafka             ● 99.9% │ │                             │
└────────────────────────────┘ └─────────────────────────────┘

┌─ Latency ──────────────────┐ ┌─ Recent Changes ────────────┐
│ P50   82ms                 │ │ 14:02 payment-api v2.4     │
│ P95  680ms                 │ │ 13:58 Redis TTL changed    │
│ P99  1.82s                 │ │ 13:41 Kafka config         │
└────────────────────────────┘ └─────────────────────────────┘
```

여기서 중요한 것은 **처음부터 원인을 알려주지 않는 것**입니다.

Overview는 증상만 보여줘야 합니다.

------

# 4. Golden Signals

기본 Dashboard는 Google SRE식 Golden Signals 개념으로 통일하기 좋습니다.

### Latency

```
P50
P95
P99
```

### Traffic

```
RPS
Requests/min
Messages/sec
```

### Errors

```
5xx
Timeout
Failure Rate
```

### Saturation

```
CPU
Memory
Connections
Queue
Thread Pool
```

Drill 초급에서는 기본 dashboard를 제공하고 고급에서는 사용자가 직접 구성하도록 합니다.

------

# 5. Service Map

Architecture와 Observability를 연결해야 합니다.

```
                       12K RPS
                         ↓

                   API Gateway
                    ● 12ms
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼

         Product API            User API
          ⚠ 820ms                ● 42ms
              │
        ┌─────┴──────┐
        │            │
        ▼            ▼

      Redis       PostgreSQL
     ⚠ 540ms        ● 180ms
```

연결선 자체에:

```
RPS
latency
error %
```

를 표시할 수 있습니다.

사용자가 한눈에 dependency path를 봅니다.

하지만 여기서도 **원인을 자동으로 빨간색으로 찍어주는 수준까지 가면 안 됩니다.**

증상을 보여주되 판단은 사용자가 해야 합니다.

------

# 6. RED / USE Method 지원

서비스 모니터링에는 RED:

```
Rate
Errors
Duration
```

인프라에는 USE:

```
Utilization
Saturation
Errors
```

를 적용할 수 있습니다.

예를 들어 API 노드를 클릭하면:

```
Product API

RED

Rate
12,821 RPS

Errors
3.82%

Duration
P99 820ms
```

DB를 클릭하면:

```
PostgreSQL

USE

Utilization
CPU 78%

Saturation
Connections 498 / 500

Errors
Connection Timeout 182/min
```

이 자체가 Observability 학습이 됩니다.

------

# 7. Metrics Explorer

고급 사용자는 직접 Metric을 조사해야 합니다.

```
Metrics Explorer

Metric

postgres_connections_active

Filters

service = checkout
region  = ap-northeast-2

Aggregation

AVG

Group By

instance

────────────────────────────

500 │              ┌──────────
400 │          ┌───┘
300 │      ┌───┘
200 │   ───┘
100 │
    └──────────────────────────
     14:00                14:20
```

지원 기능:

- Filter
- Group By
- Aggregation
- Rate
- Percentile
- Compare
- Overlay
- Time range

정도가 좋습니다.

PromQL 자체를 지원하는 **Advanced Mode**도 나중에는 매우 좋습니다.

------

# 8. Metric Correlation

두 지표를 겹쳐볼 수 있어야 합니다.

```
Compare Metrics

Cache Hit Rate
──────────────

DB CPU
──────────────
```

결과:

```
14:04

Cache Hit
92% → 31%

DB CPU
42% → 97%
```

사용자가:

> Cache miss 증가와 DB 부하가 관련 있겠구나.

라고 스스로 추론합니다.

------

# 9. Logs Explorer

실제 로그 도구처럼 만듭니다.

```
LOGS                                      LIVE ●

Query

service:product-api level:error

────────────────────────────────────────────────

14:03:42 ERROR Redis timeout
service=product-api
trace_id=82af...

14:03:43 WARN Cache fallback
key=product:82391

14:03:44 ERROR DB connection timeout
pool=primary
active=500
```

필수 기능은:

```
Search
Filter
Live tail
Level
Service
Trace ID
Correlation ID
Structured Fields
```

입니다.

------

# 10. Log Context

로그 한 줄을 누르면:

```
View Context

5 lines before
5 lines after
```

를 제공합니다.

그리고:

```
[ View Trace ]

[ View Service ]

[ View Related Metrics ]
```

로 연결합니다.

Observability에서 중요한 것은 **도구 간 이동**입니다.

------

# 11. Trace Explorer

```
TRACE

GET /checkout                         2.82s

API Gateway        █ 12ms

Checkout API       █████████████████████████ 2.79s

 ├─ Redis          ██████████ 820ms
 │
 ├─ Payment API    █████ 420ms
 │
 └─ PostgreSQL     ███████████████ 1.42s
```

Span을 클릭하면:

```
PostgreSQL

Duration
1.42 sec

Query

SELECT ...
FROM orders ...

Rows
128K

Trace Tags

db.instance=orders-primary
region=ap-northeast-2
```

그리고:

```
[ View DB Metrics ]

[ View Logs ]

[ View Query ]
```

로 이동합니다.

------

# 12. Metrics → Logs → Trace 연결

이 UX는 반드시 구현하는 것을 권합니다.

예:

Metrics에서:

```
P99 spike

14:04 ~ 14:12

[ Investigate ]
```

클릭하면 해당 시간 범위로 Logs가 자동 필터링됩니다.

```
Logs

Time
14:04 ~ 14:12
```

로그에서 trace_id를 누르면 Trace로 갑니다.

즉:

```
Metric anomaly
      ↓
Logs
      ↓
Trace
      ↓
Dependency
      ↓
Root Cause Hypothesis
```

라는 실제 조사 workflow를 훈련합니다.

------

# 13. Change Overlay

모니터링 차트에 배포/설정 변경을 표시하면 굉장히 좋습니다.

```
P99 Latency

2s │                 ╭──────
   │                ╱
1s │          │    ╱
   │──────────│───╯
              ▲
          Deploy v2.4
```

다른 이벤트:

```
▲ Deploy
◆ Config Change
● Feature Flag
■ Scaling
⚡ Incident
```

클릭하면 변경 내용을 볼 수 있습니다.

이것만으로도 실제 장애 조사 느낌이 크게 올라갑니다.

------

# 14. Alert Center

별도의 Alert UI도 필요합니다.

```
ALERTS

● CRITICAL

DB Connection Pool Saturation

Started
14:04:32

Duration
8m 12s

Current
99.6%

Threshold
> 90% for 2m

Affected

checkout-api
orders-api

[ Investigate ]
```

단순 Alert 목록이 아니라 **Alert → Investigation**의 시작점입니다.

------

# 15. 사용자가 Alert Rule을 직접 작성

Learning과 Drill을 연결할 수 있습니다.

```
Create Alert

Metric

http_error_rate

Condition

> 5%

For

[ 5 ] minutes

Severity

Critical
```

그리고 Incident 종료 후 평가합니다.

```
Incident started
14:02

Alert fired
14:09

Detection Delay
7m

⚠ Alert was too slow.
```

또는:

```
False Alerts

18 / day

⚠ Threshold too sensitive
```

즉 **모니터링 설계 자체가 평가 대상**이 됩니다.

------

# 16. SLO Dashboard

별도의 Reliability 화면을 둘 가치가 있습니다.

```
SERVICE LEVEL

Checkout

Availability

Target
99.95%

Current
99.91%

Error Budget

███████████░░░░░░

Remaining
32%

Burn Rate

4.2× ⚠
```

여기에:

```
1h burn
6h burn
24h burn
```

을 보여줄 수 있습니다.

고급 Reliability Drill에서는 multi-window burn-rate alert까지 학습할 수 있습니다.

------

# 17. Business Metrics도 반드시 포함

실제 운영에서 CPU만 보는 것은 부족합니다.

예를 들어 이커머스:

```
BUSINESS HEALTH

Checkout Success

98.2%
   ↓ 4.1%

Payments / min

8,420
   ↓ 18%

GMV / min

₩82M
   ↓ 21%

Cart Abandonment

12%
   ↑ 7%
```

기술 지표는 정상인데 비즈니스 지표만 깨지는 장애도 만들 수 있습니다.

예:

```
CPU       Normal
Memory    Normal
Latency   Normal
Errors    Normal

BUT

Payment Conversion
82% → 61%
```

원인:

> 특정 결제 수단의 비즈니스 로직 오류

이런 시나리오는 상당히 현실적입니다.

------

# 18. Synthetic Monitoring

외부 사용자 관점 모니터링도 추가합니다.

```
Synthetic Checks

Seoul

Checkout
● 182ms

Tokyo
● 210ms

Singapore
⚠ 1.8s

Virginia
● 320ms
```

Multi-region Drill에서 유용합니다.

------

# 19. Dependency Health

외부 서비스도 모니터링합니다.

```
DEPENDENCIES

Stripe
● Healthy

Email Provider
⚠ Degraded

Redis
● Healthy

Kafka
● Healthy

Shipping API
🔴 Down
```

다만 실제 원인이 외부 dependency인지 아닌지 사용자가 판단해야 합니다.

------

# 20. Infrastructure View

Application View와 Infrastructure View를 분리합니다.

```
Infrastructure

Cluster
 ├ Node A   CPU 42%
 ├ Node B   CPU 44%
 └ Node C   CPU 97% ⚠

Pods

checkout-1   ●
checkout-2   ●
checkout-3   ⚠

DB

primary      ●
replica-1    ●
replica-2    ⚠ lag
```

이렇게 하면 Kubernetes/Cloud 운영 Drill로 확장할 수 있습니다.

------

# 21. Database Monitoring

DB는 별도의 상세 화면이 필요할 정도입니다.

```
PostgreSQL

Queries/sec
12K

Connections
498 / 500 ⚠

P99 Query
820ms

Lock Wait
42

Replication Lag
2.8 sec
```

하단:

```
Top Queries

Query               Calls      P99

SELECT orders...    82K        1.8s
UPDATE inventory    12K        820ms
```

그리고:

```
Slow Queries
Locks
Connections
Replication
Indexes
```

탭을 제공합니다.

------

# 22. Kafka / Queue Monitoring

```
Kafka

Throughput
82K msg/s

Consumer Lag
1.8M ⚠

Partitions
32

Consumers
8

Oldest Message
18m
```

Consumer Group 상세:

```
Partition   Lag

0           12K
1           18K
2           620K ⚠
3           14K
```

여기서 특정 partition skew를 발견하게 만들 수 있습니다.

------

# 23. Cache Monitoring

```
Redis

Hit Rate
91% → 42% ⚠

Memory
82%

Evictions
18K/min

Hot Keys

product:38291
42K req/s
```

Cache Stampede, Hot Key, Eviction Storm 학습과 바로 연결됩니다.

------

# 24. Profiling까지 확장

Senior/Performance Drill에서는:

```
Profiles

CPU
Memory
Allocations
Lock
```

Flame Graph까지 제공할 수 있습니다.

예:

```
checkout()

 ├─ validate()       4%
 ├─ calculate()     12%
 └─ serialize()     62% ⚠
```

이를 통해:

> 서버 CPU가 높은데 infrastructure 문제가 아니라 코드의 serialization이 병목

같은 문제를 만들 수 있습니다.

------

# 25. Monitoring Setup Phase

특히 추천합니다.

Drill을 시작할 때 모든 Dashboard를 완성해서 주지 않습니다.

사용자가 Production 배포 전에:

```
Production Readiness

□ Metrics configured
□ Dashboard created
□ Alerts configured
□ SLO defined
□ Logs structured
□ Tracing enabled
□ Runbook created
```

를 준비합니다.

관측성을 제대로 안 만들었다면 Incident 때 실제로 불리해집니다.

예:

```
Trace

No data

Tracing was not enabled
during deployment.
```

이게 중요합니다.

> **관측성을 준비하지 않은 결과를 실제 장애에서 체험하게 하는 것**입니다.

------

# 26. Observability Cost

모니터링도 공짜가 아닙니다.

```
Observability Cost

Metrics
$120 / month

Logs
$840 / month ⚠

Traces
$1,420 / month ⚠

Total
$2,380 / month
```

사용자가:

```
Trace Sampling

100%
```

으로 설정했다면 비용이 높습니다.

```
10%
```

으로 줄이면:

```
Cost ↓

But

Diagnostic Coverage ↓
```

즉 sampling과 cardinality까지 배울 수 있습니다.

------

# 27. Cardinality Incident

고급 Observability Drill로 재미있습니다.

사용자가 Metric label에:

```
user_id
```

를 넣습니다.

잠시 후:

```
Metric Cardinality

12.8M series

Monitoring backend

CPU       98%
Memory    94%

Cost
+$4,200/day
```

**모니터링 시스템 자체가 장애를 일으키는 시나리오**입니다.

Senior 레벨에서 상당히 실용적입니다.

------

# 28. Monitoring Quality Score

Drill 종료 후 별도 평가합니다.

```
OBSERVABILITY

Coverage          82
Alert Quality     64
SLO Design        91
Dashboard         76
Logging           88
Tracing           42
Cost Efficiency   61

Overall
72
```

하지만 단순 점수 아래에 반드시 근거를 보여�