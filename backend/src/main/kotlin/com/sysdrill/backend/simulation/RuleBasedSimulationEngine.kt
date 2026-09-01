package com.sysdrill.backend.simulation

import kotlin.math.max

/**
 * Pure, deterministic simulation math (docs/ARCHITECTURE.md §6). No I/O, no
 * Spring — everything here is `NextState = f(...)`. One incident template
 * per scenario domain (PLAN.md step 4 built "coupon"; step 11 adds
 * "notification" and "product-browsing" using the same utilization-band
 * philosophy) rather than one generic engine reading scenario-defined
 * parameters — three concrete templates turned out simple enough that the
 * generalization wouldn't have paid for itself yet.
 *
 * This was a plain `object SimulationEngine` through Phase 1/2 — PLAN.md
 * step 21 extracted the [SimulationEngine] interface so a real-infra
 * alternative ([com.sysdrill.backend.simulation.realinfra.RealInfraCouponEngine])
 * could exist alongside it without touching any of these formulas.
 */
object RuleBasedSimulationEngine : SimulationEngine {

    const val DOMAIN_COUPON = "coupon"
    const val DOMAIN_NOTIFICATION = "notification"
    const val DOMAIN_PRODUCT_BROWSING = "product-browsing"
    const val DOMAIN_PAYMENT = "payment"
    const val DOMAIN_RESERVATION = "reservation"
    const val DOMAIN_BATCH_SETTLEMENT = "batch-settlement"
    const val DOMAIN_AUTOSCALING = "autoscaling"

    /**
     * utilization = incoming_load / max_capacity bands, per docs/ARCHITECTURE.md §6:
     * 0~60% 안정 / 60~80% latency 증가 / 80~95% p95·p99 급등 / 95~100% error 증가 / 100%+ timeout·drop.
     * Shared by every domain below — the bands are a property of *any* saturating
     * resource, not something specific to coupon's DB.
     */
    fun latencyMultiplier(utilization: Double): Double = when {
        utilization < 0.6 -> 1.0
        utilization < 0.8 -> 1.5
        utilization < 0.95 -> 3.0
        utilization < 1.0 -> 5.0
        else -> 8.0
    }

    fun errorRateFor(utilization: Double): Double = when {
        utilization < 0.6 -> BASELINE_ERROR_RATE
        utilization < 0.8 -> 0.005
        utilization < 0.95 -> 0.02
        utilization < 1.0 -> 0.10
        else -> 0.30
    }

    private const val BASELINE_ERROR_RATE = 0.001

    override fun computeState(session: SimulationSessionState): SystemState = when (session.domain) {
        DOMAIN_COUPON -> Coupon.computeState(session)
        DOMAIN_NOTIFICATION -> Notification.computeState(session)
        DOMAIN_PRODUCT_BROWSING -> ProductBrowsing.computeState(session)
        DOMAIN_PAYMENT -> Payment.computeState(session)
        DOMAIN_RESERVATION -> Reservation.computeState(session)
        DOMAIN_BATCH_SETTLEMENT -> BatchSettlement.computeState(session)
        DOMAIN_AUTOSCALING -> Autoscaling.computeState(session)
        else -> error("Unknown simulation domain: ${session.domain}")
    }

    override fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState =
        when (current.domain) {
            DOMAIN_COUPON -> current.copy(traits = Coupon.applyAction(current.traits, action))
            DOMAIN_NOTIFICATION -> current.copy(traits = Notification.applyAction(current.traits, action))
            DOMAIN_PRODUCT_BROWSING -> current.copy(traits = ProductBrowsing.applyAction(current.traits, action))
            DOMAIN_PAYMENT -> current.copy(traits = Payment.applyAction(current.traits, action))
            DOMAIN_RESERVATION -> current.copy(traits = Reservation.applyAction(current.traits, action))
            DOMAIN_BATCH_SETTLEMENT -> current.copy(traits = BatchSettlement.applyAction(current.traits, action))
            DOMAIN_AUTOSCALING -> current.copy(traits = Autoscaling.applyAction(current.traits, action))
            else -> error("Unknown simulation domain: ${current.domain}")
        }

