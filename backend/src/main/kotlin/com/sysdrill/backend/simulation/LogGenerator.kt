package com.sysdrill.backend.simulation

import java.time.Instant

/** One generated log line (docs/OBSERVABILITY_UI_PLAN.md O4). */
data class LogLine(
    val at: Instant,
    val level: String,
    val service: String,
    val message: String,
    val traceId: String?,
)

/**
 * docs/OBSERVABILITY_UI_PLAN.md O4 (PLAN.md Round E17) — logs derived from the incident's time
 * series, replacing the client-side synthesis LogViewer used to do. Deterministic: the same
 * session, points and actions always give the same lines (ids and counts come from a hash of the
 * seed, the second and the template), so a reload, a spectator and the replay agree.
 *
 * A template fires when its own metric is past a band — the logs describe symptoms per
 * component, exactly what a real service would print. Nothing in them names the root cause
 * (the plan's rule); they're the raw material for the learner's hypothesis.
 */
object LogGenerator {

    private data class Template(
        val service: String,
        val level: String,
        /** Whether this template applies at this point. */
        val active: (TelemetryPoint) -> Boolean,
        /** Lines per point when active (1..n, picked by hash). */
        val maxPerPoint: Int,
        val message: (TelemetryPoint, Long) -> String,
    )

    private fun hash(seed: String, epochSecond: Long, salt: Int): Long {
        var h = 1125899906842597L
        for (c in "$seed:$epochSecond:$salt") h = 31 * h + c.code
        // splitmix64 finalizer — inputs differing only in the last character must not give neighbouring ids.
        h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
        h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
        return (h xor (h ushr 31)) and Long.MAX_VALUE
    }

