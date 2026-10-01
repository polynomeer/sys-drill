package com.sysdrill.backend.auth

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * docs/COMMERCIALIZATION.md — this app is fully stateless JWT (no session
 * store, ADR-0003/PLAN.md step 30), so "log out" previously meant nothing
 * server-side; a leaked token stayed valid for the full 30-day TTL with no
 * way to stop it. Rather than a per-token blacklist (would need to store
 * every outstanding token), this stores one "revoked before" timestamp per
 * user -- logging out invalidates every token issued before that moment,
 * which also means "log out everywhere", a better fit for responding to a
 * suspected leak than killing one token at a time. Same Redis-timestamp
 * idiom as [UserExistenceCache].
 */
@Component
class TokenRevocationService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.auth.token-ttl-days}") private val tokenTtlDays: Long,
) {
    private fun key(userId: UUID) = "sysdrill:auth:revoked-before:$userId"

    /** Invalidates every token this user currently holds, issued at or before now. */
    fun revokeAllTokens(userId: UUID) {
        // TTL matches the longest a token could otherwise live -- once that
        // long has passed, every pre-logout token would have expired via its
        // own `exp` claim anyway, so the marker stops being needed.
        redisTemplate.opsForValue().set(key(userId), Instant.now().toString(), Duration.ofDays(tokenTtlDays))
    }

    /**
     * True if [issuedAt] is at or before this user's last logout -- i.e. a
     * pre-logout token being replayed. `<=`, not `<`: JWT `iat` is
     * second-granularity, so a same-second re-login's new token could tie
     * with the revocation timestamp. Erring toward rejecting that rare tie
     * (the user just logs in again) over ever letting a revoked token
     * through.
     */
    fun isRevoked(userId: UUID, issuedAt: Instant): Boolean {
        val revokedBefore = redisTemplate.opsForValue().get(key(userId))?.let { Instant.parse(it) } ?: return false
        return !issuedAt.isAfter(revokedBefore)
    }
}
