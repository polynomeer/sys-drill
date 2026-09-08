package com.sysdrill.backend.build

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.writeText

data class SandboxResult(val passed: Boolean, val output: String)

/**
 * Runs one stage's test against a submission's source code in an isolated
 * Docker container (PLAN.md step 9: "Docker 기반 격리 워커, CPU/메모리/timeout
 * 제한, outbound network 차단"). One `docker run` per (submission, stage) —
 * simple and fully isolated, at the cost of container-startup latency; fine
 * for the MVP's scale.
 *
 * **컨테이너를 죽이는 경로가 둘이어야 한다.** [Process.destroyForcibly] 가 죽이는 것은
 * `docker run` **클라이언트**일 뿐이고, 컨테이너는 데몬 아래 그대로 돈다. `--rm` 도
 * 컨테이너가 스스로 끝났을 때만 도는 옵션이라 매달린 컨테이너에는 효과가 없다. 그래서
 * 컨테이너에 이름을 붙여 두고 [forceRemove] 로 직접 지운다.
 *
 * **출력은 실행과 동시에 비워야 한다.** `waitFor` 로 먼저 기다린 뒤 읽으면 두 가지가
 * 깨진다. 출력이 파이프 버퍼(약 64KB)를 넘기는 순간 컨테이너가 write 에서 멈춰 멀쩡한
 * 제출이 timeout 으로 오판되고, 데드라인을 넘긴 실행에서는 그 `readText` 가 EOF 를
 * 영영 만나지 못해 **호출 스레드가 그대로 멈춘다**.
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
        val containerName = "$CONTAINER_PREFIX${UUID.randomUUID()}"
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--name", containerName,
            "--label", "$OWNER_LABEL=$OWNER_LABEL_VALUE",
            "--network", "none",
            "--cpus", "0.5",
            "--memory", "128m",
            "--pids-limit", "64",
            "-v", "$workDir:/work:ro",
            "-w", "/work",
            image,
            // 여기서 도는 것은 신뢰할 수 없는 제출 코드다. `timeout` 은 기본적으로
            // SIGTERM 만 보내므로, 그것을 무시하는 세 줄이면 이 제한을 그냥 통과한다.
            // --kill-after 가 그 뒤에 SIGKILL 을 보낸다.
            "timeout", "--kill-after=${KILL_AFTER_SECONDS}s", timeoutSeconds.toString(),
            "python3", "run_test.py",
        ).redirectErrorStream(true).start()

        // 실행과 나란히 비운다. 다 끝난 뒤에 읽으면 늦다 — 위 KDoc 참고.
        val output = StringBuilder()
        val passed = AtomicBoolean(false)
        val drain = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    // 판정은 상한과 무관하게 흐르는 모든 줄에서 본다. 마커는 대개 마지막
                    // 줄이라, 보관 상한으로 함께 잘라내면 출력이 많은 정답이 전부 실패한다.
                    if (line.contains(PASS_MARKER)) passed.set(true)
                    if (output.length < MAX_OUTPUT_CHARS) output.appendLine(line)
                }
            }
        }.apply { isDaemon = true; start() }

        return try {
            val finished = process.waitFor(timeoutSeconds + GRACE_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                SandboxResult(passed = false, output = "sandbox timed out after ${timeoutSeconds}s")
            } else {
                // 프로세스가 끝나도 마지막 줄이 아직 스레드에 남아 있을 수 있다.
                drain.join(DRAIN_JOIN_MILLIS)
                SandboxResult(passed = passed.get(), output = output.toString().trim())
            }
        } finally {
            // 어떤 경로로 끝났든 컨테이너를 남기지 않는다. destroyForcibly 만으로는
            // 클라이언트만 사라지고 컨테이너는 계속 CPU 를 태운다.
            process.destroyForcibly()
            drain.interrupt()
            forceRemove(containerName)
        }
    }

    /**
     * 컨테이너를 확실히 없앤다.
     *
     * 정상 종료한 실행에서는 `--rm` 이 이미 지운 뒤라 실패하는데, 그것이 정상이므로
     * 종료 코드를 보지 않는다. 확인할 것은 이 정리가 걸려 있지 않은가뿐이다 — 데몬이
     * 응답하지 않을 때 여기서 막히면 컨테이너 하나가 새는 대신 채점 스레드가 멈춘다.
     */
    private fun forceRemove(containerName: String) {
        runCatching {
            val remove = ProcessBuilder("docker", "rm", "--force", containerName)
                .redirectErrorStream(true)
                .start()
            if (!remove.waitFor(REMOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) remove.destroyForcibly()
        }.onFailure { log.warn("Failed to remove sandbox container {}: {}", containerName, it.message) }
    }

    companion object {
        /** 호스트에서 사람이 봤을 때 출처가 드러나야 한다. */
        const val CONTAINER_PREFIX = "sysdrill-sandbox-"
        const val OWNER_LABEL = "com.sysdrill.sandbox"
        const val OWNER_LABEL_VALUE = "backend-build"

        /** `timeout` 이 SIGTERM 뒤 SIGKILL 을 보내기까지 주는 유예. */
        const val KILL_AFTER_SECONDS = 5L

        /** 컨테이너 기동까지 감안해 바깥에서 더 기다리는 시간. */
        const val GRACE_SECONDS = 15L

        const val REMOVE_TIMEOUT_SECONDS = 15L

        const val PASS_MARKER = "RESULT:PASS"

        /**
         * 응답에 실어 보낼 출력의 상한.
         *
         * 판정에는 영향을 주지 않는다 — 마커는 잘린 뒤의 줄에서도 계속 본다.
         */
        const val MAX_OUTPUT_CHARS = 64_000

        const val DRAIN_JOIN_MILLIS = 2_000L
    }
}
