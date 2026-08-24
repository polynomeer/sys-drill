package com.sysdrill.backend.evaluation.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Uses the real Spring-autoconfigured ObjectMapper (not a hand-built one) so
 * this actually verifies what production gets — in particular, that Kotlin
 * data class default parameter values (e.g. `missedPoints: List<String> =
 * emptyList()`) are honored for JSON fields the model omits, which depends
 * on jackson-module-kotlin being registered the same way Spring wires it.
 */
@SpringBootTest
class LlmEvaluationResultParserTest(@Autowired val parser: LlmEvaluationResultParser) {

    @Test
    fun `parses a plain JSON response`() {
        val result = parser.parse(
            """{"totalScore": 72, "rubricScores": {"아키텍처 적합성": 20}, "strengths": ["good split"]}"""
        )

        assertThat(result.totalScore).isEqualTo(72)
        assertThat(result.rubricScores).containsEntry("아키텍처 적합성", 20)
        assertThat(result.strengths).containsExactly("good split")
    }

    @Test
    fun `fields the model omits fall back to their Kotlin default (empty), not a parse failure`() {
        val result = parser.parse("""{"totalScore": 72}""")

        assertThat(result.rubricScores).isEmpty()
        assertThat(result.strengths).isEmpty()
        assertThat(result.missedPoints).isEmpty()
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val result = parser.parse(
            """
            ```json
            {"totalScore": 50, "rubricScores": {}}
            ```
            """.trimIndent()
        )

        assertThat(result.totalScore).isEqualTo(50)
    }

    @Test
    fun `throws a clear error for malformed JSON`() {
        assertThatThrownBy { parser.parse("not json at all") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Failed to parse LLM evaluation JSON")
    }
}
