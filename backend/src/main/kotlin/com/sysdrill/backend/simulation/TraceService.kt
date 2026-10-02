package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

data class TraceSpan(
    val spanId: String,
    val parentSpanId: String?,
    val name: String,
    val service: String,
    /** Milliseconds after the trace's first span started. */
    val startMs: Long,
    val durationMs: Long,
    val error: Boolean = false,
)

data class TraceSummary(val traceId: String, val at: Instant, val rootName: String, val service: String, val durationMs: Long, val error: Boolean)

/** [source]: JAEGER (real spans, real-infra coupon) or SYNTHETIC (decomposed from the rule engine's state). */
data class TraceList(val source: String, val available: Boolean, val traces: List<TraceSummary>, val note: String? = null)

data class TraceView(val traceId: String, val source: String, val at: Instant, val durationMs: Long, val spans: List<TraceSpan>)

/**
 * docs/OBSERVABILITY_UI_PLAN.md O6 (PLAN.md Round E25).
 *
 * Real-infra coupon: the spans Spring and [com.sysdrill.backend.simulation.realinfra.RealInfraCouponController]
 * already export to Jaeger, found by their `sysdrill.session_id` tag — the only real trace data there is.
 * Rule-based: a waterfall decomposed from the series point a log line came from, so a log's `traceId`
 * opens the request it describes. Synthetic traces say so; they explain where the time went, they
 * don't pretend to be measurements.
 */
