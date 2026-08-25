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
 */
object SimulationEngine {

    const val DOMAIN_COUPON = "coupon"
    const val DOMAIN_NOTIFICATION = "notification"
    const val DOMAIN_PRODUCT_BROWSING = "product-browsing"

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

    fun computeState(session: SimulationSessionState): SystemState = when (session.domain) {
        DOMAIN_COUPON -> Coupon.computeState(session)
        DOMAIN_NOTIFICATION -> Notification.computeState(session)
        DOMAIN_PRODUCT_BROWSING -> ProductBrowsing.computeState(session)
        else -> error("Unknown simulation domain: ${session.domain}")
    }

    fun applyAction(current: SimulationSessionState, action: SimulationActionType): SimulationSessionState =
        when (current.domain) {
            DOMAIN_COUPON -> current.copy(traits = Coupon.applyAction(current.traits, action))
            DOMAIN_NOTIFICATION -> current.copy(traits = Notification.applyAction(current.traits, action))
            DOMAIN_PRODUCT_BROWSING -> current.copy(traits = ProductBrowsing.applyAction(current.traits, action))
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
}
