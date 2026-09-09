package com.sysdrill.backend.auth

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/** docs/COMMERCIALIZATION.md — brute-force lockout keyed by the attempted email (not IP -- [RateLimitInterceptor] already covers IP-based abuse at the endpoint level; this specifically protects one account from being guessed against). */
@Service
class LoginAttemptService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.auth.login-lockout.max-attempts}") private val maxAttempts: Long,
    @Value("\${sysdrill.auth.login-lockout.lockout-minutes}") private val lockoutMinutes: Long,
) {
    private fun key(email: String) = "sysdrill:auth:login-attempts:${email.lowercase()}"

    fun isLocked(email: String): Boolean {
        val count = redisTemplate.opsForValue().get(key(email))?.toLongOrNull() ?: 0
        return count >= maxAttempts
    }

    fun recordFailure(email: String) {
        val redisKey = key(email)
        val count = redisTemplate.opsForValue().increment(redisKey) ?: 1
        if (count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofMinutes(lockoutMinutes))
        }
    }

    fun recordSuccess(email: String) {
        redisTemplate.delete(key(email))
    }
}
