package com.sysdrill.backend.build

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/** Same Redis-list-as-queue approach as EvaluationQueue, one submission id per job. */
@Component
class BuildJobQueue(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.build.queue-key}") private val queueKey: String,
) {
    fun enqueue(submissionId: UUID) {
        redisTemplate.opsForList().rightPush(queueKey, submissionId.toString())
    }

    fun poll(timeout: Duration): UUID? =
        redisTemplate.opsForList().leftPop(queueKey, timeout)?.let(UUID::fromString)
}
