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
    @Value("\${sysdrill.build.sandbox-image}") private val pythonImage: String,
    @Value("\${sysdrill.build.sandbox-image-typescript}") private val typescriptImage: String,
    @Value("\${sysdrill.build.sandbox-image-java}") private val javaImage: String,
    @Value("\${sysdrill.build.sandbox-image-kotlin}") private val kotlinImage: String,
    @Value("\${sysdrill.build.sandbox-image-go}") private val goImage: String,
    @Value("\${sysdrill.build.timeout-seconds}") private val timeoutSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * `BuildChallenge.languages` → 어떤 이미지·어떤 파일명으로 테스트 스크립트를
     * 써야 하는지·어떤 명령으로 실행할지. TOPOLOGY_FIELDS([com.sysdrill.backend.simulation.SystemTopologyService])와
     * 같은 작은 정적 설정 맵 패턴 — 언어 하나 늘 때마다 항목 하나만 추가하면 된다.
     */
    private val languageRuntimes: Map<String, LanguageRuntime> by lazy {
        mapOf(
            "python" to LanguageRuntime(pythonImage, "run_test.py", listOf("python3", "run_test.py")),
            // --experimental-strip-types는 타입만 벗겨낼 뿐 완전한 트랜스파일이 아니다 —
            // 생성자 파라미터 프로퍼티 등 일부 TS 문법은 지원하지 않는다(challenges/rate-limiter-ts/README.md 참고).
            "typescript" to LanguageRuntime(
                typescriptImage,
                "run_test.ts",
                listOf("node", "--experimental-strip-types", "run_test.ts"),
            ),
            // 컴파일 언어는 매 단계 컨테이너 안에서 컴파일부터 한다. /work 가 읽기 전용이라
            // 산출물은 /tmp 로 보내고, 컴파일 시간은 [LanguageRuntime.compileSeconds] 로 따로 준다.
            "java" to LanguageRuntime(
                javaImage,
                "RunTest.java",
                listOf("sh", "-c", "javac -d /tmp/classes *.java && java -cp /tmp/classes RunTest"),
                compileSeconds = 5,
            ),
            // kotlinc 는 128m 에서 OOM 으로 죽고 0.5 CPU 에서는 컴파일만 7초가 걸린다 — 이 언어만
            // 한도를 올린다. 힙(-Xmx256m) 바깥의 메타스페이스·네이티브 메모리까지 들어가야 해서 384m 는
            // 경계선이었다(통과하다가 cgroup OOM 으로 죽기 시작함). 측정치와 근거는 ADR-0051.
            "kotlin" to LanguageRuntime(
                kotlinImage,
                "RunTest.kt",
                listOf("sh", "-c", "kotlinc -nowarn *.kt -d /tmp/run.jar && java -cp /tmp/run.jar:\$KOTLIN_STDLIB RunTest"),
                cpus = "1.0",
                memory = "512m",
                compileSeconds = 10,
            ),
            // `_test.go` 로 끝나는 파일은 `go run` 이 받지 않는다. 이미지에 표준 라이브러리 빌드
            // 캐시를 미리 채워 둔 이유는 sandbox/go/Dockerfile.
            // -race: 0.5 CPU 에서는 고루틴이 거의 겹치지 않아, 락 없는 구현도 결과값만 보면 대개
            // 통과한다. 레이스 디텍터는 겹침 운과 무관하게 동기화 없는 접근을 잡는다(ADR-0052).
            "go" to LanguageRuntime(
                goImage,
                "run_stage.go",
                listOf("sh", "-c", "go run -race *.go"),
                compileSeconds = 5,
            ),
        )
    }

    fun run(language: String, sourceFileName: String, sourceCode: String, testScript: String): SandboxResult {
        val runtime = languageRuntimes[language]
            ?: throw IllegalArgumentException("Unknown build challenge language: $language")
        val workDir = Files.createTempDirectory("sysdrill-build-")
        return try {
            workDir.resolve(sourceFileName).writeText(sourceCode)
            workDir.resolve(runtime.testFileName).writeText(testScript)
            execute(workDir, runtime)
        } finally {
            runCatching { workDir.toFile().deleteRecursively() }
                .onFailure { log.warn("Failed to clean up sandbox workdir {}: {}", workDir, it.message) }
        }
    }

    private fun execute(workDir: Path, runtime: LanguageRuntime): SandboxResult {
        val containerName = "$CONTAINER_PREFIX${UUID.randomUUID()}"
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--name", containerName,
            "--label", "$OWNER_LABEL=$OWNER_LABEL_VALUE",
            "--network", "none",
            "--cpus", runtime.cpus,
            "--memory", runtime.memory,
            "--pids-limit", "64",
            "-v", "$workDir:/work:ro",
            "-w", "/work",
            runtime.image,
            // 여기서 도는 것은 신뢰할 수 없는 제출 코드다. `timeout` 은 기본적으로
            // SIGTERM 만 보내므로, 그것을 무시하는 세 줄이면 이 제한을 그냥 통과한다.
            // --kill-after 가 그 뒤에 SIGKILL 을 보낸다.
            "timeout", "--kill-after=${KILL_AFTER_SECONDS}s", runtime.deadlineSeconds().toString(),
            *runtime.runCommand.toTypedArray(),
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
            val finished = process.waitFor(runtime.deadlineSeconds() + GRACE_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                SandboxResult(passed = false, output = "sandbox timed out after ${runtime.deadlineSeconds()}s")
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

    /** 실행 시간 한도 = 설정된 테스트 시간 + 컴파일 언어가 매번 치르는 컴파일 시간. */
    private fun LanguageRuntime.deadlineSeconds() = timeoutSeconds + compileSeconds

    private data class LanguageRuntime(
        val image: String,
        val testFileName: String,
        val runCommand: List<String>,
        val cpus: String = "0.5",
        val memory: String = "128m",
        val compileSeconds: Long = 0,
    )

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