    /** docs/PRD.md §8.1 — Redis latency 증가 → DB write hotspot (PLAN.md step 4). */
    private object Coupon {
        private const val BASELINE_TRAFFIC_RPS = 300.0
        private const val INCIDENT_TRAFFIC_MULTIPLIER = 20.0 // PRD.md §8.1 꼬리설계: 트래픽 20배
        private const val READ_RATIO = 0.7
        private const val WRITE_RATIO = 0.3

        private const val BASE_DB_READ_CAPACITY_RPS = 3000.0
        private const val BASE_DB_WRITE_CAPACITY_RPS = 1000.0 // at BASELINE_DB_POOL_SIZE
        private const val RATE_LIMIT_CEILING_RPS = 3000.0

        private const val BASE_CACHE_LATENCY_MS = 2.0
        private const val REDIS_DEGRADATION_FACTOR = 15.0 // PRD.md §8.1 워게임: Redis latency 증가
        private const val MAX_USEFUL_TTL_SECONDS = 600.0
        private const val BASELINE_CACHE_HIT_RATIO = 0.95

        private const val BASE_P95_LATENCY_MS = 80.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val baseTraffic = if (session.incidentActive) {
                BASELINE_TRAFFIC_RPS * INCIDENT_TRAFFIC_MULTIPLIER
            } else {
                BASELINE_TRAFFIC_RPS
            }
            val admittedTraffic = if (traits.rateLimitEnabled) minOf(baseTraffic, RATE_LIMIT_CEILING_RPS) else baseTraffic

            val cacheLatencyMs = if (session.incidentActive) {
                BASE_CACHE_LATENCY_MS * REDIS_DEGRADATION_FACTOR
            } else {
                BASE_CACHE_LATENCY_MS
            }
            // Redis is only "degraded" relative to its own baseline; a longer TTL means
            // fewer lock/refresh round-trips are needed, dampening how much an elevated
            // cacheLatencyMs actually costs the effective hit ratio.
            val effectiveCacheHitRatio = if (cacheLatencyMs <= BASE_CACHE_LATENCY_MS * 2) {
                BASELINE_CACHE_HIT_RATIO
            } else {
                val ttlDampening = (traits.cacheTtlSeconds / MAX_USEFUL_TTL_SECONDS).coerceIn(0.0, 1.0)
                BASELINE_CACHE_HIT_RATIO * (0.4 + 0.6 * ttlDampening)
            }

            val readRps = admittedTraffic * READ_RATIO
            val writeRps = admittedTraffic * WRITE_RATIO

            val dbReadRps = readRps * (1 - effectiveCacheHitRatio)
            val dbWriteRps = writeRps
            val dbWriteCapacity = BASE_DB_WRITE_CAPACITY_RPS * (traits.dbPoolSize / DesignTraits.DEFAULT_DB_POOL_SIZE.toDouble())

            val readUtilization = dbReadRps / BASE_DB_READ_CAPACITY_RPS
            val writeUtilization = dbWriteRps / dbWriteCapacity
            val overallUtilization = max(readUtilization, writeUtilization)

            return SystemState(
                trafficRps = admittedTraffic,
                p95LatencyMs = BASE_P95_LATENCY_MS * latencyMultiplier(overallUtilization),
                errorRate = errorRateFor(overallUtilization),
                availability = 1.0 - errorRateFor(overallUtilization),
                dbReadLoad = readUtilization,
                dbWriteLoad = writeUtilization,
                connectionPoolUsage = writeUtilization.coerceAtMost(1.0),
                cacheHitRatio = effectiveCacheHitRatio,
                cacheLatencyMs = cacheLatencyMs,
                queueLag = 0,
                consumerThroughput = 0.0,
                externalDependencyLatencyMs = 0.0,
            )
        }

