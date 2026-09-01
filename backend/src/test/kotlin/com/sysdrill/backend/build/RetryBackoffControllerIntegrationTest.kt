package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async Build pipeline for the "retry-backoff" challenge
 * (PLAN.md step 16), mirroring the coverage established for
 * "rate-limiter"/"queue"/"circuit-breaker"/"distributed-lock".
 */
@SpringBootTest
@AutoConfigureMockMvc
class RetryBackoffControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "retrybackoff-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID =
        mockMvc.submitBuildChallenge(objectMapper, "retry-backoff", userId, sourceCode)

    private fun awaitCompleted(submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId").header("Authorization", bearerHeader(userId))).andReturn().response.contentAsString
            val status = JsonPath.read<String>(response, "$.status")
            if (status == "COMPLETED" || status == "ERROR") return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    @Test
    fun `a correct retry-backoff implementation passes all four stages`() {
        val submissionId = submit(CORRECT_RETRY_BACKOFF)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(4)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
    }

    @Test
    fun `an unimplemented stub fails every stage`() {
        val submissionId = submit(STUB_RETRY_BACKOFF)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    private companion object {
        // Mirrors challenges/retry-backoff/retry_backoff.py's reference solution —
        // verified against the real sandbox before being written into the seeded
        // stage tests (see PLAN.md step 16 notes).
        val CORRECT_RETRY_BACKOFF = """
            import random
            import time

            class RetryExhaustedError(Exception):
                pass

            class RetryBudget:
                def __init__(self, capacity=10):
                    self.capacity = capacity
                    self._remaining = capacity

                def try_consume(self):
                    if self._remaining <= 0:
                        return False
                    self._remaining -= 1
                    return True

            class RetryPolicy:
                def __init__(self, max_attempts=5, base_delay=0.01, max_delay=1.0, budget=None, sleep_fn=None):
                    self.max_attempts = max_attempts
                    self.base_delay = base_delay
                    self.max_delay = max_delay
                    self.budget = budget
                    self.sleep_fn = sleep_fn if sleep_fn is not None else time.sleep

                def _delay_for(self, attempt):
                    capped = min(self.max_delay, self.base_delay * (2 ** attempt))
                    return random.uniform(0, capped)

                def execute(self, fn, *args, **kwargs):
                    last_exc = None
                    for attempt in range(self.max_attempts):
                        try:
                            return fn(*args, **kwargs)
                        except Exception as e:
                            last_exc = e
                            if attempt == self.max_attempts - 1:
                                break
                            if self.budget is not None and not self.budget.try_consume():
                                break
                            self.sleep_fn(self._delay_for(attempt))
                    raise RetryExhaustedError("exhausted after retrying") from last_exc
        """.trimIndent()

        val STUB_RETRY_BACKOFF = """
            class RetryExhaustedError(Exception):
                pass

            class RetryBudget:
                def __init__(self, capacity=10):
                    raise NotImplementedError
                def try_consume(self):
                    raise NotImplementedError

            class RetryPolicy:
                def __init__(self, max_attempts=5, base_delay=0.01, max_delay=1.0, budget=None, sleep_fn=None):
                    raise NotImplementedError
                def execute(self, fn, *args, **kwargs):
                    raise NotImplementedError
        """.trimIndent()
    }
}
