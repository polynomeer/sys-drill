package com.sysdrill.backend.build

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consumes submission ids from [BuildJobQueue] on a single background thread
 * (same shape as EvaluationWorker). Unlike EvaluationWorker, every DB write
 * here is a standalone repository call — no bulk @Modifying update is mixed
 * in, so none of that pitfall's flush dance is needed; Spring Data wraps each
 * repository call in its own short transaction.
 */
@Component
class BuildRunnerWorker(
    private val buildJobQueue: BuildJobQueue,
    private val buildChallengeRepository: BuildChallengeRepository,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val buildStageRepository: BuildStageRepository,
    private val buildStageResultRepository: BuildStageResultRepository,
    private val sandboxExecutor: SandboxExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "build-runner-worker") }

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
                val submissionId = buildJobQueue.poll(POLL_TIMEOUT) ?: continue
                processSubmission(submissionId)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                if (running.get()) {
                    log.error("Build runner loop error", ex)
                }
            }
        }
    }

    private fun processSubmission(submissionId: UUID) {
        val submission = buildSubmissionRepository.findById(submissionId).orElse(null)
        if (submission == null) {
            log.warn("Build submission {} not found; dropping job", submissionId)
            return
        }
        val challenge = buildChallengeRepository.findById(submission.challengeId).orElse(null)
        val stages = challenge?.let { buildStageRepository.findByChallengeIdOrderByStageOrderAsc(it.id!!) }.orEmpty()

        if (challenge == null || stages.isEmpty()) {
            log.warn("Build challenge {} missing or has no stages; failing submission {}", submission.challengeId, submissionId)
            submission.status = BuildSubmissionStatus.ERROR
            submission.completedAt = Instant.now()
            buildSubmissionRepository.save(submission)
            return
        }

        submission.status = BuildSubmissionStatus.RUNNING
        buildSubmissionRepository.save(submission)

        var passedCount = 0
        for (stage in stages) {
            val result = sandboxExecutor.run(challenge.sourceFileName, submission.sourceCode, stage.testScript)
            val status = if (result.passed) BuildStageStatus.PASSED else BuildStageStatus.FAILED
            if (result.passed) passedCount++

            buildStageResultRepository.save(
                BuildStageResult(
                    submissionId = submission.id!!,
                    stageId = stage.id!!,
                    status = status,
                    feedback = buildFeedback(stage, result),
                )
            )
        }

        submission.status = BuildSubmissionStatus.COMPLETED
        submission.score = passedCount
        submission.completedAt = Instant.now()
        buildSubmissionRepository.save(submission)
    }

    private fun buildFeedback(stage: BuildStage, result: SandboxResult): String =
        if (result.passed) {
            "통과했습니다. 학습 포인트: ${stage.spec ?: stage.title}"
        } else {
            "실패했습니다: ${extractFailReason(result.output)}"
        }

    private fun extractFailReason(output: String): String {
        val marker = "RESULT:FAIL:"
        val line = output.lineSequence().firstOrNull { it.startsWith(marker) }
        return line?.removePrefix(marker)?.trim()?.takeIf { it.isNotEmpty() }
            ?: output.takeLast(500).ifBlank { "알 수 없는 오류" }
    }

    private companion object {
        val POLL_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
