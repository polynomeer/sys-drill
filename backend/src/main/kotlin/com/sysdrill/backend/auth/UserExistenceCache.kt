package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.UserRepository
import java.time.Duration
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * docs/COMMERCIALIZATION.md — a JWT only proves the token was signed by us
 * and hasn't expired, not that the user it names still exists (DB reset,
 * account deletion). [AuthInterceptor] uses this to catch a stale token
 * before it reaches a service layer that assumes the user row is there and
 * fails with an opaque FK-constraint 500 instead of a clear 401. Same Redis
 * counter/cache shape as [com.sysdrill.backend.evaluation.LlmUsageGuard] —
 * short TTL on both the positive and negative result so a real deletion is
 * picked up within [ttlSeconds] without hitting the DB on every request.
 */
@Component
class UserExistenceCache(
    private val redisTemplate: StringRedisTemplate,
    private val userRepository: UserRepository,
    @Value("\${sysdrill.auth.user-exists-cache-ttl-seconds}") private val ttlSeconds: Long,
) {
    private fun key(userId: UUID) = "sysdrill:auth:user-exists:$userId"

    fun exists(userId: UUID): Boolean {
        redisTemplate.opsForValue().get(key(userId))?.let { return it == "1" }
        val actual = userRepository.existsById(userId)
        redisTemplate.opsForValue().set(key(userId), if (actual) "1" else "0", Duration.ofSeconds(ttlSeconds))
        return actual
    }
}
