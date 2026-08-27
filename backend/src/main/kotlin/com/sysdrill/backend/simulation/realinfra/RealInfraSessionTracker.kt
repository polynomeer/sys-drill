package com.sysdrill.backend.simulation.realinfra

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Tracks which sessions currently have real-infra resources provisioned
 * (Postgres schema + dedicated pool), scored by last-touched time, so
 * [RealInfraSessionSweepWorker] (PLAN.md step 22) can find and clean up
 * abandoned ones. A Redis sorted set, not a TTL key — [SimulationStateStore]'s
 * per-session TTL already expires the *simulation state*, but that alone
 * never tells anyone to also drop the schema/pool, hence a separate tracker.
 */
@Component
class RealInfraSessionTracker(private val redisTemplate: StringRedisTemplate) {

    fun touch(sessionId: UUID) {
        redisTemplate.opsForZSet().add(KEY, sessionId.toString(), System.currentTimeMillis().toDouble())
    }

    fun findExpired(olderThan: Duration): List<UUID> {
        val cutoff = (System.currentTimeMillis() - olderThan.toMillis()).toDouble()
        val ids = redisTemplate.opsForZSet().rangeByScore(KEY, Double.NEGATIVE_INFINITY, cutoff) ?: emptySet()
        return ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun forget(sessionId: UUID) {
        redisTemplate.opsForZSet().remove(KEY, sessionId.toString())
    }

    private companion object {
        const val KEY = "sysdrill:simulation:realinfra:sessions"
    }
}
