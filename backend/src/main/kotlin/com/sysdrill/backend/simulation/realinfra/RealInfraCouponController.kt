package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.SimulationStateStore
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
 * fault, not a formula.
 */
@RestController
@RequestMapping("/sessions/{sessionId}/simulation/realinfra/coupon")
class RealInfraCouponController(
    private val stateStore: SimulationStateStore,
    private val schemaProvisioner: CouponSchemaProvisioner,
    private val dataSourceRegistry: SessionDataSourceRegistry,
    private val toxiproxy: ToxiproxySessionProxy,
    private val redisTemplate: StringRedisTemplate,
    private val stats: RealInfraCouponStats,
    @Value("\${sysdrill.simulation.realinfra.max-db-pool-size}") private val maxPoolSize: Int,
) {
    @GetMapping("/remaining")
    fun remaining(@PathVariable sessionId: UUID): ResponseEntity<Map<String, Int>> {
        val traits = currentTraits(sessionId)
        val cacheKey = cacheKey(sessionId)
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            stats.recordCacheHit(sessionId)
            return ResponseEntity.ok(mapOf("remaining" to cached.toInt()))
        }
        stats.recordCacheMiss(sessionId)
        val remaining = jdbcTemplateFor(sessionId, traits)
            .queryForObject("SELECT remaining FROM coupon_inventory WHERE id = 1", Int::class.java) ?: 0
        redisTemplate.opsForValue().set(cacheKey, remaining.toString(), Duration.ofSeconds(traits.cacheTtlSeconds.toLong().coerceAtLeast(1)))
        return ResponseEntity.ok(mapOf("remaining" to remaining))
    }

    @PostMapping("/claim")
    fun claim(@PathVariable sessionId: UUID): ResponseEntity<Map<String, String>> {
        val traits = currentTraits(sessionId)
        if (traits.rateLimitEnabled && !stats.tryAcquire(sessionId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("status" to "rate_limited"))
        }
        val updated = jdbcTemplateFor(sessionId, traits)
            .update("UPDATE coupon_inventory SET remaining = remaining - 1 WHERE id = 1 AND remaining > 0")
        // A successful claim invalidates the cached "remaining" count so the next read reflects reality.
        redisTemplate.delete(cacheKey(sessionId))
        return if (updated > 0) {
            ResponseEntity.ok(mapOf("status" to "claimed"))
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("status" to "sold_out"))
        }
    }

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
