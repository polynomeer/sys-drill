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
    /** 연속 실패 횟수 — 백오프를 늘리는 데만 쓴다. 스레드마다 따로 센다. */
    private val consecutiveErrors = ThreadLocal.withInitial { 0 }
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
                consecutiveErrors.set(0)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                if (!running.get()) continue
                log.error("Build runner loop error", ex)
                backOffAfterError()
            }
        }
    }


    /**
     * 예외 뒤에는 쉬었다 가되, 연속 실패가 이어지면 **점점 더 오래** 쉰다
     * (1초에서 두 배씩, [MAX_ERROR_BACKOFF] 상한). 한 번이라도 정상 처리되면
     * 다시 1초로 돌아간다.
     *
     * 큐가 비어 있을 때는 poll 자체가 [POLL_TIMEOUT] 만큼 막아 주지만, Redis 가
     * 내려가거나 컨텍스트가 닫혀 커넥션 팩토리가 멈추면 poll 이 **즉시** 던진다.
     * 대기가 없으면 CPU 가 허용하는 속도로 같은 스택 트레이스를 찍는 핫 루프가
     * 되는데, 2026-10-01 전체 테스트 실행에서 이 한 가지 원인으로 test-results 가
     * 15GB 까지 불었고 그 CPU 경합이 샌드박스 타임아웃에도 기여했다.
     *
     * 복구 불가로 보이는 예외에서 루프를 아예 끝내 보기도 했는데, 그건 더 나빴다 —
     * 살아 있는 컨텍스트의 워커까지 영구히 죽여서 이후 세션이 평가되지 않았고
     * community 테스트 14개가 깨졌다. 워커를 멈추는 것은 [stop] 하나로만 한다.
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
            // Go 레이스 디텍터(ADR-0052)는 RESULT 줄 전에 프로세스를 멈춘다. 출력 끝 500자는
            // 스택의 꼬리뿐이라, 무엇이 잘못됐는지를 먼저 말하고 위치는 테스트 로그로 보낸다.
            ?: DATA_RACE_FEEDBACK.takeIf { output.contains("WARNING: DATA RACE") }
            ?: output.takeLast(500).ifBlank { "알 수 없는 오류" }
    }

    private companion object {
        const val DATA_RACE_FEEDBACK =
            "data race — 여러 고루틴이 동기화 없이 같은 값을 읽고 썼습니다. 테스트 로그의 \"WARNING: DATA RACE\" 아래 " +
                "두 스택이 서로 부딪힌 위치입니다. 그 접근을 sync.Mutex 등으로 보호하세요."

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

        /** The sandbox already caps capture at 64k; the log panel needs far less. Keep the tail — the failure marker and traceback are at the end. */
        const val MAX_STORED_OUTPUT_CHARS = 8_000
    }
}
