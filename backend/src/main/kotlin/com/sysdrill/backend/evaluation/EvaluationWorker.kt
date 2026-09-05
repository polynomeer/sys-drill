package com.sysdrill.backend.evaluation

import com.sysdrill.backend.identity.SkillProfileService
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
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
    private val skillProfileService: SkillProfileService,
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
        try {
            transactionTemplate.executeWithoutResult {
                processJobInTransaction(job)
            }
        } catch (ex: DataIntegrityViolationException) {
            // The exists-check above and this insert aren't atomic, so a
            // redelivered (or cross-instance) duplicate of this job can race
            // past the check before either side commits. idx_evaluations_one
            // _active_per_submission (V28, ADR-0026) is the actual guard;
            // this just means we lost the race — the other delivery already
            // won, so there is nothing left for this one to do.
            log.info(
                "Submission {} was evaluated concurrently by another delivery; discarding this duplicate",
                job.submissionId,
            )
        }
    }

    private fun processJobInTransaction(job: EvaluationJob) {
        if (evaluationRepository.existsBySubmissionIdAndIsActiveTrue(job.submissionId)) {
            log.info("Submission {} already evaluated; skipping duplicate job", job.submissionId)
            return
        }
        val submission = submissionRepository.findById(job.submissionId).orElse(null)
        if (submission == null) {
            log.warn("Submission {} not found; dropping job", job.submissionId)
            return
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
            sessionRepository.findById(submission.sessionId).ifPresent { session ->
                skillProfileService.recordEvaluation(
                    userId = session.userId,
                    ruleRiskKeys = outcome.riskFlags.filter { it.riskKey != "LLM_TOP_RISK" }.map { it.riskKey },
                    totalScore = outcome.totalScore,
                )
            }

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
        } catch (ex: DataIntegrityViolationException) {
            // Postgres has already aborted this transaction; let it roll
            // back and propagate to processJob's outer catch instead of
            // treating this as a real failure (handleFailure would otherwise
            // re-enqueue it and, worse, try more JDBC calls on a connection
            // that's no longer usable).
            throw ex
        } catch (ex: Exception) {
            handleFailure(job, submission.sessionId, ex)
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
