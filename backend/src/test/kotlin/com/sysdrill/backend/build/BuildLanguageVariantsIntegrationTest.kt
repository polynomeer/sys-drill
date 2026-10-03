package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
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
 * retry-backoff and event-bus. Each port's stage tests were written fresh in that
 * language, so this pins both directions through the real pipeline: the shipped
 * stub (challenges/<slug>/) fails every stage, and a model answer
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
        @JvmStatic
        fun variants(): List<String> =
            listOf("queue", "circuit-breaker", "distributed-lock", "retry-backoff", "event-bus")
                .flatMap { family -> listOf("java", "kotlin", "go").map { "$family-$it" } }
    }
}
