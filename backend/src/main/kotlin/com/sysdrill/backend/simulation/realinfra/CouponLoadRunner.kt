package com.sysdrill.backend.simulation.realinfra

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

data class K6Summary(val p95Ms: Double, val errorRate: Double, val achievedRps: Double)

/**
 * Runs the coupon pilot's k6 load script in Docker (PLAN.md step 21) —
 * mirrors [com.sysdrill.backend.build.SandboxExecutor]'s
 * `ProcessBuilder`-plus-`docker run` shape, but with the OPPOSITE
 * networking/mount posture: k6 must reach the live app (not be isolated from
 * it), and it must write its summary out (not just read its input).
 *
 * 컨테이너 정리도 같은 이유로 [com.sysdrill.backend.build.SandboxExecutor] 를 따른다 —
 * `destroyForcibly` 는 `docker run` 클라이언트만 죽이고 컨테이너는 남기므로, 이름을
 * 붙여 두고 직접 지운다. 여기서는 그것이 더 급하다. 남은 k6 컨테이너는 아무도 결과를
 * 읽지 않는 채로 **살아 있는 앱에 부하를 계속 쏜다**.
 *
 * 출력도 같은 이유로 실행과 나란히 비운다. k6 는 요약 외에도 진행 상황을 계속
 * 찍어서 파이프 버퍼를 쉽게 넘기는데, `waitFor` 뒤에 읽으면 그 순간 컨테이너가
 * write 에서 막혀 부하 시험 자체가 timeout 으로 잘못 기록된다.
 */
@Component
class CouponLoadRunner(
    @Value("\${sysdrill.simulation.realinfra.k6-image}") private val image: String,
    // Resolved lazily (Environment.getRequiredProperty in execute(), not a
    // constructor @Value) because its default embeds ${local.server.port} —
    // Spring Boot only publishes that property once the web server has
    // actually started, which is after this bean's own construction.
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(sessionId: UUID, rps: Int, durationSeconds: Int): K6Summary {
        val scriptFile = Files.createTempFile("sysdrill-coupon-load-", ".js")
        val outDir = Files.createTempDirectory("sysdrill-realinfra-out-")
        return try {
            javaClass.getResourceAsStream("/realinfra/coupon-load.js")?.use { input ->
                Files.copy(input, scriptFile, StandardCopyOption.REPLACE_EXISTING)
            } ?: error("realinfra/coupon-load.js not found on classpath")
            execute(sessionId, rps, durationSeconds, scriptFile, outDir)
        } finally {
            runCatching { Files.deleteIfExists(scriptFile) }
            runCatching { outDir.toFile().deleteRecursively() }
                .onFailure { log.warn("Failed to clean up k6 output dir {}: {}", outDir, it.message) }
        }
    }

    private fun execute(sessionId: UUID, rps: Int, durationSeconds: Int, scriptFile: Path, outDir: Path): K6Summary {
        val appBaseUrl = environment.getRequiredProperty("sysdrill.simulation.realinfra.app-base-url")
        val containerName = "$CONTAINER_PREFIX${UUID.randomUUID()}"
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--name", containerName,
            "--label", "$OWNER_LABEL=$OWNER_LABEL_VALUE",
            "--add-host", "host.docker.internal:host-gateway",
            // More headroom than SandboxExecutor's untrusted-code limits — at
            // higher incident RPS, an underpowered k6 container becomes the
            // bottleneck itself (dropped_iterations) rather than the app.
            "--cpus", "1.0",
            "--memory", "512m",
            "-v", "$scriptFile:/scripts/coupon-load.js:ro",
            "-v", "$outDir:/out",
            "-e", "SESSION_ID=$sessionId",
            "-e", "TARGET_URL=$appBaseUrl",
            "-e", "RATE=$rps",
            "-e", "DURATION=${durationSeconds}s",
            image,
            "run", "--summary-export=/out/summary.json", "/scripts/coupon-load.js",
        ).redirectErrorStream(true).start()

        val output = StringBuilder()
        val drain = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (output.length < MAX_OUTPUT_CHARS) output.appendLine(line)
                }
            }
        }.apply { isDaemon = true; start() }

        try {
            val finished = process.waitFor(durationSeconds + GRACE_SECONDS, TimeUnit.SECONDS)
            drain.join(DRAIN_JOIN_MILLIS)
            if (!finished) {
                log.warn("k6 run timed out for session {}: {}", sessionId, output)
                return K6Summary(p95Ms = 0.0, errorRate = 1.0, achievedRps = 0.0)
            }

            val summaryFile = outDir.resolve("summary.json")
            if (!Files.exists(summaryFile)) {
                log.warn("k6 produced no summary for session {}: {}", sessionId, output)
                return K6Summary(p95Ms = 0.0, errorRate = 1.0, achievedRps = 0.0)
            }
            return parseSummary(summaryFile.readText())
        } finally {
            process.destroyForcibly()
            drain.interrupt()
            forceRemove(containerName)
        }
    }

    /** 정상 종료라면 `--rm` 이 이미 지운 뒤다. 종료 코드를 보지 않는 이유가 그것이다. */
    private fun forceRemove(containerName: String) {
        runCatching {
            val remove = ProcessBuilder("docker", "rm", "--force", containerName)
                .redirectErrorStream(true)
                .start()
            if (!remove.waitFor(REMOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) remove.destroyForcibly()
        }.onFailure { log.warn("Failed to remove k6 container {}: {}", containerName, it.message) }
    }

    /**
     * k6's `--summary-export` JSON is flat per metric (no `values` wrapper) —
     * verified against an actual run's output, not assumed from docs:
     * `{"http_req_duration": {"p(95)": ..., "avg": ...}, "http_req_failed":
     * {"value": 0.0, "passes": N, "fails": N}, "http_reqs": {"rate": ..., "count": ...}}`.
     * `http_req_failed.value` is the fraction failed (0.0–1.0) — its
     * `passes`/`fails` sub-fields are boolean-outcome counts, not the rate.
     */
    private fun parseSummary(json: String): K6Summary {
        val metrics = objectMapper.readTree(json).path("metrics")
        val p95 = metrics.path("http_req_duration").path("p(95)").asDouble(0.0)
        val errorRate = metrics.path("http_req_failed").path("value").asDouble(0.0)
        val achievedRps = metrics.path("http_reqs").path("rate").asDouble(0.0)
        return K6Summary(p95Ms = p95, errorRate = errorRate, achievedRps = achievedRps)
    }

    companion object {
        /** 호스트에서 사람이 봤을 때 출처가 드러나야 한다. */
        const val CONTAINER_PREFIX = "sysdrill-k6-"
        const val OWNER_LABEL = "com.sysdrill.sandbox"
        const val OWNER_LABEL_VALUE = "realinfra-k6"

        /**
         * k6 가 스스로 끝나기를 기다리며 더 주는 시간.
         *
         * 이 안에 이미지 pull 이 들어간다. 캐시에 없으면 pull 만으로 이 창을 넘길 수
         * 있고, 그때 클라이언트만 죽으면 남은 컨테이너가 살아 있는 앱에 부하를 계속
         * 쏜다 — finally 의 정리가 그 경우를 받는다.
         */
        const val GRACE_SECONDS = 20L

        const val REMOVE_TIMEOUT_SECONDS = 15L

        /** 로그로 나가는 k6 출력의 상한. 요약은 파일로 받으므로 여기를 다 담을 필요가 없다. */
        const val MAX_OUTPUT_CHARS = 64_000

        const val DRAIN_JOIN_MILLIS = 2_000L
    }
}
