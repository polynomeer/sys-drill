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
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Consumes [EvaluationJob]s from [EvaluationQueue] on [workerConcurrency]
 * background threads (docs/COMMERCIALIZATION.md — a local traffic benchmark
 * showed the old single-thread version backing this queue up to 4,500+
 * items under sustained load). Safe to run concurrently: retry state lives
 * in the job payload, not on this class, and duplicate/concurrent delivery
 * of the same job is already handled below via
 * idx_evaluations_one_active_per_submission (V28, ADR-0026) rather than by
 * assuming only one thread is ever processing at a time. This is a logically
 * separate module today (docs/ARCHITECTURE.md §2 calls for the boundary to
 * exist even before it runs as its own process) — splitting it into a
 * standalone deployable is a later infra step, not a PLAN.md step 3 concern.
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
    @Value("\${sysdrill.evaluation.worker-concurrency}") private val workerConcurrency: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val threadCounter = AtomicInteger(0)
    /** 연속 실패 횟수 — 백오프를 늘리는 데만 쓴다. 스레드마다 따로 센다. */
    private val consecutiveErrors = ThreadLocal.withInitial { 0 }
    private val executor = Executors.newFixedThreadPool(workerConcurrency) { r ->
        Thread(r, "evaluation-worker-${threadCounter.incrementAndGet()}")
    }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!running.compareAndSet(false, true)) return
        repeat(workerConcurrency) { executor.submit { runLoop() } }
    }

    /**
     * `@PreDestroy` 가 아니라 [ContextClosedEvent] 로 멈춘다.
     *
     * Spring 의 종료 순서는 ContextClosedEvent 발행 → Lifecycle 빈 stop() →
     * 싱글턴 destroy() 다. `@PreDestroy` 는 마지막 단계라, 그 사이에 이미 멈춘
     * LettuceConnectionFactory 를 워커가 계속 폴링하며
     * `IllegalStateException: LettuceConnectionFactory has been STOPPED` 를
     * 끝없이 던진다. 테스트처럼 한 JVM 에서 컨텍스트가 여러 번 뜨고 닫히면 그
     * 스레드들이 살아남아 이후 테스트 내내 CPU 와 로그를 먹는다 — 2026-10-01
     * 전체 실행에서 클래스당 수백 건씩 찍힌 그 오류가 이것이다.
     *
     * [stop] 은 멱등이고, `@PreDestroy` 는 이벤트를 받지 못하는 경로를 위한
     * 안전망으로 남겨 둔다.
     */
    @EventListener(ContextClosedEvent::class)
    @PreDestroy
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        executor.shutdownNow()
    }

    private fun runLoop() {
        while (running.get()) {
            try {
                val job = evaluationQueue.poll(POLL_TIMEOUT) ?: continue
                processJob(job)
                consecutiveErrors.set(0)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                // stop() interrupts a possibly-blocked Redis call; depending on
                // exactly when the connection factory is torn down relative to
                // that interrupt, Lettuce can surface this as a plain
                // IllegalStateException instead of InterruptedException. Only
                // treat it as a real error if we're not already shutting down.
                if (!running.get()) continue
                log.error("Evaluation worker loop error", ex)
                backOffAfterError()
            }
        }
    }

    /**
     * 예외 뒤에는 쉬었다 가되, 연속 실패가 이어지면 점점 더 오래 쉰다 — 이유는
     * [com.sysdrill.backend.build.BuildRunnerWorker] 의 같은 메서드 주석 참고.
     * 두 워커가 같은 루프 모양을 공유하므로 같은 함정도 공유한다.
     */
    private fun backOffAfterError() {
        val attempt = consecutiveErrors.get() + 1
        consecutiveErrors.set(attempt)
        val millis = minOf(
            ERROR_BACKOFF.toMillis() shl minOf(attempt - 1, 16),
            MAX_ERROR_BACKOFF.toMillis(),
        )
        try {
            Thread.sleep(millis)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
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

        /** 첫 실패 직후의 대기. 이후 연속 실패마다 두 배가 된다. */
        val ERROR_BACKOFF: Duration = Duration.ofSeconds(1)

        /**
         * 백오프 상한.
         *
         * 60초로 잡았다가 10초로 낮췄다. 상한이 클수록 좀비 스레드의 로그는 줄지만,
         * 그만큼 **살아 있는 워커가 다음 작업을 늦게 집는다** — 일시적인 오류 몇 번에
         * 60초를 자고 있으면 그 사이 들어온 제출은 사용자가 1분을 기다린다.
         * 10초면 소음은 여전히 10배 줄고(1초 간격 대비), 사용자가 체감할 지연은
         * 폴링 주기([POLL_TIMEOUT]) 수준에 머문다.
         */
        val MAX_ERROR_BACKOFF: Duration = Duration.ofSeconds(10)
    }
}
