package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.SimulationStateStore
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * Target endpoints for [CouponLoadRunner]'s k6 script (PLAN.md step 21) —
 * NOT called by the frontend. Reads/writes go through the session's
 * dedicated pool ([SessionDataSourceRegistry]) and schema
 * ([CouponSchemaProvisioner]), itself routed through the session's
 * [ToxiproxySessionProxy] (PLAN.md step 23) — so every number k6 measures
 * reflects genuinely isolated real infra under a real injected network
 * fault, not a formula. The actual DB calls are wrapped in a named
 * [Observation] (PLAN.md step 24) — Spring auto-instruments HTTP request
 * handling once micrometer-tracing is on the classpath, but a plain JDBC
 * call through a manually-built HikariDataSource is not auto-instrumented
 * without a JDBC proxy dependency, and the DB span is the one that actually
 * shows the real Toxiproxy-injected latency in the resulting Jaeger trace.
 */
@RestController
@RequestMapping("/sessions/{sessionId}/simulation/realinfra/coupon")
class RealInfraCouponController(
    private val stateStore: SimulationStateStore,
    private val schemaProvisioner: CouponSchemaProvisioner,
    private val dataSourceRegistry: SessionDataSourceRegistry,
    private val toxiproxy: ToxiproxySessionProxy,
    private val sessionTracker: RealInfraSessionTracker,
    private val redisTemplate: StringRedisTemplate,
    private val stats: RealInfraCouponStats,
    private val observationRegistry: ObservationRegistry,
    @Value("\${sysdrill.simulation.realinfra.max-db-pool-size}") private val maxPoolSize: Int,
) {
    @GetMapping("/remaining")
    fun remaining(@PathVariable sessionId: UUID): ResponseEntity<Map<String, Int>> {
        // This controller's own jdbcTemplateFor() provisions a Toxiproxy proxy
        // (toxiproxy.jdbcUrlFor) independently of RealInfraCouponEngine, which is
        // the only other place that touches the tracker (PLAN.md step 22/23).
        // A request landing here for a session the engine never touched — e.g. a
        // one-off manual curl call, or k6 hitting this endpoint directly — would
        // otherwise provision a proxy RealInfraSessionSweepWorker can never find,
        // leaking it (and its port) forever.
        sessionTracker.touch(sessionId)
        val traits = currentTraits(sessionId)
        val cacheKey = cacheKey(sessionId)
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            stats.recordCacheHit(sessionId)
            return ResponseEntity.ok(mapOf("remaining" to cached.toInt()))
        }
        stats.recordCacheMiss(sessionId)
        val remaining = dbObservation("coupon.db.select_remaining", sessionId) {
            jdbcTemplateFor(sessionId, traits).queryForObject("SELECT remaining FROM coupon_inventory WHERE id = 1", Int::class.java) ?: 0
        }
        redisTemplate.opsForValue().set(cacheKey, remaining.toString(), Duration.ofSeconds(traits.cacheTtlSeconds.toLong().coerceAtLeast(1)))
        return ResponseEntity.ok(mapOf("remaining" to remaining))
    }

    @PostMapping("/claim")
    fun claim(@PathVariable sessionId: UUID): ResponseEntity<Map<String, String>> {
        sessionTracker.touch(sessionId)
        val traits = currentTraits(sessionId)
        if (traits.rateLimitEnabled && !stats.tryAcquire(sessionId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("status" to "rate_limited"))
        }
        val updated = dbObservation("coupon.db.claim", sessionId) {
            jdbcTemplateFor(sessionId, traits).update("UPDATE coupon_inventory SET remaining = remaining - 1 WHERE id = 1 AND remaining > 0")
        }
        // A successful claim invalidates the cached "remaining" count so the next read reflects reality.
        redisTemplate.delete(cacheKey(sessionId))
        return if (updated > 0) {
            ResponseEntity.ok(mapOf("status" to "claimed"))
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("status" to "sold_out"))
        }
    }

    // highCardinalityKeyValue, not low — a session id is unbounded/unique per
    // session, which is exactly what that attribute class exists for (trace
    // detail only, never aggregated into a metric's tag set).
    private fun <T> dbObservation(name: String, sessionId: UUID, block: () -> T): T =
        Observation.createNotStarted(name, observationRegistry)
            .highCardinalityKeyValue("sysdrill.session_id", sessionId.toString())
            .observe(block)!!

    private fun jdbcTemplateFor(sessionId: UUID, traits: DesignTraits): JdbcTemplate {
        val schema = schemaProvisioner.schemaName(sessionId)
        val poolSize = traits.dbPoolSize.coerceIn(MIN_DB_POOL_SIZE, maxPoolSize)
        // Same jdbcUrl the engine's probe used (toxiproxy.provision is
        // idempotent) — without this, k6's requests would bypass the injected
        // latency entirely, defeating the fault injection.
        val jdbcUrl = toxiproxy.jdbcUrlFor(sessionId)
        return JdbcTemplate(dataSourceRegistry.poolFor(sessionId, schema, poolSize, jdbcUrl))
    }

    private fun currentTraits(sessionId: UUID): DesignTraits = stateStore.find(sessionId)?.traits ?: DesignTraits()

    private fun cacheKey(sessionId: UUID) = "sysdrill:simulation:realinfra:$sessionId:remaining"

    private companion object {
        const val MIN_DB_POOL_SIZE = 2
    }
}
