package com.sysdrill.backend.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit

/** Exercises the real `docker run` sandbox (not mocked) — see PLAN.md step 9 notes. */
@SpringBootTest
class SandboxExecutorTest(@Autowired val sandboxExecutor: SandboxExecutor) {

    @Test
    fun `a correct implementation passes its stage test`() {
        val result = sandboxExecutor.run(
            "rate_limiter.py",
            PASSING_SOURCE,
            """
            from rate_limiter import add
            assert add(2, 3) == 5
            print("RESULT:PASS")
            """.trimIndent(),
        )
        assertThat(result.passed).isTrue()
        assertThat(result.output).contains("RESULT:PASS")
    }

    @Test
    fun `an incorrect implementation fails with the assertion message`() {
        val result = sandboxExecutor.run(
            "rate_limiter.py",
            "def add(a, b):\n    return a - b\n",
            """
            from rate_limiter import add
            try:
                assert add(2, 3) == 5, "add(2, 3) should be 5"
                print("RESULT:PASS")
            except AssertionError as e:
                print(f"RESULT:FAIL:{e}")
            """.trimIndent(),
        )
        assertThat(result.passed).isFalse()
        assertThat(result.output).contains("RESULT:FAIL:add(2, 3) should be 5")
    }

    @Test
    fun `the sandbox has no outbound network access`() {
        val result = sandboxExecutor.run(
            "rate_limiter.py",
            "x = 1\n",
            """
            import socket
            try:
                socket.create_connection(("8.8.8.8", 53), timeout=2)
                print("RESULT:FAIL:network was reachable")
            except OSError:
                print("RESULT:PASS")
            """.trimIndent(),
        )
        assertThat(result.passed).isTrue()
    }

    /**
     * 제출이 SIGTERM 을 무시해도 컨테이너가 남지 않는지.
     *
     * 컨테이너 안의 `timeout` 은 기본적으로 SIGTERM 만 보낸다. 그것을 무시하는 제출은
     * 그 제한을 통과하고, 바깥의 `destroyForcibly` 는 `docker run` **클라이언트**만
     * 죽이므로 컨테이너가 CPU 를 태우며 영구히 남았다 — 제출자가 세 줄로 만들 수 있는
     * 상태였다. 증상이 오판이 아니라 **호스트가 조용히 잠식되는 것**이라 판정만 봐서는
     * 드러나지 않는다.
     */
    @Test
    fun `a submission that ignores SIGTERM leaves no container behind`() {
        val before = sandboxContainerNames()

        val result = sandboxExecutor.run(
            "rate_limiter.py",
            PASSING_SOURCE,
            """
            import signal, time
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            while True:
                time.sleep(1)
            """.trimIndent(),
        )

        assertThat(result.passed).isFalse()
        assertThat(sandboxContainerNames() - before)
            .describedAs("sandbox containers left running after the deadline")
            .isEmpty()
    }

    /**
     * 출력이 파이프 버퍼를 넘겨도 timeout 으로 오판하지 않는지.
     *
     * `waitFor` 로 먼저 기다린 뒤 읽는 순서에서는, 출력이 약 64KB 를 넘기는 순간
     * 컨테이너가 write 에서 막히고 그것이 데드라인 초과로 나타났다. 제출은 멀쩡한데
     * "sandbox timed out" 을 받는, 출력량에만 의존하는 오판이다.
     */
    @Test
    fun `a chatty submission is not misjudged as a timeout`() {
        val result = sandboxExecutor.run(
            "rate_limiter.py",
            PASSING_SOURCE,
            """
            from rate_limiter import add
            for _ in range(4000):
                print("x" * 60)
            assert add(2, 3) == 5
            print("RESULT:PASS")
            """.trimIndent(),
        )

        assertThat(result.output).doesNotContain("sandbox timed out")
        assertThat(result.passed).isTrue()
    }

    /** 지금 이 호스트에 남아 있는 샌드박스 컨테이너. */
    private fun sandboxContainerNames(): Set<String> {
        val process = ProcessBuilder(
            "docker", "ps", "--all", "--no-trunc",
            "--filter", "name=${SandboxExecutor.CONTAINER_PREFIX}",
            "--format", "{{.Names}}",
        ).start()
        val rows = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return rows.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private companion object {
        const val PASSING_SOURCE = "def add(a, b):\n    return a + b\n"
    }
}
