package com.sysdrill.backend.evaluation

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

data class EvaluationJob(val submissionId: UUID, val attempt: Int)

/**
 * Thin wrapper around a Redis list used as the MVP job queue
 * (docs/ARCHITECTURE.md §2/§8). Jobs are encoded as "<submissionId>:<attempt>"
 * to avoid pulling a JSON mapper into what is otherwise plumbing.
 */
@Component
class EvaluationQueue(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.evaluation.queue-key}") private val queueKey: String,
    @Value("\${sysdrill.evaluation.dead-letter-key}") private val deadLetterKey: String,
) {

    fun enqueue(submissionId: UUID, attempt: Int = 1) {
        redisTemplate.opsForList().rightPush(queueKey, encode(submissionId, attempt))
    }

    /** Blocks up to [timeout] waiting for a job; returns null on timeout. */
    fun poll(timeout: Duration): EvaluationJob? {
        val raw = redisTemplate.opsForList().leftPop(queueKey, timeout) ?: return null
        return decode(raw)
    }

    fun sendToDeadLetter(job: EvaluationJob) {
        redisTemplate.opsForList().rightPush(deadLetterKey, encode(job.submissionId, job.attempt))
    }

    fun deadLetterCount(): Long = redisTemplate.opsForList().size(deadLetterKey) ?: 0

    private fun encode(submissionId: UUID, attempt: Int) = "$submissionId:$attempt"

    private fun decode(raw: String): EvaluationJob {
        val (id, attempt) = raw.split(":", limit = 2)
        return EvaluationJob(UUID.fromString(id), attempt.toInt())
    }
}