@Service
class TraceService(
    private val simulationService: SimulationService,
    private val sessionRepository: com.sysdrill.backend.session.SessionRepository,
    private val missionService: com.sysdrill.backend.mission.MissionService,
    private val objectMapper: ObjectMapper,
    @Value("\${sysdrill.tracing.jaeger-query-url:http://localhost:16686}") jaegerQueryUrl: String,
    @Value("\${spring.application.name:backend}") private val serviceName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jaeger: RestClient = RestClient.builder().baseUrl(jaegerQueryUrl).build()

    fun list(sessionId: UUID, now: Instant = Instant.now()): TraceList {
        val series = simulationService.getSeries(sessionId, now)
        if (series.incidentStartedAt == null) return TraceList(SYNTHETIC, available = false, traces = emptyList())
        // PLAN.md Round E26 (O7) — tracing switched off at "deploy": there is simply nothing to look at.
        if (!tracingOn(sessionId)) return TraceList(SYNTHETIC, available = false, traces = emptyList(), note = TRACING_OFF)
        if (series.engineMode == EngineMode.REAL_INFRA.name) return jaegerList(sessionId)
        val lines = simulationService.getLogs(sessionId, now).filter { it.traceId != null }
        val traces = lines.distinctBy { it.traceId }.takeLast(MAX_TRACES).asReversed().mapNotNull { line ->
            val point = nearest(series, line.at) ?: return@mapNotNull null
            val spans = SyntheticTraces.spans(series.domain.orEmpty(), point, line.level == "ERROR")
            TraceSummary(line.traceId!!, line.at, spans.first().name, spans.first().service, spans.first().durationMs, spans.any { it.error })
        }
        return TraceList(SYNTHETIC, available = true, traces = traces, note = "규칙 기반 시뮬레이션의 지연을 구성 요소별로 나눠 만든 트레이스입니다(실측 아님).")
    }

    fun get(sessionId: UUID, traceId: String, now: Instant = Instant.now()): TraceView {
        if (!tracingOn(sessionId)) throw NotFoundException("Trace not found: $traceId")
        val series = simulationService.getSeries(sessionId, now)
        if (series.engineMode == EngineMode.REAL_INFRA.name) return jaegerTrace(traceId)
        val line = simulationService.getLogs(sessionId, now).lastOrNull { it.traceId == traceId }
            ?: throw NotFoundException("Trace not found: $traceId")
        val point = nearest(series, line.at) ?: throw NotFoundException("Trace not found: $traceId")
        val spans = SyntheticTraces.spans(series.domain.orEmpty(), point, line.level == "ERROR")
        return TraceView(traceId, SYNTHETIC, line.at, spans.first().durationMs, spans)
    }

    private fun tracingOn(sessionId: UUID): Boolean =
        sessionRepository.findById(sessionId).map { missionService.state(it).readiness?.tracing ?: true }.orElse(true)

    private fun nearest(series: SimulationSeries, at: Instant): TelemetryPoint? =
        series.points.map { it.first }.filter { !it.at.isAfter(at) }.maxByOrNull { it.at } ?: series.points.firstOrNull()?.first

    // ---- Jaeger (real-infra coupon) ----

    private fun jaegerList(sessionId: UUID): TraceList {
        val tags = objectMapper.writeValueAsString(mapOf(SESSION_TAG to sessionId.toString()))
        val body = runCatching {
            jaeger.get().uri { it.path("/api/traces").queryParam("service", serviceName).queryParam("tags", tags).queryParam("limit", MAX_TRACES).queryParam("lookback", "6h").build() }
                .retrieve().body(String::class.java)
        }.onFailure { log.warn("Jaeger query failed for session $sessionId: ${it.message}") }.getOrNull()
            ?: return TraceList(JAEGER, available = false, traces = emptyList(), note = "Jaeger에 연결하지 못했습니다.")
        val traces = objectMapper.readTree(body).path("data").mapNotNull { toView(it) }
            .map { v -> TraceSummary(v.traceId, v.at, v.spans.first().name, v.spans.first().service, v.durationMs, v.spans.any { it.error }) }
            .sortedByDescending { it.at }
        return TraceList(JAEGER, available = true, traces = traces)
    }

    private fun jaegerTrace(traceId: String): TraceView {
        require(TRACE_ID.matches(traceId)) { "bad trace id" }
        val body = runCatching { jaeger.get().uri("/api/traces/{id}", traceId).retrieve().body(String::class.java) }.getOrNull()
            ?: throw NotFoundException("Trace not found: $traceId")
        return objectMapper.readTree(body).path("data").firstOrNull()?.let { toView(it) } ?: throw NotFoundException("Trace not found: $traceId")
    }

    /** One Jaeger trace → spans ordered by start, offsets relative to the earliest span. */
    internal fun toView(trace: JsonNode): TraceView? {
        val processes = trace.path("processes")
        val raw = trace.path("spans").toList()
        if (raw.isEmpty()) return null
        val start = raw.minOf { it.path("startTime").asLong() }
        val spans = raw.sortedBy { it.path("startTime").asLong() }.map { s ->
            val parent = s.path("references").firstOrNull { it.path("refType").asString() == "CHILD_OF" }?.path("spanID")?.asString()
            val error = s.path("tags").any { it.path("key").asString() == "error" && it.path("value").asString() == "true" } ||
                s.path("tags").any { it.path("key").asString() == "otel.status_code" && it.path("value").asString() == "ERROR" }
            TraceSpan(
                spanId = s.path("spanID").asString(),
                parentSpanId = parent,
                name = s.path("operationName").asString(),
                service = processes.path(s.path("processID").asString()).path("serviceName").asString(),
                startMs = (s.path("startTime").asLong() - start) / 1000,
                durationMs = max(1, s.path("duration").asLong() / 1000),
                error = error,
            )
        }
        val end = raw.maxOf { it.path("startTime").asLong() + it.path("duration").asLong() }
        return TraceView(trace.path("traceID").asString(), JAEGER, Instant.ofEpochMilli(start / 1000), max(1, (end - start) / 1000), spans)
    }

    companion object {
        const val JAEGER = "JAEGER"
        const val SYNTHETIC = "SYNTHETIC"
        const val SESSION_TAG = "sysdrill.session_id"
        const val TRACING_OFF = "배포 시 트레이싱이 비활성이었습니다."
        private const val MAX_TRACES = 20
        private val TRACE_ID = Regex("^[0-9a-f]{8,32}$")
    }
}

