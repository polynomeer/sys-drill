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
        val process = ProcessBuilder(
            "docker", "run", "--rm",
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

        val finished = process.waitFor(durationSeconds + 20L, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) {
            process.destroyForcibly()
            log.warn("k6 run timed out for session {}: {}", sessionId, output)
            return K6Summary(p95Ms = 0.0, errorRate = 1.0, achievedRps = 0.0)
        }

        val summaryFile = outDir.resolve("summary.json")
        if (!Files.exists(summaryFile)) {
            log.warn("k6 produced no summary for session {}: {}", sessionId, output)
            return K6Summary(p95Ms = 0.0, errorRate = 1.0, achievedRps = 0.0)
        }
        return parseSummary(summaryFile.readText())
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
}
