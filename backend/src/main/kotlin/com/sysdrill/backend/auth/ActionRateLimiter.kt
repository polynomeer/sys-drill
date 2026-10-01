package com.sysdrill.backend.auth

import java.time.Duration
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * docs/COMMERCIALIZATION.md — a per-minute, per-user burst limiter for
 * authenticated actions that cost real money or compute (an LLM call, a
 * sandboxed `docker run`), built on the same [RateLimiter] the IP-keyed
 * [RateLimitInterceptor] uses for the public `/auth` endpoints. Keyed by
 * user rather than IP since every call site this backs is already behind
 * [AuthInterceptor] -- the limit should follow the account incurring the
 * cost, not whichever IP it happens to come from.
 */
@Component
class ActionRateLimiter(private val rateLimiter: RateLimiter) {

    /** Returns true if [action] is allowed for [userId] (and counts it) within this minute's window. */
    fun tryAcquire(action: String, userId: UUID, limit: Long): Boolean =
        rateLimiter.tryAcquire("$action:$userId", limit, Duration.ofMinutes(1))
}