/**
 * O6 2차 — where the request time went at one series point, per domain. The root span is the
 * point's p95; children split it by the components the domain's engine actually models
 * (cache latency, external dependency latency, DB/pool saturation), so the waterfall can't
 * contradict the charts it sits next to.
 */
object SyntheticTraces {
    private data class Part(val name: String, val service: String, val ms: Double, val error: Boolean = false)

    fun spans(domain: String, point: TelemetryPoint, error: Boolean): List<TraceSpan> {
        val s = point.state
        val total = max(2.0, s.p95LatencyMs)
        val (root, rootService, parts) = when (domain) {
            RuleBasedSimulationEngine.DOMAIN_COUPON -> Triple("POST /coupons/issue", "coupon-api", listOf(
                Part("GET coupon:stock", "redis", s.cacheLatencyMs),
                Part("HikariPool.getConnection", "coupon-api", poolWait(s, total - s.cacheLatencyMs), error && s.connectionPoolUsage >= 0.95),
                Part("UPDATE coupon_stock", "coupon-db", dbTime(s, total - s.cacheLatencyMs)),
            ))
            RuleBasedSimulationEngine.DOMAIN_NOTIFICATION -> Triple("consume order-events", "notify-consumer", listOf(
                Part("send via provider", "notification-provider", s.externalDependencyLatencyMs, error),
                Part("ack offset", "kafka", 2.0),
            ))
            RuleBasedSimulationEngine.DOMAIN_PRODUCT_BROWSING -> Triple("GET /products/{id}", "product-api", listOf(
                Part("GET product:{id}", "cache", max(1.0, s.cacheLatencyMs)),
                Part("SELECT product (cache miss)", "product-db", dbTime(s, total) * (1 - s.cacheHitRatio).coerceIn(0.0, 1.0), error),
            ))
            RuleBasedSimulationEngine.DOMAIN_PAYMENT -> Triple("POST /orders/{id}/pay", "order-api", listOf(
                Part("HikariPool.getConnection", "order-api", poolWait(s, total)),
                Part("PG approve", "payment-gateway", s.externalDependencyLatencyMs, error),
                Part("INSERT outbox", "order-db", 3.0),
            ))
            RuleBasedSimulationEngine.DOMAIN_RESERVATION -> Triple("POST /reservations", "reservation-api", listOf(
                Part("acquire seat lock", "lock-service", total * s.dbWriteLoad.coerceIn(0.0, 1.0) * 0.6, error),
                Part("INSERT reservation", "reservation-db", dbTime(s, total) * 0.4),
            ))
            RuleBasedSimulationEngine.DOMAIN_BATCH_SETTLEMENT -> Triple("settle chunk", "settlement-batch", listOf(
                Part("POST settlement-api", "settlement-api", s.externalDependencyLatencyMs, error),
                Part("UPDATE settlement", "settlement-db", dbTime(s, total)),
            ))
            RuleBasedSimulationEngine.DOMAIN_AUTOSCALING -> Triple("GET /recommendations", "ingress", listOf(
                Part("recommend", "recommend-api", total * 0.9, error),
            ))
            else -> Triple("request", "service", emptyList())
        }
        // Children run back to back after 1ms of the root's own work; the root covers them all.
        val spans = mutableListOf<TraceSpan>()
        var cursor = 1.0
        parts.filter { it.ms >= 0.5 }.forEachIndexed { i, p ->
            spans += TraceSpan("s${i + 1}", "s0", p.name, p.service, cursor.roundToLong(), max(1, p.ms.roundToLong()), p.error)
            cursor += p.ms
        }
        val rootDuration = max(total, cursor + 1).roundToLong()
        return listOf(TraceSpan("s0", null, root, rootService, 0, rootDuration, error)) + spans
    }

    private fun poolWait(s: SystemState, budget: Double): Double =
        if (s.connectionPoolUsage >= 0.95) max(0.0, budget) * 0.6 else 0.0

    private fun dbTime(s: SystemState, budget: Double): Double =
        if (s.connectionPoolUsage >= 0.95) max(1.0, budget) * 0.4 else max(1.0, budget - 2)
}
