package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/COMMERCIALIZATION.md — BuildRunnerWorker moved from a single
 * background thread to a configurable pool (sysdrill.build.worker-concurrency).
 * Doesn't assert on `Thread.getAllStackTraces()` (see EvaluationWorkerConcurrencyTest's
 * kdoc for why that's unsound under the full test suite's cached-context
 * reuse). Instead starts two submissions back-to-back (not awaiting the
 * first before starting the second) and confirms both still complete
 * correctly -- real docker sandbox runs per stage, so this is slow like
 * BuildControllerIntegrationTest's tests, but it's the actual thing worth
 * proving: concurrent processing doesn't corrupt either submission's result.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildRunnerWorkerConcurrencyTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun workerConcurrency(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.build.worker-concurrency") { "2" }
        }

        // Mirrors BuildControllerIntegrationTest's STUB_RATE_LIMITER -- an
        // unimplemented stub that fails every stage. Correctness of pass/fail
        // grading isn't the point here (already covered elsewhere); reaching
        // COMPLETED for both submissions under concurrent processing is.
        private val STUB = """
            class InMemoryStore:
                def __init__(self):
                    self._data = {}
                def incr(self, key):
                    raise NotImplementedError
                def expire(self, key, seconds):
                    raise NotImplementedError

            class FaultyStore:
                def incr(self, key):
                    raise ConnectionError("store unavailable")
                def expire(self, key, seconds):
                    raise ConnectionError("store unavailable")

            class RateLimiter:
                def __init__(self, capacity, window_seconds=1.0, store=None, fail_mode="open"):
                    raise NotImplementedError
                def allow(self, key):
                    raise NotImplementedError
                @property
                def metrics(self):
                    raise NotImplementedError
        """.trimIndent()
    }

    private fun awaitCompleted(userId: UUID, submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk).andReturn().response.contentAsString
            val status = JsonPath.read<String>(response, "$.status")
            if (status == "COMPLETED" || status == "ERROR") return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    @Test
    fun `two submissions started back-to-back both complete correctly under raised worker concurrency`() {
        val userA = userRepository.save(User(email = "concurrency-a-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "a")).id!!
        val userB = userRepository.save(User(email = "concurrency-b-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "b")).id!!

        val submissionA = mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", userA, STUB)
        val submissionB = mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", userB, STUB)

        val responseA = awaitCompleted(userA, submissionA)
        val responseB = awaitCompleted(userB, submissionB)

        assertThat(JsonPath.read<String>(responseA, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(responseA, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<String>(responseB, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(responseB, "$.score")).isEqualTo(0)
    }
}
