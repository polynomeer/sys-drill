package com.sysdrill.backend.build

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

data class SandboxResult(val passed: Boolean, val output: String)

/**
 * Runs one stage's test against a submission's source code in an isolated
 * Docker container (PLAN.md step 9: "Docker 기반 격리 워커, CPU/메모리/timeout
 * 제한, outbound network 차단"). One `docker run` per (submission, stage) —
 * simple and fully isolated, at the cost of container-startup latency; fine
 * for the MVP's scale.
 */
@Component
class SandboxExecutor(
    @Value("\${sysdrill.build.sandbox-image}") private val image: String,
    @Value("\${sysdrill.build.timeout-seconds}") private val timeoutSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(sourceFileName: String, sourceCode: String, testScript: String): SandboxResult {
        val workDir = Files.createTempDirectory("sysdrill-build-")
        return try {
            workDir.resolve(sourceFileName).writeText(sourceCode)
            workDir.resolve("run_test.py").writeText(testScript)
            execute(workDir)
        } finally {
            runCatching { workDir.toFile().deleteRecursively() }
                .onFailure { log.warn("Failed to clean up sandbox workdir {}: {}", workDir, it.message) }
        }
    }

    private fun execute(workDir: Path): SandboxResult {
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--network", "none",
            "--cpus", "0.5",
            "--memory", "128m",
            "--pids-limit", "64",
            "-v", "$workDir:/work:ro",
            "-w", "/work",
            image,
            "timeout", timeoutSeconds.toString(),
            "python3", "run_test.py",
        ).redirectErrorStream(true).start()

        val finished = process.waitFor(timeoutSeconds + 15, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) {
            process.destroyForcibly()
            return SandboxResult(passed = false, output = "sandbox timed out after ${timeoutSeconds}s")
        }
        return SandboxResult(passed = output.contains("RESULT:PASS"), output = output.trim())
    }
}
