package com.sysdrill.backend.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Java/Kotlin/Go runtime coverage for [SandboxExecutor] (ADR-0051). What's new
 * for these languages is the compile step inside the container — /work is
 * read-only, so output must land in /tmp, and Kotlin needs its own CPU/memory
 * limits — so each language checks a pass, a failed assertion, and a compile
 * error. Network isolation and container cleanup are docker flags shared by
 * every runtime and stay covered in [SandboxExecutorTest].
 *
 * Needs the Kotlin/Go images built: `docker compose --profile sandbox-images build`.
 */
@SpringBootTest
class SandboxExecutorCompiledLanguagesTest(@Autowired val sandboxExecutor: SandboxExecutor) {

    enum class Case(
        val language: String,
        val sourceFileName: String,
        val correct: String,
        val wrong: String,
        val uncompilable: String,
        val testScript: String,
    ) {
        JAVA(
            "java",
            "Add.java",
            "public class Add { static int add(int a, int b) { return a + b; } }\n",
            "public class Add { static int add(int a, int b) { return a - b; } }\n",
            "public class Add { static int add(int a, int b) { return a + ; } }\n",
            """
            class RunTest {
                public static void main(String[] args) {
                    if (Add.add(2, 3) != 5) {
                        System.out.println("RESULT:FAIL:add(2, 3) should be 5");
                        System.exit(1);
                    }
                    System.out.println("RESULT:PASS");
                }
            }
            """.trimIndent(),
        ),
        KOTLIN(
            "kotlin",
            "Add.kt",
            "fun add(a: Int, b: Int) = a + b\n",
            "fun add(a: Int, b: Int) = a - b\n",
            "fun add(a: Int, b: Int) = a +\n",
            """
            @file:JvmName("RunTest")

            fun main() {
                if (add(2, 3) != 5) {
                    println("RESULT:FAIL:add(2, 3) should be 5")
                    System.exit(1)
                }
                println("RESULT:PASS")
            }
            """.trimIndent(),
        ),
        GO(
            "go",
            "add.go",
            "package main\n\nfunc add(a, b int) int { return a + b }\n",
            "package main\n\nfunc add(a, b int) int { return a - b }\n",
            "package main\n\nfunc add(a, b int) int { return a + }\n",
            """
            package main

            import (
                "fmt"
                "os"
            )

            func main() {
                if add(2, 3) != 5 {
                    fmt.Println("RESULT:FAIL:add(2, 3) should be 5")
                    os.Exit(1)
                }
                fmt.Println("RESULT:PASS")
            }
            """.trimIndent(),
        ),
    }

    @ParameterizedTest
    @EnumSource(Case::class)
    fun `a correct implementation passes its stage test`(case: Case) {
        val result = sandboxExecutor.run(case.language, case.sourceFileName, case.correct, case.testScript)
        assertThat(result.passed).describedAs(result.output).isTrue()
        assertThat(result.output).contains("RESULT:PASS")
    }

    @ParameterizedTest
    @EnumSource(Case::class)
    fun `an incorrect implementation fails with the test's message`(case: Case) {
        val result = sandboxExecutor.run(case.language, case.sourceFileName, case.wrong, case.testScript)
        assertThat(result.passed).isFalse()
        assertThat(result.output).contains("RESULT:FAIL:add(2, 3) should be 5")
    }

    /** A compile error never reaches the test — the learner sees the compiler's own output instead. */
    @ParameterizedTest
    @EnumSource(Case::class)
    fun `a submission that doesn't compile fails with the compiler output`(case: Case) {
        val result = sandboxExecutor.run(case.language, case.sourceFileName, case.uncompilable, case.testScript)
        assertThat(result.passed).isFalse()
        assertThat(result.output).doesNotContain("RESULT:").contains(case.sourceFileName)
    }
}
