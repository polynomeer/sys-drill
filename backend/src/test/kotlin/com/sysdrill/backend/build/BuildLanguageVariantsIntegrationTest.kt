package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * V73 — the Java/Kotlin/Go ports of queue, circuit-breaker, distributed-lock,
 * retry-backoff and event-bus — and V75/V76's cache, idempotency, consistent-hashing
 * and outbox in all four languages. Each port's stage tests were written fresh in
 * that language, so this pins both directions through the real pipeline: the
 * shipped stub (challenges/<slug>/) fails every stage, and a model answer
 * (src/test/resources/build-solutions/<slug>/) passes every stage — a stage test
 * that can't be passed, or that passes an empty stub, fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildLanguageVariantsIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {

    @ParameterizedTest
    @MethodSource("variants")
    fun `the stub fails every stage and the model answer passes every stage`(slug: String) {
        // A fresh user per variant — build submissions are rate limited per user.
        val userId = userRepository.save(
            User(email = "variant-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "variant")
        ).id!!
        val fileName = solutionDir(slug).listFiles()!!.single().name

        val stub = awaitCompleted(userId, mockMvc.submitBuildChallenge(objectMapper, slug, userId, File("../challenges/$slug/$fileName").readText()))
        assertThat(JsonPath.read<List<String>>(stub, "$.stages[*].status")).describedAs("$slug stub").allMatch { it == "FAILED" }

        val answer = awaitCompleted(userId, mockMvc.submitBuildChallenge(objectMapper, slug, userId, File(solutionDir(slug), fileName).readText()))
        assertThat(JsonPath.read<List<String>>(answer, "$.stages[*].status"))
            .describedAs("$slug model answer: ${JsonPath.read<List<String?>>(answer, "$.stages[*].output")}")
            .isNotEmpty.allMatch { it == "PASSED" }
    }

    /**
     * ADR-0052 — a Go queue with no locking at all hands out each message exactly once at 0.5 CPU,
     * so stage 4 would pass on outcome alone. Under the race detector it fails, with feedback that
     * says what happened instead of the tail of a stack trace.
     */
    @Test
    fun `a lock-free go queue fails the concurrency stage as a data race`() {
        val userId = userRepository.save(
            User(email = "racy-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "racy")
        ).id!!
        val racy = File("src/test/resources/build-solutions-racy/queue-go/queue.go").readText()

        val response = awaitCompleted(userId, mockMvc.submitBuildChallenge(objectMapper, "queue-go", userId, racy))
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).containsExactly("PASSED", "PASSED", "PASSED", "FAILED")
        assertThat(JsonPath.read<String>(response, "$.stages[3].feedback")).contains("data race")
        assertThat(JsonPath.read<String>(response, "$.stages[3].output")).contains("WARNING: DATA RACE")
    }

    private fun solutionDir(slug: String) = File("src/test/resources/build-solutions/$slug")

    private fun awaitCompleted(userId: UUID, submissionId: UUID, timeout: Duration = Duration.ofSeconds(240)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk).andReturn().response.contentAsString
            if (JsonPath.read<String>(response, "$.status") in setOf("COMPLETED", "ERROR")) return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    companion object {
        /** System property (`-PbuildVariants=…` in Gradle) that narrows the run — see [variants]. */
        const val VARIANTS_PROPERTY = "sysdrill.buildVariants"

        val ALL_VARIANTS: List<String> =
            listOf("queue", "circuit-breaker", "distributed-lock", "retry-backoff", "event-bus")
                .flatMap { family -> listOf("java", "kotlin", "go").map { "$family-$it" } } +
                // V75/V76 — new challenges carry a model answer for their Python original too.
                listOf("cache", "idempotency", "consistent-hashing", "outbox")
                    .flatMap { family -> listOf(family) + listOf("java", "kotlin", "go").map { "$family-$it" } }

        /** One per language, so a narrowed run still exercises every runtime end to end. */
        val SMOKE_VARIANTS = listOf("cache", "queue-java", "idempotency-kotlin", "outbox-go")

        /**
         * Every variant by default — a full run is ~11 minutes of real sandbox grading. With
         * [VARIANTS_PROPERTY] set (CI does this on pull requests, from the PR's changed paths —
         * scripts/select-build-variants.sh), only [SMOKE_VARIANTS] plus the listed slugs run.
         * `all` or an absent/blank property means everything; slugs without a model answer
         * (e.g. the Python originals of the older challenges) are ignored.
         */
        @JvmStatic
        fun variants(): List<String> {
            val requested = System.getProperty(VARIANTS_PROPERTY)?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
            if (requested.isNullOrEmpty() || "all" in requested) return ALL_VARIANTS
            val selected = SMOKE_VARIANTS.toSet() + requested
            return ALL_VARIANTS.filter { it in selected }
        }
    }
}
