package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine as Engine
import com.sysdrill.backend.simulation.TelemetrySampler
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** One metrics sample the puzzle shows — no names, nothing that would give the domain away. */
data class PuzzlePoint(
    val second: Long,
    val trafficRps: Double,
    val p95LatencyMs: Double,
    val errorRatePct: Double,
    val dbReadLoadPct: Double,
    val dbWriteLoadPct: Double,
    val poolUsagePct: Double,
    val cacheHitPct: Double,
    val cacheLatencyMs: Double,
    val queueLag: Long,
    val externalLatencyMs: Double,
)

data class PuzzleChoice(val key: String, val label: String)

data class DiagnosticPuzzleView(
    val seed: Long,
    /** From a minute before the incident to the moment being asked about. */
    val points: List<PuzzlePoint>,
    val patterns: List<PuzzleChoice>,
    val checks: List<PuzzleChoice>,
)

data class PuzzleAnswer(val pattern: String? = null, val check: String? = null)

data class PuzzleResult(
    val patternCorrect: Boolean?,
    val checkCorrect: Boolean?,
    /** The failure pattern (an incident domain) — the page links to /learning/failures/{it}. */
    val answerPattern: String,
    val answerPatternName: String,
    val acceptedChecks: List<String>,
    val explanation: String,
)

/**
 * docs/LEARNING_EXPANSION_PLAN.md L9 (PLAN.md Round E28) — "what is happening?" from metrics alone.
 * Nothing is stored: a seed decides the domain, slightly shaken capacity values and the moment,
 * and the rule engine produces the numbers. Also the weekly C12 puzzle's generator.
 */
object DiagnosticPuzzles {
    /**
     * Which domains and checks a puzzle draws from. Versioned by week (LEARNING_EXPANSION_PLAN L9, follow-up):
     * a weekly puzzle someone already answered must not change under them when a domain is added.
     */
    data class PuzzleSet(val domains: List<String>, val checks: List<PuzzleChoice>)

    private val DOMAINS_7 = listOf(
        Engine.DOMAIN_COUPON, Engine.DOMAIN_NOTIFICATION, Engine.DOMAIN_PRODUCT_BROWSING, Engine.DOMAIN_PAYMENT,
        Engine.DOMAIN_RESERVATION, Engine.DOMAIN_BATCH_SETTLEMENT, Engine.DOMAIN_AUTOSCALING,
    )

    private val CHECKS_7 = listOf(
        PuzzleChoice("db-pool", "DB 커넥션 풀·쓰기 부하"),
        PuzzleChoice("cache", "캐시 적중률·캐시 지연"),
        PuzzleChoice("external", "외부 의존성 응답 시간"),
        PuzzleChoice("consumer-lag", "큐 적체(consumer lag)"),
        PuzzleChoice("lock", "락 대기·경합"),
        PuzzleChoice("reprocess", "배치 실패·재처리 범위"),
        PuzzleChoice("pods", "Pod 재시작·가용 Pod 수"),
    )

    /** Until ISO week 2026-40: the seven domains the weekly puzzle launched with. */
    val LEGACY = PuzzleSet(DOMAINS_7, CHECKS_7)

    /** From ISO week 2026-41 (and every random puzzle): with the deployment domain (ADR-0049). */
    val CURRENT = PuzzleSet(DOMAINS_7 + Engine.DOMAIN_DEPLOYMENT, CHECKS_7 + PuzzleChoice("deploy", "최근 배포·변경 이력"))

    private const val DEPLOYMENT_FROM_WEEK = 202641

    fun setForWeek(week: Int): PuzzleSet = if (week < DEPLOYMENT_FROM_WEEK) LEGACY else CURRENT

    /** Every check any set offers — for validating an answer. */
    val CHECKS: List<PuzzleChoice> get() = CURRENT.checks

    /** First things worth checking per domain — the first is the best, the rest are also fine. */
    private val ACCEPTED = mapOf(
        Engine.DOMAIN_COUPON to listOf("db-pool", "cache"),
        Engine.DOMAIN_NOTIFICATION to listOf("external", "consumer-lag"),
        Engine.DOMAIN_PRODUCT_BROWSING to listOf("cache"),
        Engine.DOMAIN_PAYMENT to listOf("external", "db-pool"),
        Engine.DOMAIN_RESERVATION to listOf("lock"),
        Engine.DOMAIN_BATCH_SETTLEMENT to listOf("reprocess", "external"),
        Engine.DOMAIN_AUTOSCALING to listOf("pods"),
        Engine.DOMAIN_DEPLOYMENT to listOf("deploy"),
    )