        /** docs/ARCHITECTURE.md §6.1 "긍정 효과" — the trade-off isn't separately numerically modeled, see [SimulationActionType]'s effect description in SimulationService. */
        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.STRENGTHEN_RATE_LIMIT -> traits.copy(rateLimitEnabled = true)
            SimulationActionType.INCREASE_CACHE_TTL -> traits.copy(cacheTtlSeconds = maxOf(traits.cacheTtlSeconds * 3, 300))
            SimulationActionType.INCREASE_DB_POOL -> traits.copy(dbPoolSize = traits.dbPoolSize + 100)
            else -> error("$action does not apply to the coupon incident")
        }
    }

    /**
     * docs/PRD.md §8.2 — provider(email/SMS/push) timeout 발생 시, 컨슈머가 동기 호출에
     * 묶여 처리량이 붕괴하고(consumerThroughput) 재시도가 몰려(retry storm) backlog가
     * 쌓인다(queueLag). 세 액션은 서로 다른 축을 고친다: circuit breaker는 죽은
     * provider를 기다리지 않게 하고, consumer 증설은 처리 용량을 늘리고, retry backoff
     * 조정은 재시도 폭풍을 줄인다 — 셋을 모두 적용해야 utilization이 회복 구간(<0.6)
     * 으로 돌아온다 (SimulationEngineTest에서 손으로 검증).
     */
    private object Notification {
        private const val BASELINE_EVENT_RATE = 50.0
        private const val INCIDENT_MULTIPLIER = 10.0 // PRD.md §8.2 꼬리설계: 주문량 10배

        private const val BASE_PROVIDER_LATENCY_MS = 20.0
        private const val PROVIDER_DEGRADATION_FACTOR = 15.0 // PRD.md §8.2 워게임: provider timeout
        private const val FAST_FAIL_LATENCY_MS = 10.0 // circuit breaker OPEN: fail fast instead of waiting on a dead provider

        private const val RETRY_STORM_FACTOR = 2.0

        private const val BASE_NOTIFICATION_LATENCY_MS = 300.0 // async delivery baseline, not a sync API call
        private const val BACKLOG_WINDOW_SECONDS = 1.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val incomingRate = if (session.incidentActive) BASELINE_EVENT_RATE * INCIDENT_MULTIPLIER else BASELINE_EVENT_RATE

            val effectiveProviderLatencyMs = when {
                traits.circuitBreakerEnabled -> FAST_FAIL_LATENCY_MS
                session.incidentActive -> BASE_PROVIDER_LATENCY_MS * PROVIDER_DEGRADATION_FACTOR
                else -> BASE_PROVIDER_LATENCY_MS
            }
            val perConsumerThroughput = 1000.0 / effectiveProviderLatencyMs
            val consumerThroughput = traits.consumerCount * perConsumerThroughput

            val retryAmplification = if (session.incidentActive) RETRY_STORM_FACTOR / traits.retryBackoffMultiplier else 0.0
            val effectiveIncomingRate = incomingRate * (1 + retryAmplification)

            val utilization = effectiveIncomingRate / consumerThroughput
            val queueLag = max(0.0, (effectiveIncomingRate - consumerThroughput) * BACKLOG_WINDOW_SECONDS).toLong()

            return SystemState(
                trafficRps = incomingRate,
                p95LatencyMs = BASE_NOTIFICATION_LATENCY_MS * latencyMultiplier(utilization),
                errorRate = errorRateFor(utilization),
                availability = 1.0 - errorRateFor(utilization),
                dbReadLoad = 0.0,
                dbWriteLoad = 0.0,
                connectionPoolUsage = 0.0,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = queueLag,
                consumerThroughput = consumerThroughput,
                externalDependencyLatencyMs = effectiveProviderLatencyMs,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.ADD_CONSUMERS -> traits.copy(consumerCount = traits.consumerCount + 8)
            SimulationActionType.ENABLE_CIRCUIT_BREAKER -> traits.copy(circuitBreakerEnabled = true)
            SimulationActionType.ADJUST_RETRY_BACKOFF -> traits.copy(retryBackoffMultiplier = traits.retryBackoffMultiplier + 4)
            else -> error("$action does not apply to the notification incident")
        }
    }

    /**
     * docs/PRD.md §8.3 — 트래픽 20배가 소수의 hot key에 쏠리며 캐시가 스래싱되고
     * (cacheHitRatio 붕괴), single-flight 없이는 동시에 miss한 요청이 각자 DB를
     * 때려(dogpile) miss 1건이 DB read 여러 건으로 증폭된다. 세 액션이 서로 다른
     * 축을 고친다: 캐시 정책 분리는 hit ratio를, single-flight는 증폭을,
     * read replica는 DB read 용량을 — 셋을 모두 적용해야 회복된다 (SimulationEngineTest).
     */
    private object ProductBrowsing {
        private const val BASELINE_TRAFFIC_RPS = 500.0
        private const val INCIDENT_MULTIPLIER = 20.0 // PRD.md §8.3 꼬리설계: 트래픽 20배

        private const val BASELINE_HIT_RATIO = 0.9
        private const val HOT_KEY_PENALTY_SEVERE = 0.7 // no cache-policy split: hit ratio craters to 0.2
        private const val HOT_KEY_PENALTY_MITIGATED = 0.1 // split: hit ratio only dips to 0.8

        private const val MISS_DOGPILE_AMPLIFICATION = 10.0 // one miss fans out to N DB reads without single-flight
        private const val BASE_DB_READ_CAPACITY_RPS = 2000.0 // per replica unit (0 replicas = 1x this)

        private const val BASE_PRODUCT_LATENCY_MS = 60.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val incomingRps = if (session.incidentActive) BASELINE_TRAFFIC_RPS * INCIDENT_MULTIPLIER else BASELINE_TRAFFIC_RPS

            val cacheHitRatio = when {
                !session.incidentActive -> BASELINE_HIT_RATIO
                traits.cachePolicySplit -> BASELINE_HIT_RATIO - HOT_KEY_PENALTY_MITIGATED
                else -> BASELINE_HIT_RATIO - HOT_KEY_PENALTY_SEVERE
            }
            val missRps = incomingRps * (1 - cacheHitRatio)

            val missAmplification = if (!session.incidentActive || traits.singleFlightEnabled) 1.0 else MISS_DOGPILE_AMPLIFICATION
            val dbReadRps = missRps * missAmplification

            val dbReadCapacity = BASE_DB_READ_CAPACITY_RPS * (1 + traits.readReplicaCount)
            val readUtilization = dbReadRps / dbReadCapacity

            return SystemState(
                trafficRps = incomingRps,
                p95LatencyMs = BASE_PRODUCT_LATENCY_MS * latencyMultiplier(readUtilization),
                errorRate = errorRateFor(readUtilization),
                availability = 1.0 - errorRateFor(readUtilization),
                dbReadLoad = readUtilization,
                dbWriteLoad = 0.0,
                connectionPoolUsage = 0.0,
                cacheHitRatio = cacheHitRatio,
                cacheLatencyMs = 0.0,
                queueLag = 0,
                consumerThroughput = 0.0,
                externalDependencyLatencyMs = 0.0,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.SPLIT_CACHE_POLICY -> traits.copy(cachePolicySplit = true)
            SimulationActionType.ENABLE_SINGLE_FLIGHT -> traits.copy(singleFlightEnabled = true)
            SimulationActionType.ADD_READ_REPLICA -> traits.copy(readReplicaCount = traits.readReplicaCount + 1)
            else -> error("$action does not apply to the product-browsing incident")
        }
    }

    /**
     * docs/PRD.md §8 "주문/결제" — 외부 PG(결제대행사)가 느려지면 주문 생성 시
     * 함께 쓰는 outbox 테이블(트랜잭션 경계를 지키기 위한 안전한 이벤트 발행
     * 패턴)의 처리가 밀리고, 그 backlog가 커넥션 풀을 통해 주문 처리 자체에도
     * 번진다(계단식 장애) — 격리(bulkhead)하지 않으면. PG 응답 유실(partial
     * failure)로 인한 재시도가 idempotency 없이 이루어지면 처리해야 할 유효
     * 부하 자체가 늘어난다. Notification의 "provider 저하 → 처리량 붕괴"와
     * 표면적으로 비슷해 보이지만, 축이 다르다: 여기서는 격리 여부가 "장애가
     * 다른 곳으로 번지는지"를 결정하고, 나머지 두 액션이 backlog 자체를
     * 줄인다 — 세 액션 모두 서로 다른 실패 모드를 겨냥한다(ADR-0010 참고).
     */
    private object Payment {
        private const val BASELINE_ORDER_RPS = 30.0
        private const val BASE_PG_LATENCY_MS = 50.0
        private const val PG_DEGRADATION_FACTOR = 20.0 // PRD.md §8 워게임: 외부 결제 timeout

        private const val PARTIAL_FAILURE_WASTE_FACTOR = 3.0 // idempotency 없이 재시도할 때의 유효 부하 증폭
        private const val BACKLOG_CAPACITY_FOR_FULL_PRESSURE = 50.0
        private const val BASE_POOL_USAGE = 0.2

        private const val BASE_ORDER_LATENCY_MS = 60.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val effectivePgLatencyMs = if (session.incidentActive) {
                BASE_PG_LATENCY_MS * PG_DEGRADATION_FACTOR
            } else {
                BASE_PG_LATENCY_MS
            }
            val dispatcherThroughput = traits.dispatcherWorkers * (1000.0 / effectivePgLatencyMs)

            val retryWasteFactor = if (session.incidentActive && !traits.idempotentPgRetryEnabled) {
                PARTIAL_FAILURE_WASTE_FACTOR
            } else {
                0.0
            }
            val effectiveOrderRps = BASELINE_ORDER_RPS * (1 + retryWasteFactor)
            val outboxBacklog = max(0.0, effectiveOrderRps - dispatcherThroughput)
            val backlogRatio = outboxBacklog / BACKLOG_CAPACITY_FOR_FULL_PRESSURE

            val poolUsageFromWaste = BASE_POOL_USAGE * (1 + retryWasteFactor)
            // Isolating the payment/outbox connection pool (bulkhead) keeps a growing
            // backlog from also degrading order-serving queries that share the same pool.
            val connectionPoolUsage = poolUsageFromWaste + if (traits.paymentPoolIsolated) 0.0 else backlogRatio

            return SystemState(
                trafficRps = BASELINE_ORDER_RPS,
                p95LatencyMs = BASE_ORDER_LATENCY_MS * latencyMultiplier(connectionPoolUsage),
                errorRate = errorRateFor(connectionPoolUsage),
                availability = 1.0 - errorRateFor(connectionPoolUsage),
                dbReadLoad = 0.0,
                dbWriteLoad = 0.0,
                connectionPoolUsage = connectionPoolUsage,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = outboxBacklog.toLong(),
                consumerThroughput = dispatcherThroughput,
                externalDependencyLatencyMs = effectivePgLatencyMs,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.ADD_DISPATCHER_WORKERS -> traits.copy(dispatcherWorkers = traits.dispatcherWorkers + 8)
            SimulationActionType.ENABLE_IDEMPOTENT_PG_RETRY -> traits.copy(idempotentPgRetryEnabled = true)
            SimulationActionType.ISOLATE_PAYMENT_POOL -> traits.copy(paymentPoolIsolated = true)
            else -> error("$action does not apply to the payment incident")
        }
    }

    /**
     * docs/PRD.md §8 "예약 시스템" — 인기 좌석에 예약 시도가 몰리면 락 경합이
     * 커진다. 이전 도메인들과 축이 다르다(ADR-0010/0012): 다운스트림 의존성이나
     * 캐시가 아니라, **락 자체의 세분화 수준**이 유효 처리 용량을 결정한다.
     * 좌석 단위가 아니라 전체를 하나의 락으로 묶으면 무관한 좌석에 대한
     * 요청까지 서로 줄을 선다. 결제를 완료하지 않고 이탈한 "유령 홀드"는
     * hold timeout이 지날 때까지 용량을 계속 깎아먹고, 재고 확인과 확정이
     * 원자적이지 않으면 그 사이 경쟁에서 진 요청들의 재시도가 유효 부하를
     * 늘린다 — payment의 "유실 후 재시도 낭비"와 같은 모양이지만 여기서는
     * DB 커넥션 풀이 아니라 락 서비스 용량 자체에 적용된다.
     */
    private object Reservation {
        private const val BASELINE_RESERVATION_RPS = 20.0
        private const val INCIDENT_MULTIPLIER = 15.0 // PRD.md §8 워게임: 경합 급증

        private const val BASE_CAPACITY_RPS = 200.0 // 좌석 전체를 하나의 락으로 묶었을 때
        private const val LOCK_PARALLELISM_FACTOR = 20.0 // 좌석별로 락을 세분화했을 때의 용량 배수

        private const val ABANDONED_HOLD_RATE = 0.15 // 결제 미완료로 이탈, hold timeout까지 자원 점유
        private const val REFERENCE_TIMEOUT_SECONDS = 30.0
        private const val MAX_TIMEOUT_PRESSURE_FRACTION = 0.9

        private const val INVENTORY_RACE_WASTE_FACTOR = 2.0 // 재고 확인/확정이 원자적이지 않을 때의 재시도 낭비

        private const val BASE_RESERVATION_LATENCY_MS = 40.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val incomingRps = if (session.incidentActive) {
                BASELINE_RESERVATION_RPS * INCIDENT_MULTIPLIER
            } else {
                BASELINE_RESERVATION_RPS
            }

            val timeoutPressureFraction = if (session.incidentActive) {
                minOf(
                    MAX_TIMEOUT_PRESSURE_FRACTION,
                    ABANDONED_HOLD_RATE * (traits.holdTimeoutSeconds / REFERENCE_TIMEOUT_SECONDS),
                )
            } else {
                0.0
            }
            val baseCapacity = BASE_CAPACITY_RPS * (if (traits.fineGrainedLockingEnabled) LOCK_PARALLELISM_FACTOR else 1.0)
            val effectiveCapacity = baseCapacity * (1 - timeoutPressureFraction)

            val retryWasteFactor = if (session.incidentActive && !traits.atomicInventoryCheckEnabled) {
                INVENTORY_RACE_WASTE_FACTOR
            } else {
                0.0
            }
            val effectiveIncomingRps = incomingRps * (1 + retryWasteFactor)

            val utilization = effectiveIncomingRps / effectiveCapacity

            return SystemState(
                trafficRps = incomingRps,
                p95LatencyMs = BASE_RESERVATION_LATENCY_MS * latencyMultiplier(utilization),
                errorRate = errorRateFor(utilization),
                availability = 1.0 - errorRateFor(utilization),
                dbReadLoad = 0.0,
                dbWriteLoad = utilization,
                connectionPoolUsage = 0.0,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = max(0.0, effectiveIncomingRps - effectiveCapacity).toLong(),
                consumerThroughput = effectiveCapacity,
                externalDependencyLatencyMs = 0.0,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.ENABLE_FINE_GRAINED_LOCKING -> traits.copy(fineGrainedLockingEnabled = true)
            SimulationActionType.SHORTEN_HOLD_TIMEOUT -> traits.copy(holdTimeoutSeconds = MIN_HOLD_TIMEOUT_SECONDS)
            SimulationActionType.ENABLE_ATOMIC_INVENTORY_CHECK -> traits.copy(atomicInventoryCheckEnabled = true)
            else -> error("$action does not apply to the reservation incident")
        }

        private const val MIN_HOLD_TIMEOUT_SECONDS = 30
    }

    /**
     * docs/PRD.md §8 "배치/정산" — 야간 정산 배치가 record 단위로 청크를
     * 처리하던 중 정산 API가 저하되면 청크 하나가 실패한다. 이전 5개
     * 도메인과 축이 또 다르다(ADR-0010/0012): 동시 요청 트래픽이 자원을
     * 두고 경합하는 게 아니라, **하나의 연속된 작업이 중간에 끊겼을 때
     * 무엇을 다시 해야 하는가**가 핵심이다. 체크포인트 없이 재시작하면
     * (checkpointingEnabled=false) 실패 시점까지 이미 처리한 레코드까지
     * 전부 버려지고 처음부터 다시 돌아야 한다 — 반대로 실패한 청크만
     * 재개하면 낭비가 청크 크기로 줄어든다. 청크를 잘게 쪼갤수록
     * (chunkSize↓) 실패 시 버리는 양은 줄지만, 청크마다 붙는 커밋
     * 오버헤드가 상대적으로 커져 정상 처리량 자체가 낮아진다 — locking
     * 세분화(reservation)처럼 "작을수록 무조건 좋다"가 아니라 처리량과
     * 재처리 비용이 서로를 깎아먹는 진짜 트레이드오프다. 재처리된
     * 레코드가 멱등하게 처리되지 않으면(idempotentReconciliationEnabled=false)
     * 이미 반영된 정산 결과가 중복 반영된다 — errorRate가 여기서는
     * "요청 실패율"이 아니라 **정산 정합성이 깨진 레코드 비율**이라는
     * 점도 이전 도메인들과 다르다. 그래서 이 도메인만 latencyMultiplier/
     * errorRateFor 공용 utilization 밴드를 쓰지 않는다 — 정합성 붕괴는
     * 시스템이 "포화"돼서가 아니라 설계 구멍 때문에 생기는 것이라 부하
     * 구간과 결부시킬 이유가 없다.
     */
    private object BatchSettlement {
        private const val TOTAL_RECORDS = 1_000_000.0
        private const val PROGRESS_AT_FAILURE_FRACTION = 0.6 // PRD.md §8 워게임: partial failure — 진행 60% 시점에 정산 API 저하

        private const val BASE_SETTLEMENT_API_LATENCY_MS = 40.0
        private const val INCIDENT_API_DEGRADATION_FACTOR = 15.0

        private const val CHUNK_OVERHEAD_MS = 50.0 // 청크마다 붙는 고정 커밋/체크포인트 오버헤드
        private const val RECORD_PROCESSING_MS = 0.05

        private const val BASELINE_INGESTION_RPS = 20000.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val externalLatencyMs = if (session.incidentActive) {
                BASE_SETTLEMENT_API_LATENCY_MS * INCIDENT_API_DEGRADATION_FACTOR
            } else {
                BASE_SETTLEMENT_API_LATENCY_MS
            }

            // Restartability: 체크포인트가 없으면 실패 시점까지 처리한 전체 레코드를
            // 버리고 처음부터 다시 시작해야 한다. 있으면 실패한 청크 하나만 버린다.
            val wastedRecords = if (!session.incidentActive) {
                0.0
            } else if (traits.checkpointingEnabled) {
                traits.chunkSize.toDouble()
            } else {
                TOTAL_RECORDS * PROGRESS_AT_FAILURE_FRACTION
            }
            val recoveryOverheadFraction = wastedRecords / TOTAL_RECORDS

            // Reconciliation: 재처리가 멱등하지 않으면 버려진 게 아니라 이미 반영된
            // 레코드까지 다시 쓰여 정산 결과가 중복된다.
            val mismatchFraction = if (traits.idempotentReconciliationEnabled) 0.0 else recoveryOverheadFraction

            val chunkProcessingMs = traits.chunkSize * RECORD_PROCESSING_MS + CHUNK_OVERHEAD_MS
            val baseThroughput = traits.chunkSize / (chunkProcessingMs / 1000.0)
            val effectiveThroughput = baseThroughput * (1 - recoveryOverheadFraction)

            return SystemState(
                trafficRps = BASELINE_INGESTION_RPS,
                p95LatencyMs = chunkProcessingMs + externalLatencyMs,
                errorRate = mismatchFraction,
                availability = 1.0 - mismatchFraction,
                dbReadLoad = 0.0,
                dbWriteLoad = recoveryOverheadFraction,
                connectionPoolUsage = 0.0,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = wastedRecords.toLong(),
                consumerThroughput = effectiveThroughput,
                externalDependencyLatencyMs = externalLatencyMs,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.ENABLE_CHECKPOINT_RESTART -> traits.copy(checkpointingEnabled = true)
            SimulationActionType.REDUCE_CHUNK_SIZE -> traits.copy(chunkSize = MIN_CHUNK_SIZE)
            SimulationActionType.ENABLE_IDEMPOTENT_RECONCILIATION -> traits.copy(idempotentReconciliationEnabled = true)
            else -> error("$action does not apply to the batch-settlement incident")
        }

        private const val MIN_CHUNK_SIZE = 1000
    }

    /**
     * PLAN.md step 29 — 트래픽이 튀는 실시간 추천 API가 Kubernetes 위에서
     * 운영되다가, 트래픽 폭증과 롤링 배포가 겹쳐 발생하는 인시던트. 이전
     * 6개 도메인과 축이 다르다(ADR-0010/0012): 여기서는 "수평 확장 자체가
     * 유효한가"를 두 개의 **독립적인 승법 패널티**가 결정한다 — 리소스
     * request/limit이 안 맞으면 일부 Pod가 OOM kill로 재시작을 반복해
     * 가용 Pod 비율이 깎이고(oomAvailability), 무중단 배포 안전장치
     * (readiness probe/PodDisruptionBudget) 없이 인시던트 중 롤링 배포가
     * 겹치면 또 별도로 깎인다(rolloutAvailability). 두 패널티는 서로
     * 독립적이라 곱해진다 — 그래서 Pod 수만 늘리는 조치(SCALE_OUT_REPLICAS)
     * 하나만으로는 거의 개선되지 않는다(새로 늘어난 Pod도 똑같이 OOM
     * kill되거나 롤아웃에 휩쓸린다): 수평 확장은 안정성 문제를 대신
     * 해결해주지 않는다는, 이전 도메인들엔 없던 교훈이다.
     */
    private object Autoscaling {
        private const val BASELINE_TRAFFIC_RPS = 100.0
        private const val INCIDENT_MULTIPLIER = 10.0 // 인기 콘텐츠 바이럴: 트래픽 10배 폭증

        private const val PER_POD_CAPACITY_RPS = 50.0

        private const val OOM_CRASH_LOOP_AVAILABILITY = 0.4 // 리소스 제한 미조정 시, 유효 가용 Pod 비율
        private const val ROLLOUT_CAPACITY_RETENTION = 0.7 // 롤아웃 안전장치 없을 시, 배포 중 유효 가용 Pod 비율

        private const val BASE_LATENCY_MS = 45.0

        fun computeState(session: SimulationSessionState): SystemState {
            val traits = session.traits
            val incomingRps = if (session.incidentActive) BASELINE_TRAFFIC_RPS * INCIDENT_MULTIPLIER else BASELINE_TRAFFIC_RPS

            val oomAvailability = if (session.incidentActive && !traits.resourceLimitsTuned) OOM_CRASH_LOOP_AVAILABILITY else 1.0
            val rolloutAvailability = if (session.incidentActive && !traits.rolloutSafeguardEnabled) ROLLOUT_CAPACITY_RETENTION else 1.0

            val effectiveReplicas = traits.podReplicas * oomAvailability * rolloutAvailability
            val crashLoopingPods = (traits.podReplicas * (1 - oomAvailability)).toLong()

            val totalCapacity = effectiveReplicas * PER_POD_CAPACITY_RPS
            val utilization = incomingRps / totalCapacity

            return SystemState(
                trafficRps = incomingRps,
                p95LatencyMs = BASE_LATENCY_MS * latencyMultiplier(utilization),
                errorRate = errorRateFor(utilization),
                availability = 1.0 - errorRateFor(utilization),
                dbReadLoad = 0.0,
                dbWriteLoad = 0.0,
                connectionPoolUsage = 0.0,
                cacheHitRatio = 0.0,
                cacheLatencyMs = 0.0,
                queueLag = crashLoopingPods,
                consumerThroughput = totalCapacity,
                externalDependencyLatencyMs = 0.0,
            )
        }

        fun applyAction(traits: DesignTraits, action: SimulationActionType): DesignTraits = when (action) {
            SimulationActionType.SCALE_OUT_REPLICAS -> traits.copy(podReplicas = traits.podReplicas + SCALE_OUT_STEP)
            SimulationActionType.TUNE_RESOURCE_LIMITS -> traits.copy(resourceLimitsTuned = true)
            SimulationActionType.ENABLE_ROLLOUT_SAFEGUARD -> traits.copy(rolloutSafeguardEnabled = true)
            else -> error("$action does not apply to the autoscaling incident")
        }

        private const val SCALE_OUT_STEP = 36
    }
}
