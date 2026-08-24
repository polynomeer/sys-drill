package com.sysdrill.backend.simulation

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Per-session simulation state, held in Redis rather than Postgres
 * (docs/ARCHITECTURE.md §9: "실시간 시뮬레이션 상태"). Expires on its own if a
 * session is abandoned mid-wargame rather than needing explicit cleanup.
 */
@Component
class SimulationStateStore(private val redisTemplate: StringRedisTemplate) {

    fun save(sessionId: UUID, state: SimulationSessionState) {
        redisTemplate.opsForValue().set(key(sessionId), SimulationSessionStateCodec.encode(state), TTL)
    }

    fun find(sessionId: UUID): SimulationSessionState? =
        redisTemplate.opsForValue().get(key(sessionId))?.let(SimulationSessionStateCodec::decode)

    private fun key(sessionId: UUID) = "sysdrill:simulation:$sessionId"

    private companion object {
        val TTL: Duration = Duration.ofHours(6)
    }
}
