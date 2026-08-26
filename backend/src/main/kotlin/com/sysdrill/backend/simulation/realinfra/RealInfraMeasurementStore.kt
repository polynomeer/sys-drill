package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.SystemState
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

/**
 * Caches the last real-infra probe's [SystemState] in Redis, so
 * [RealInfraCouponEngine.computeState] — polled every 3s by WargameLive.tsx —
 * reads a snapshot instead of re-running k6 on every poll. Only
 * [RealInfraCouponEngine.applyAction] (and the first post-incident
 * `computeState`) actually re-probes and refreshes this. JSON, unlike
 * [com.sysdrill.backend.simulation.SimulationSessionStateCodec]'s pipe
 * format — this is a one-off blob, not a slowly-growing fixed field list.
 */
@Component
class RealInfraMeasurementStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun find(sessionId: UUID): SystemState? =
        redisTemplate.opsForValue().get(key(sessionId))?.let { objectMapper.readValue(it, SystemState::class.java) }

    fun save(sessionId: UUID, state: SystemState) {
        redisTemplate.opsForValue().set(key(sessionId), objectMapper.writeValueAsString(state), TTL)
    }

    fun evict(sessionId: UUID) {
        redisTemplate.delete(key(sessionId))
    }

    private fun key(sessionId: UUID) = "sysdrill:simulation:realinfra:$sessionId:state"

    private companion object {
        val TTL: Duration = Duration.ofHours(6)
    }
}
