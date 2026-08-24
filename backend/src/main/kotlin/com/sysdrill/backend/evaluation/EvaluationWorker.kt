package com.sysdrill.backend.evaluation

import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consumes [EvaluationJob]s from [EvaluationQueue] on a single background
 * thread. This is a logically separate module today (docs/ARCHITECTURE.md §2
 * calls for the boundary to exist even before it runs as its own process) —
 * splitting it into a standalone deployable is a later infra step, not a
 * PLAN.md step 3 concern.
 *
 * On failure: retries up to [maxAttempts] by re-enqueueing with an
 * incremented attempt count, then gives up by sending the job to the dead
 * letter list and moving the session to EVALUATION_FAILED
 * (docs/ARCHITECTURE.md §5/§8).
 */
@Component
class EvaluationWorker(
    private val evaluationQueue: EvaluationQueue,
    private val evaluationRepository: EvaluationRepository,
    private val evaluationRiskFlagRepository: EvaluationRiskFlagRepository,
    private val submissionRepository: SubmissionRepository,
    private val sessionRepository: SessionRepository,
    private val hybridRuleAiEvaluator: HybridRuleAiEvaluator,
    private val objectMapper: ObjectMapper,
    @Qualifier("transactionTemplate") private val transactionTemplate: TransactionTemplate,
    @Value("\${sysdrill.evaluation.max-attempts}") private val maxAttempts: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "evaluation-worker") }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.submit { runLoop() }
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    private fun runLoop() {
        while (running.get()) {
            try {
                val job = evaluationQueue.poll(POLL_TIMEOUT) ?: continue
                processJob(job)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                // stop() interrupts a possibly-blocked Redis call; depending on
                // exactly when the connection factory is torn down relative to
                // that interrupt, Lettuce can surface this as a plain
                // IllegalStateException instead of InterruptedException. Only
                // treat it as a real error if we're not already shutting down.
                if (running.get()) {
                    log.error("Evaluation worker loop error", ex)
                }
            }
        }
    }

    private fun processJob(job: EvaluationJob) {
        transactionTemplate.executeWithoutResult {
            if (evaluationRepository.existsBySubmissionIdAndIsActiveTrue(job.submissionId)) {
                log.info("Submission {} already evaluated; skipping duplicate job", job.submissionId)
                return@executeWithoutResult
            }
            val submission = submissionRepository.findById(job.submissionId).orElse(null)
            if (submission == null) {
                log.warn("Submission {} not found; dropping job", job.submissionId)
                return@executeWithoutResult
            }

            try {
                val outcome = hybridRuleAiEvaluator.evaluate(submission)
                // saveAndFlush, not save: compareAndSetStatus below is a bulk
                // @Modifying(clearAutomatically = true) update. clear() detaches
                // the persistence context without flushing it first, so a plain
                // save() here would have its INSERT silently dropped.
                val evaluation = evaluationRepository.saveAndFlush(
                    Evaluation(
                        submissionId = submission.id!!,
                        rubricVersion = outcome.rubricVersion,
                        totalScore = outcome.totalScore,
                        scoreDimensions = objectMapper.writeValueAsString(outcome.rubricScores),
                        strengths = objectMapper.writeValueAsString(outcome.strengths),
                        weaknesses = objectMapper.writeValueAsString(outcome.weaknesses),
                        riskPoints = objectMapper.writeValueAsString(outcome.riskFlags.map { it.description }),
                        followupQuestions = objectMapper.writeValueAsString(outcome.followupQuestions),
                        recommendedChanges = objectMapper.writeValueAsString(outcome.recommendedChanges),
                        modelProvider = outcome.modelProvider,
                        modelName = outcome.modelName,
                        latencyMs = outcome.latencyMs,
                    )
                )
                // saveAllAndFlush for the same reason as the Evaluation save above:
                // the compareAndSetStatus bulk update right after this clears the
                // persistence context without flushing it first.
                evaluationRiskFlagRepository.saveAllAndFlush(
                    outcome.riskFlags.map { finding ->
                        EvaluationRiskFlag(
                            evaluationId = evaluation.id!!,
                            riskKey = finding.riskKey,
                            severity = finding.severity,
                            description = finding.description,
                        )
                    }
                )
                val transitioned = sessionRepository.compareAndSetStatus(
                    submission.sessionId,
                    SessionStatus.EVALUATING,
                    SessionStatus.FEEDBACK_READY,
                )
                if (transitioned == 0) {
                    log.warn(
                        "Session {} was not EVALUATING after evaluating submission {}; leaving as-is",
                        submission.sessionId,
                        submission.id,
                    )
                }
            } catch (ex: Exception) {
                handleFailure(job, submission.sessionId, ex)
            }
        }
    }

    private fun handleFailure(job: EvaluationJob, sessionId: java.util.UUID, ex: Exception) {
        log.warn("Evaluation attempt {} failed for submission {}: {}", job.attempt, job.submissionId, ex.message)
        if (job.attempt < maxAttempts) {
            evaluationQueue.enqueue(job.submissionId, job.attempt + 1)
            return
        }
        evaluationQueue.sendToDeadLetter(job)
        val transitioned = sessionRepository.compareAndSetStatus(
            sessionId,
            SessionStatus.EVALUATING,
            SessionStatus.EVALUATION_FAILED,
        )
        if (transitioned == 0) {
            log.warn("Session {} was not EVALUATING when giving up on submission {}", sessionId, job.submissionId)
        }
    }

    private companion object {
        val POLL_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
