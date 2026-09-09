package com.sysdrill.backend.evaluation

import com.sysdrill.backend.common.web.ConflictException
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * docs/COMMERCIALIZATION.md — caps how many evaluation requests (each one an
 * LLM call via [com.sysdrill.backend.evaluation.llm.AnthropicLlmClient]) a
 * single user can trigger per calendar day, so a runaway loop or a malicious
 * user can't run up the LLM bill unbounded. Same Redis counter shape as
 * [com.sysdrill.backend.auth.RateLimiter], but daily-windowed and keyed by
 * user rather than IP.
 */
@Component
class LlmUsageGuard(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.evaluation.daily-limit-per-user}") private val dailyLimitPerUser: Long,
) {
    private fun key(userId: UUID) = "sysdrill:evaluation:daily-usage:$userId:${LocalDate.now()}"

    /** Throws if [userId] has already hit today's limit; otherwise counts this call and allows it. */
    fun checkAndRecord(userId: UUID) {
        val redisKey = key(userId)
        val count = redisTemplate.opsForValue().increment(redisKey) ?: 1
        if (count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofHours(25)) // outlives the calendar day regardless of timezone skew
        }
        if (count > dailyLimitPerUser) {
            throw ConflictException("Daily evaluation limit reached -- please try again tomorrow")
        }
    }
}
