package com.sysdrill.backend.build

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Consumes submission ids from [BuildJobQueue] on [workerConcurrency]
 * background threads (same shape as EvaluationWorker; docs/COMMERCIALIZATION.md).
 * Unlike EvaluationWorker, every DB write here is a standalone repository
 * call — no bulk @Modifying update is mixed in, so none of that pitfall's
 * flush dance is needed; Spring Data wraps each repository call in its own
 * short transaction. Each thread blocks synchronously on its own `docker
 * run` inside [SandboxExecutor], so [workerConcurrency] is also, directly,
 * the number of sandbox containers that can be running at once — size it
 * against host CPU/memory (each container reserves `--cpus 0.5 --memory
 * 128m`), not just against desired throughput.
 */
@Component
class BuildRunnerWorker(
    private val buildJobQueue: BuildJobQueue,
    private val buildChallengeRepository: BuildChallengeRepository,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val buildStageRepository: BuildStageRepository,
    private val buildStageResultRepository: BuildStageResultRepository,
    private val sandboxExecutor: SandboxExecutor,
    @Value("\${sysdrill.build.worker-concurrency}") private val workerConcurrency: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val threadCounter = AtomicInteger(0)
    private val executor = Executors.newFixedThreadPool(workerConcurrency) { r ->
        Thread(r, "build-runner-worker-${threadCounter.incrementAndGet()}")
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
                val submissionId = buildJobQueue.poll(POLL_TIMEOUT) ?: continue
                processSubmission(submissionId)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                if (running.get()) {
                    log.error("Build runner loop error", ex)
                    backOffAfterError()
                }
            }
        }
    }

    /**
     * 예외 뒤에는 반드시 쉬었다 간다.
     *
     * 큐가 비어 있을 때는 [BuildJobQueue.poll] 자체가 [POLL_TIMEOUT] 만큼 막아 주지만,
     * Redis 가 내려가면 poll 이 **즉시** 던진다. 그러면 이 catch 가 곧바로 다음 루프로
     * 이어져 CPU 가 허용하는 속도로 같은 스택 트레이스를 찍는 핫 루프가 된다 —
     * 2026-10-01 전체 테스트 실행에서 이 한 가지 원인으로 build 테스트의
     * test-results XML 이 클래스당 수백 MB(합계 15GB)까지 불었고, 그 CPU 경합이
     * 같은 실행의 샌드박스 타임아웃에도 기여했다.
     */
    private fun backOffAfterError() {
        try {
            Thread.sleep(ERROR_BACKOFF.toMillis())
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
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
            val startedAt = System.nanoTime()
            val result = sandboxExecutor.run(challenge.languages, challenge.sourceFileName, submission.sourceCode, stage.testScript)
            val durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis().toInt()
            val status = if (result.passed) BuildStageStatus.PASSED else BuildStageStatus.FAILED
            if (result.passed) passedCount++

            buildStageResultRepository.save(
                BuildStageResult(
                    submissionId = submission.id!!,
                    stageId = stage.id!!,
                    status = status,
                    feedback = buildFeedback(stage, result),
                    output = result.output.takeLast(MAX_STORED_OUTPUT_CHARS),
                    durationMs = durationMs,
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

        /** 예외 직후의 대기. 유휴 시 루프 주기가 이미 [POLL_TIMEOUT] 이라 이 정도면 충분하다. */
        val ERROR_BACKOFF: Duration = Duration.ofSeconds(1)

        /** The sandbox already caps capture at 64k; the log panel needs far less. Keep the tail — the failure marker and traceback are at the end. */
        const val MAX_STORED_OUTPUT_CHARS = 8_000
    }
}
