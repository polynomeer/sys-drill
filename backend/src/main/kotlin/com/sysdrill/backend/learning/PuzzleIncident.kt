package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.simulation.LogGenerator
import com.sysdrill.backend.simulation.LogLine
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine as Engine
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SyntheticTraces
import com.sysdrill.backend.simulation.SystemStateResponse
import com.sysdrill.backend.simulation.TelemetrySampler
import com.sysdrill.backend.simulation.TraceSpan
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/** One investigation the puzzle offers; [cost] is spent from the difficulty's budget (scored on the page). */
data class PuzzleInvestigation(val key: String, val label: String, val cost: Int)

/** A recent operational change, [minutesBefore] the incident started (0 = as it started). */
data class PuzzleChange(val minutesBefore: Int, val text: String)

/** The evidence one investigation opens. Only the field matching [kind] is filled. */
data class PuzzleEvidence(
    val kind: String,
    val logs: List<LogLine>? = null,
    val spans: List<TraceSpan>? = null,
    /** `components` names the system (its topology is the picture), so the domain comes with it. */
    val domain: String? = null,
    val state: SystemStateResponse? = null,
    val changes: List<PuzzleChange>? = null,
)

/** When each of the failure pattern's alerts first fired before the moment asked about — "which signal moved first". */
data class PuzzleSignal(val metric: String, val label: String, val second: Long?)

data class PuzzleDebrief(
    val signals: List<PuzzleSignal>,
    /** The domain's three Drill actions — the action step's choices. */
    val actions: List<SimulationActionType>,
    val relatedConcepts: List<RelatedConceptLink>,
    /** The engine lab for this domain, if any — "try it with the knobs". */
    val lab: String?,
)

/** Up to three actions in order, one every [PuzzleIncidentService.ACTION_GAP_SECONDS]; the same one may repeat (a second replica). */
data class PuzzleActRequest(val actions: List<SimulationActionType> = emptyList())

data class PuzzleActResult(
    /** RECOVERED (error rate back under 1%), PARTIAL (P95 or error rate better than doing nothing) or NO_EFFECT. */
    val verdict: String,
    val timeline: TimelineBlock,
    /** The failure pattern's mitigation, in order — and its Bad Fix — as Drill actions. */
    val recommended: List<SimulationActionType>,
    val badFix: List<SimulationActionType>,
    /** Why the Bad Fix doesn't work, when what I did was exactly it. */
    val badFixWhy: String?,
)

/**
 * docs/LEARNING_DEEPENING_PLAN.md L16 (PLAN.md Round E40) — the puzzle as a short incident response:
 * observe → investigate (logs, a slow trace, per-component state, recent changes — each with a cost)
 * → diagnose → act (Drill actions applied at the moment asked about, recovery on the engine's
 * clock) → debrief (which alert fired first, related concepts, the lab).
 *
 * Everything replays the seed's scene, so the same seed always gives the same evidence and curves.
 * What counts as the right action and the Bad Fix, and the alert thresholds, come from the
 * domain's failure-pattern timeline block (V80/V81) — one source for the pattern page and the puzzle.
 */