    private val EXPLANATIONS = mapOf(
        Engine.DOMAIN_COUPON to "DB 쓰기 부하가 100%를 넘고 커넥션 풀이 가득 찼는데 캐시 지연도 함께 올랐습니다 — 한 키·한 테이블로 몰린 쓰기 경합입니다.",
        Engine.DOMAIN_NOTIFICATION to "DB는 조용한데 외부 의존성 지연이 커지고 큐 적체가 계속 늘어납니다 — 느린 provider와 재시도가 컨슈머를 붙잡고 있습니다.",
        Engine.DOMAIN_PRODUCT_BROWSING to "캐시 적중률이 떨어지면서 DB 읽기 부하만 치솟습니다 — 동시 miss가 같은 조회를 DB로 쏟는 Cache Stampede입니다.",
        Engine.DOMAIN_PAYMENT to "외부 응답이 수 초로 늘고 커넥션 풀까지 차오릅니다 — 외부 호출이 커넥션을 붙잡아 다른 처리로 번지는 중입니다.",
        Engine.DOMAIN_RESERVATION to "쓰기 부하와 에러가 함께 오르지만 외부·캐시는 멀쩡합니다 — 같은 자원을 두고 기다리는 락 경합입니다.",
        Engine.DOMAIN_BATCH_SETTLEMENT to "에러(중복·실패 레코드)와 재처리 대기 건수가 쌓이고 외부 API도 느립니다 — 실패한 배치가 처음부터 다시 도는 중입니다.",
        Engine.DOMAIN_AUTOSCALING to "트래픽은 감당 범위인데 에러가 크고 지연이 들쭉날쭉합니다 — 용량이 아니라 Pod 자체가 불안정(재시작·롤아웃)합니다.",
        Engine.DOMAIN_DEPLOYMENT to "트래픽·DB·캐시는 평소와 같은데 에러만 계단처럼 오릅니다 — 인프라가 아니라 방금 나간 변경(카나리 배포)이 원인입니다.",
    )

    fun domainOf(seed: Long, set: PuzzleSet = CURRENT): String = set.domains[Random(seed).nextInt(set.domains.size)]

    fun generate(seed: Long, patternNames: Map<String, String>, set: PuzzleSet = CURRENT): DiagnosticPuzzleView {
        val random = Random(seed)
        val domain = set.domains[random.nextInt(set.domains.size)]
        fun shake(base: Int) = maxOf(1, (base * (0.6 + random.nextDouble() * 0.8)).toInt())
        val traits = DesignTraits(
            cacheTtlSeconds = shake(DesignTraits.DEFAULT_CACHE_TTL_SECONDS),
            dbPoolSize = shake(DesignTraits.DEFAULT_DB_POOL_SIZE),
            consumerCount = shake(DesignTraits.DEFAULT_CONSUMER_COUNT),
            dispatcherWorkers = shake(DesignTraits.DEFAULT_DISPATCHER_WORKERS),
            holdTimeoutSeconds = shake(DesignTraits.DEFAULT_HOLD_TIMEOUT_SECONDS),
            chunkSize = shake(DesignTraits.DEFAULT_CHUNK_SIZE),
            podReplicas = shake(DesignTraits.DEFAULT_POD_REPLICAS),
        )
        val at = 60L + random.nextLong(181)
        val start = EPOCH
        val points = TelemetrySampler.sample(domain, traits, start, emptyList(), start.minusSeconds(60), start.plusSeconds(at), Duration.ofSeconds(10))
        return DiagnosticPuzzleView(
            seed = seed,
            points = points.map { p ->
                val s = p.state
                PuzzlePoint(
                    second = Duration.between(start, p.at).seconds,
                    trafficRps = s.trafficRps, p95LatencyMs = s.p95LatencyMs, errorRatePct = s.errorRate * 100,
                    dbReadLoadPct = s.dbReadLoad * 100, dbWriteLoadPct = s.dbWriteLoad * 100, poolUsagePct = s.connectionPoolUsage * 100,
                    cacheHitPct = s.cacheHitRatio * 100, cacheLatencyMs = s.cacheLatencyMs, queueLag = s.queueLag, externalLatencyMs = s.externalDependencyLatencyMs,
                )
            },
            patterns = set.domains.map { PuzzleChoice(it, patternNames[it] ?: it) },
            checks = set.checks,
        )
    }

    fun grade(seed: Long, answer: PuzzleAnswer, patternNames: Map<String, String>, set: PuzzleSet = CURRENT): PuzzleResult {
        if (answer.check != null && set.checks.none { it.key == answer.check }) throw BadRequestException("알 수 없는 선택지: ${answer.check}")
        val domain = domainOf(seed, set)
        val accepted = ACCEPTED.getValue(domain)
        return PuzzleResult(
            patternCorrect = answer.pattern?.let { it == domain },
            checkCorrect = answer.check?.let { it in accepted },
            answerPattern = domain,
            answerPatternName = patternNames[domain] ?: domain,
            acceptedChecks = accepted,
            explanation = EXPLANATIONS.getValue(domain),
        )
    }

    /** A fixed origin so the same seed gives the same numbers (the engine only reads offsets from it). */
    private val EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")
}

@Service
class DiagnosticPuzzleService(private val failurePatternRepository: FailurePatternRepository) {
    fun names(): Map<String, String> = failurePatternRepository.findAll().associate { it.domain to it.name }
    fun puzzle(seed: Long, set: DiagnosticPuzzles.PuzzleSet = DiagnosticPuzzles.CURRENT) = DiagnosticPuzzles.generate(seed, names(), set)
    fun grade(seed: Long, answer: PuzzleAnswer, set: DiagnosticPuzzles.PuzzleSet = DiagnosticPuzzles.CURRENT) = DiagnosticPuzzles.grade(seed, answer, names(), set)
}

/** Open without login (docs/LEARNING_EXPANSION_PLAN.md L9 — a five-minute taste). */
@RestController
class DiagnosticPuzzleController(private val service: DiagnosticPuzzleService) {
    /** Without [seed] a fresh random one. */
    @GetMapping("/learning/puzzles")
    fun puzzle(@RequestParam(required = false) seed: Long?): DiagnosticPuzzleView =
        service.puzzle(seed ?: Random.nextLong(1, Long.MAX_VALUE))

    @PostMapping("/learning/puzzles/{seed}/answer")
    fun answer(@PathVariable seed: Long, @RequestBody answer: PuzzleAnswer): PuzzleResult = service.grade(seed, answer)
}
