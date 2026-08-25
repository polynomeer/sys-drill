package com.sysdrill.backend.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

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

    private companion object {
        const val PASSING_SOURCE = "def add(a, b):\n    return a + b\n"
    }
}