@Service
class PuzzleIncidentService(
    private val failurePatternRepository: FailurePatternRepository,
    private val conceptRepository: LearningConceptRepository,
    private val labRepository: LearningLabRepository,
    private val contentBlocks: ContentBlockService,
    private val mapper: ObjectMapper,
) {
    fun investigations(): List<PuzzleInvestigation> = INVESTIGATIONS

    fun evidence(seed: Long, kind: String): PuzzleEvidence {
        val scene = DiagnosticPuzzles.scene(seed)
        val start = DiagnosticPuzzles.EPOCH
        val points = TelemetrySampler.sample(scene.domain, scene.traits, start, emptyList(), start, start.plusSeconds(scene.at), Duration.ofSeconds(5))
        val last = points.last()
        return when (kind) {
            "logs" -> PuzzleEvidence(kind, logs = LogGenerator.generate(scene.domain, seed.toString(), start, points, emptyList()).takeLast(LOG_LINES))
            "trace" -> PuzzleEvidence(kind, spans = SyntheticTraces.spans(scene.domain, last, error = last.state.errorRate >= 0.05))
            "components" -> PuzzleEvidence(kind, domain = scene.domain, state = SystemStateResponse.from(last.state))
            "changes" -> PuzzleEvidence(kind, changes = CHANGES.getValue(scene.domain))
            else -> throw BadRequestException("알 수 없는 조사: $kind")
        }
    }

    fun debrief(seed: Long): PuzzleDebrief {
        val scene = DiagnosticPuzzles.scene(seed)
        val pattern = failurePatternRepository.findById(scene.domain).orElseThrow { NotFoundException("No failure pattern for ${scene.domain}") }
        val timeline = patternTimeline(pattern)
        // The pattern's alerts against this scene's own series, sampled every 2 s up to the moment asked about.
        val start = DiagnosticPuzzles.EPOCH
        val points = TelemetrySampler.sample(scene.domain, scene.traits, start, emptyList(), start, start.plusSeconds(scene.at), Duration.ofSeconds(2))
        @Suppress("UNCHECKED_CAST")
        val rows = points.map { mapper.convertValue(SystemStateResponse.from(it.state), Map::class.java) as Map<String, Any?> }
        val signals = timeline.alerts.map { alert ->
            val hit = points.indices.firstOrNull { i ->
                val v = (rows[i][alert.metric] as Number).toDouble()
                if (alert.op == "BELOW") v < alert.threshold else v > alert.threshold
            }
            PuzzleSignal(alert.metric, alert.label, hit?.let { Duration.between(start, points[it].at).seconds })
        }.sortedBy { it.second ?: Long.MAX_VALUE }
        val labels = conceptRepository.findAllById(pattern.relatedConcepts).associate { it.riskKey to it.label }
        return PuzzleDebrief(
            signals = signals,
            actions = DOMAIN_ACTIONS.getValue(scene.domain),
            relatedConcepts = pattern.relatedConcepts.mapNotNull { key -> labels[key]?.let { RelatedConceptLink(key, it, "RELATED") } },
            lab = labRepository.findAllByOrderByDisplayOrderAsc().firstOrNull { it.kind == "ENGINE" && it.domain == scene.domain }?.slug,
        )
    }

    /**
     * My actions, one every [ACTION_GAP_SECONDS] from the moment asked about, against doing nothing and
     * against the pattern's mitigation on the same clock.
     */
    fun act(seed: Long, request: PuzzleActRequest): PuzzleActResult {
        val scene = DiagnosticPuzzles.scene(seed)
        val allowed = DOMAIN_ACTIONS.getValue(scene.domain)
        val actions = request.actions
        if (actions.isEmpty() || actions.size > 3) throw BadRequestException("조치는 1~3개를 고르세요")
        actions.firstOrNull { it !in allowed }?.let { throw BadRequestException("이 시스템에 없는 조치입니다: $it") }

        val pattern = failurePatternRepository.findById(scene.domain).orElseThrow { NotFoundException("No failure pattern for ${scene.domain}") }
        val reference = patternTimeline(pattern)
        val recommended = reference.scenarios.first { it.tone == "good" }.actions.map { it.action }
        val badFix = reference.scenarios.first { it.tone == "bad" }.actions.map { it.action }
        fun timed(list: List<SimulationActionType>) = list.mapIndexed { i, a -> TimelineBlock.At(scene.at + i * ACTION_GAP_SECONDS, a) }

        @Suppress("UNCHECKED_CAST")
        val traits = (mapper.convertValue(scene.traits, Map::class.java) as Map<String, Any?>).filterValues { it != null }.mapValues { it.value!! }
        val resolved = contentBlocks.resolveTimeline(
            TimelineBlock(
                domain = scene.domain,
                traits = traits,
                durationSeconds = scene.at + AFTER_SECONDS,
                stepSeconds = 5,
                metrics = reference.metrics,
                scenarios = listOf(
                    TimelineBlock.Scenario("내 조치", "bad", timed(actions)),
                    TimelineBlock.Scenario("장애 패턴의 완화 순서", "good", timed(recommended)),
                ),
            ),
        )
        val series = resolved.resolved!!.series
        val verdict = verdict(series[0], series[1])
        // Colour my line by how it went: green when it recovered.
        val shown = resolved.copy(
            resolved = resolved.resolved.copy(series = series.mapIndexed { i, s -> if (i == 1 && verdict == "RECOVERED") s.copy(tone = "good") else s }),
        )
        return PuzzleActResult(
            verdict = verdict,
            timeline = shown,
            recommended = recommended,
            badFix = badFix,
            // The same actions as the Bad Fix (repeats included — "replica twice" is still replicas only).
            badFixWhy = if (actions.toSet() == badFix.toSet()) pattern.badFixes.firstOrNull()?.get("why") else null,
        )
    }

    /** Recovered = the error rate is back under 1% at the end; partial = P95 or error rate ends lower than doing nothing. */
    fun verdict(nothing: TimelineBlock.Series, mine: TimelineBlock.Series): String {
        fun last(s: TimelineBlock.Series, m: String) = s.values[m]?.last()
        val error = last(mine, "errorRate") ?: error("puzzle timelines carry errorRate")
        if (error < 0.01) return "RECOVERED"
        val better = listOf("errorRate", "p95LatencyMs").any { m ->
            val before = last(nothing, m)
            val after = last(mine, m)
            before != null && after != null && Direction.of(before, after) == Direction.DOWN
        }
        return if (better) "PARTIAL" else "NO_EFFECT"
    }

    /** The failure pattern's timeline block, with errorRate and P95 among its metrics for the verdict. */
    private fun patternTimeline(pattern: FailurePattern): TimelineBlock {
        val block = contentBlocks.parse(pattern.blocks).filterIsInstance<TimelineBlock>().firstOrNull()
            ?: error("Failure pattern ${pattern.domain} has no timeline block")
        val metrics = (block.metrics + listOf("errorRate", "p95LatencyMs")).distinct()
        return block.copy(metrics = metrics)
    }

    companion object {
        const val ACTION_GAP_SECONDS = 30L
        const val AFTER_SECONDS = 240L
        private const val LOG_LINES = 30

        val INVESTIGATIONS = listOf(
            PuzzleInvestigation("changes", "최근 변경 이력", 1),
            PuzzleInvestigation("logs", "로그 보기", 2),
            PuzzleInvestigation("trace", "느린 요청 트레이스", 2),
            PuzzleInvestigation("components", "컴포넌트별 상태", 3),
        )

        /** Each domain's Drill actions (SimulationActionType groups them by incident). */
        val DOMAIN_ACTIONS: Map<String, List<SimulationActionType>> = mapOf(
            Engine.DOMAIN_COUPON to listOf(SimulationActionType.STRENGTHEN_RATE_LIMIT, SimulationActionType.INCREASE_CACHE_TTL, SimulationActionType.INCREASE_DB_POOL),
            Engine.DOMAIN_NOTIFICATION to listOf(SimulationActionType.ADD_CONSUMERS, SimulationActionType.ENABLE_CIRCUIT_BREAKER, SimulationActionType.ADJUST_RETRY_BACKOFF),
            Engine.DOMAIN_PRODUCT_BROWSING to listOf(SimulationActionType.SPLIT_CACHE_POLICY, SimulationActionType.ENABLE_SINGLE_FLIGHT, SimulationActionType.ADD_READ_REPLICA),
            Engine.DOMAIN_PAYMENT to listOf(SimulationActionType.ADD_DISPATCHER_WORKERS, SimulationActionType.ENABLE_IDEMPOTENT_PG_RETRY, SimulationActionType.ISOLATE_PAYMENT_POOL),
            Engine.DOMAIN_RESERVATION to listOf(SimulationActionType.ENABLE_FINE_GRAINED_LOCKING, SimulationActionType.SHORTEN_HOLD_TIMEOUT, SimulationActionType.ENABLE_ATOMIC_INVENTORY_CHECK),
            Engine.DOMAIN_BATCH_SETTLEMENT to listOf(SimulationActionType.ENABLE_CHECKPOINT_RESTART, SimulationActionType.REDUCE_CHUNK_SIZE, SimulationActionType.ENABLE_IDEMPOTENT_RECONCILIATION),
            Engine.DOMAIN_AUTOSCALING to listOf(SimulationActionType.SCALE_OUT_REPLICAS, SimulationActionType.TUNE_RESOURCE_LIMITS, SimulationActionType.ENABLE_ROLLOUT_SAFEGUARD),
            Engine.DOMAIN_DEPLOYMENT to listOf(SimulationActionType.CONTINUE_ROLLOUT, SimulationActionType.PAUSE_ROLLOUT, SimulationActionType.ROLLBACK),
        )

        /**
         * What the change log shows — the relevant change among ordinary noise, the way a real one reads.
         * It points at a trigger, never names the root cause outright.
         */
        val CHANGES: Map<String, List<PuzzleChange>> = mapOf(
            Engine.DOMAIN_COUPON to listOf(
                PuzzleChange(0, "선착순 쿠폰 이벤트 오픈 (마케팅 공지 발송 완료)"),
                PuzzleChange(1440, "coupon-api 1.8.2 배포 — 로그 포맷 변경"),
                PuzzleChange(4320, "Redis 노드 교체 (설정 그대로)"),
            ),
            Engine.DOMAIN_NOTIFICATION to listOf(
                PuzzleChange(10, "마케팅 푸시 캠페인 발송 시작"),
                PuzzleChange(0, "외부 메시징 provider 상태 페이지: '일부 지역 응답 지연 조사 중'"),
                PuzzleChange(2880, "notify-consumer 재시도 정책 변경 없음 (실패 시 즉시 재시도)"),
            ),
            Engine.DOMAIN_PRODUCT_BROWSING to listOf(
                PuzzleChange(5, "타임세일 시작 — 상위 20개 상품 메인 노출"),
                PuzzleChange(1440, "상품 캐시 TTL을 모든 키 10초로 통일"),
                PuzzleChange(10080, "product-db 읽기 복제본 회수 (비용 절감)"),
            ),
            Engine.DOMAIN_PAYMENT to listOf(
                PuzzleChange(0, "PG사 공지: '승인 응답 지연 발생'"),
                PuzzleChange(2880, "order-api 3.4.0 배포 — 결제 재시도 단순화 (요청마다 새 결제 요청)"),
                PuzzleChange(10080, "주문·결제 처리가 같은 DB 커넥션 풀을 쓰도록 설정 통합"),
            ),
            Engine.DOMAIN_RESERVATION to listOf(
                PuzzleChange(15, "인기 공연 예매 오픈"),
                PuzzleChange(2880, "lock-service 설정 배포 — 락 키를 공연 단위로 통합"),
                PuzzleChange(7200, "좌석 홀드 시간을 5분으로 연장"),
            ),
            Engine.DOMAIN_BATCH_SETTLEMENT to listOf(
                PuzzleChange(30, "일일 정산 배치 시작"),
                PuzzleChange(0, "settlement-api 응답 지연 (외부 정산 기관 점검 공지)"),
                PuzzleChange(5760, "정산 배치를 단일 Job으로 재작성 — 실패 시 처음부터 재실행"),
            ),
            Engine.DOMAIN_AUTOSCALING to listOf(
                PuzzleChange(10, "추천 모델 v3 롤링 업데이트 시작"),
                PuzzleChange(1440, "recommend-api 메모리 limit 512Mi 유지 (모델 크기 1.6배)"),
                PuzzleChange(4320, "HPA 최대 Pod 수 상향"),
            ),
            Engine.DOMAIN_DEPLOYMENT to listOf(
                PuzzleChange(1, "checkout-api 2.14.0 카나리 배포 시작 (10%, 60초마다 2배)"),
                PuzzleChange(1440, "checkout-api 2.13.4 배포 — 특이 사항 없음"),
                PuzzleChange(4320, "결제 화면 A/B 테스트 종료"),
            ),
        )
    }
}
