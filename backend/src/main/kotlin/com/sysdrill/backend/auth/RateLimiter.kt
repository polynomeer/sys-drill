package com.sysdrill.backend.auth

import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Fixed-window request counter backed by Redis `INCR`+`EXPIRE` (same shape as
 * [com.sysdrill.backend.evaluation.EvaluationQueue]'s Redis-plumbing
 * components) — protects public auth endpoints from brute-force/abuse.
 * Not the same thing as [com.sysdrill.backend.simulation.realinfra.RealInfraCouponStats.FixedWindowLimiter],
 * which measures *simulated* traffic for the coupon Wargame domain; this one
 * gates real requests to this API.
 */
@Component
class RateLimiter(private val redisTemplate: StringRedisTemplate) {

    /** Returns true if the call is allowed (and counts it); false if [limit] was already reached in the current window. */
    fun tryAcquire(key: String, limit: Long, window: Duration): Boolean {
        val redisKey = "sysdrill:ratelimit:$key"
        val count = redisTemplate.opsForValue().increment(redisKey) ?: 1
        if (count == 1L) {
            redisTemplate.expire(redisKey, window)
        }
        return count <= limit
    }
}
