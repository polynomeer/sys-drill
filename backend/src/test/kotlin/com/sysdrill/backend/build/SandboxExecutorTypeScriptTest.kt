package com.sysdrill.backend.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit

/**
 * TypeScript runtime coverage for [SandboxExecutor], added alongside the
 * rate-limiter-ts challenge (`V44__seed_rate_limiter_typescript_challenge.sql`).
 * Mirrors [SandboxExecutorTest]'s pass/fail/network/SIGTERM cases for the
 * `node --experimental-strip-types` runtime — the drain-timing/chatty-output
 * case is language-agnostic logic in [SandboxExecutor] itself and isn't
 * duplicated here (already covered once, for Python, in [SandboxExecutorTest]).
 */
@SpringBootTest
class SandboxExecutorTypeScriptTest(@Autowired val sandboxExecutor: SandboxExecutor) {

    @Test
    fun `a correct TypeScript implementation passes its stage test`() {
        val result = sandboxExecutor.run(
            "typescript",
            "add.ts",
            PASSING_SOURCE,
            """
            import { add } from "./add.ts";
            if (add(2, 3) !== 5) throw new Error("add(2, 3) should be 5");
            console.log("RESULT:PASS");
            """.trimIndent(),
        )
        assertThat(result.passed).isTrue()
        assertThat(result.output).contains("RESULT:PASS")
    }

    @Test
    fun `an incorrect TypeScript implementation fails with the error message`() {
        val result = sandboxExecutor.run(
            "typescript",
            "add.ts",
            "export function add(a: number, b: number): number {\n  return a - b;\n}\n",
            """
            import { add } from "./add.ts";
            try {
              if (add(2, 3) !== 5) throw new Error("add(2, 3) should be 5");
              console.log("RESULT:PASS");
            } catch (e) {
              console.log(`RESULT:FAIL:${'$'}{(e as Error).message}`);
            }
            """.trimIndent(),
        )
        assertThat(result.passed).isFalse()
        assertThat(result.output).contains("RESULT:FAIL:add(2, 3) should be 5")
    }

    @Test
    fun `the TypeScript sandbox has no outbound network access`() {
        val result = sandboxExecutor.run(
            "typescript",
            "add.ts",
            PASSING_SOURCE,
            """
            import net from "node:net";
            const socket = net.createConnection({ host: "8.8.8.8", port: 53 });
            socket.setTimeout(2000);
            socket.on("connect", () => {
              console.log("RESULT:FAIL:network was reachable");
              socket.destroy();
            });
            socket.on("timeout", () => {
              console.log("RESULT:PASS");
              socket.destroy();
            });
            socket.on("error", () => {
              console.log("RESULT:PASS");
            });
            """.trimIndent(),
        )
        assertThat(result.passed).isTrue()
    }

    /** [SandboxExecutorTest]의 같은 이름 테스트와 같은 목적 — SIGTERM을 무시해도 컨테이너가 남지 않는지, Node 런타임에서도 확인. */
    @Test
    fun `a TypeScript submission that ignores SIGTERM leaves no container behind`() {
        val before = sandboxContainerNames()

        val result = sandboxExecutor.run(
            "typescript",
            "add.ts",
            PASSING_SOURCE,
            """
            process.on("SIGTERM", () => {});
            setInterval(() => {}, 1000);
            """.trimIndent(),
        )

        assertThat(result.passed).isFalse()
        assertThat(sandboxContainerNames() - before)
            .describedAs("sandbox containers left running after the deadline")
            .isEmpty()
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
        const val PASSING_SOURCE = "export function add(a: number, b: number): number {\n  return a + b;\n}\n"
    }
}