    private val TEMPLATES: Map<String, List<Template>> = mapOf(
        RuleBasedSimulationEngine.DOMAIN_COUPON to listOf(
            Template("coupon-api", "ERROR", { it.state.errorRate >= 0.05 }, 2) { _, h -> "POST /coupons/issue 503 upstream timeout userId=${100000 + h % 900000}" },
            Template("coupon-api", "ERROR", { it.state.dbWriteLoad >= 0.95 }, 2) { p, _ -> "HikariPool-1 - Connection is not available, request timed out after 30000ms (total=50, active=50, waiting=${((p.state.dbWriteLoad - 0.9) * 400).toInt().coerceAtLeast(1)})" },
            Template("coupon-db", "WARN", { it.state.dbReadLoad >= 0.6 }, 1) { p, h -> "slow query ${(p.state.p95LatencyMs * 0.8).toInt()}ms: SELECT remaining FROM coupon_stock WHERE event_id=${h % 50}" },
            Template("redis", "WARN", { it.state.cacheLatencyMs > 10 }, 1) { p, h -> "GET coupon:stock:${h % 50} took ${p.state.cacheLatencyMs.toInt()}ms" },
            Template("redis", "INFO", { it.state.cacheHitRatio in 0.0..0.7 && it.state.cacheHitRatio > 0 }, 1) { p, h -> "cache miss ratio ${((1 - p.state.cacheHitRatio) * 100).toInt()}% key=coupon:stock:${h % 50}" },
        ),
        RuleBasedSimulationEngine.DOMAIN_NOTIFICATION to listOf(
            Template("notify-consumer", "ERROR", { it.state.externalDependencyLatencyMs >= 200 }, 2) { p, h -> "provider call timed out after ${p.state.externalDependencyLatencyMs.toInt()}ms channel=${listOf("sms", "push", "email")[(h % 3).toInt()]}" },
            Template("notify-consumer", "WARN", { it.state.errorRate >= 0.02 }, 2) { _, h -> "retrying message id=msg-${h % 100000} attempt=${1 + h % 4}" },
            Template("kafka", "WARN", { it.backlog > 1000 }, 1) { p, h -> "consumer group notify-workers lag=${p.backlog} partition=${h % 8}" },
        ),
        RuleBasedSimulationEngine.DOMAIN_PRODUCT_BROWSING to listOf(
            Template("product-api", "ERROR", { it.state.errorRate >= 0.05 }, 2) { _, h -> "GET /products/${h % 100000} 504 gateway timeout" },
            Template("cache", "WARN", { it.state.cacheHitRatio < 0.7 }, 2) { _, h -> "cache miss key=product:${h % 20} (hot)" },
            Template("product-db", "WARN", { it.state.dbReadLoad >= 0.8 }, 1) { p, _ -> "replica read queue depth high, p95=${p.state.p95LatencyMs.toInt()}ms" },
        ),
        RuleBasedSimulationEngine.DOMAIN_PAYMENT to listOf(
            Template("payment-dispatcher", "ERROR", { it.state.externalDependencyLatencyMs >= 500 }, 2) { p, h -> "PG approve timed out after ${p.state.externalDependencyLatencyMs.toInt()}ms orderId=ORD-${h % 1000000}" },
            Template("order-api", "ERROR", { it.state.connectionPoolUsage >= 0.95 }, 2) { _, _ -> "HikariPool-main - Connection is not available, request timed out after 30000ms" },
            Template("outbox", "WARN", { it.state.queueLag > 0 }, 1) { p, _ -> "outbox pending events=${p.state.queueLag}" },
        ),
        RuleBasedSimulationEngine.DOMAIN_RESERVATION to listOf(
            Template("reservation-api", "ERROR", { it.state.errorRate >= 0.05 }, 2) { _, h -> "reservation failed: lock wait timeout seat=${"ABCDEFGH"[(h % 8).toInt()]}-${1 + h % 30}" },
            Template("lock-service", "WARN", { it.state.dbWriteLoad >= 0.8 }, 1) { p, _ -> "lock contention: ${p.state.queueLag} waiters" },
            Template("reservation-api", "INFO", { it.state.dbWriteLoad >= 0.6 }, 1) { _, h -> "hold expired seat=${"ABCDEFGH"[(h % 8).toInt()]}-${1 + h % 30} (no payment)" },
        ),
        RuleBasedSimulationEngine.DOMAIN_BATCH_SETTLEMENT to listOf(
            Template("settlement-batch", "ERROR", { it.state.errorRate > 0 }, 2) { _, h -> "duplicate settlement row merchant=${h % 5000} (already applied)" },
            Template("settlement-batch", "WARN", { it.state.queueLag > 0 }, 1) { p, _ -> "chunk failed — reprocessing ${p.state.queueLag} records" },
            Template("settlement-api", "WARN", { it.state.externalDependencyLatencyMs >= 200 }, 1) { p, _ -> "settlement API slow: ${p.state.externalDependencyLatencyMs.toInt()}ms" },
        ),
        RuleBasedSimulationEngine.DOMAIN_AUTOSCALING to listOf(
            Template("kubelet", "ERROR", { it.state.queueLag > 0 }, 2) { _, h -> "pod recommend-api-${java.lang.Long.toHexString(h % 0xfffff)} OOMKilled (exit 137), restarting" },
            Template("ingress", "ERROR", { it.state.errorRate >= 0.05 }, 2) { _, _ -> "503 no healthy upstream for recommend-api" },
            Template("recommend-api", "WARN", { it.state.p95LatencyMs >= 100 }, 1) { p, _ -> "request latency p95=${p.state.p95LatencyMs.toInt()}ms" },
        ),
    )

    fun generate(
        domain: String,
        seed: String,
        incidentStartedAt: Instant,
        points: List<TelemetryPoint>,
        actions: List<TimedAction>,
    ): List<LogLine> {
        val templates = TEMPLATES[domain].orEmpty()
        val lines = mutableListOf<LogLine>()
        lines += LogLine(incidentStartedAt, "WARN", "monitor", "traffic shift detected — incident window opened", null)
        actions.forEach { lines += LogLine(it.at, "INFO", "deploy", "config change applied: ${it.action.name}", null) }
        points.filter { !it.at.isBefore(incidentStartedAt) }.forEach { point ->
            val second = point.at.epochSecond
            templates.forEachIndexed { i, template ->
                if (!template.active(point)) return@forEachIndexed
                val h = hash(seed, second, i)
                val count = 1 + (h % template.maxPerPoint).toInt()
                repeat(count) { k ->
                    val hk = hash(seed, second, i * 31 + k)
                    lines += LogLine(
                        at = point.at.plusMillis(hk % 1000),
                        level = template.level,
                        service = template.service,
                        message = template.message(point, hk),
                        traceId = if (template.level == "INFO") null else java.lang.Long.toHexString(hk).takeLast(12),
                    )
                }
            }
        }
        return lines.sortedBy { it.at }
    }
}
