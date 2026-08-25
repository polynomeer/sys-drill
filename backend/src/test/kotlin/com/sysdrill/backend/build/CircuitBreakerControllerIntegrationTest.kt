package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async Build pipeline for the "circuit-breaker" challenge
 * (PLAN.md step 14), mirroring BuildControllerIntegrationTest/
 * QueueControllerIntegrationTest's coverage of "rate-limiter"/"queue".
 */
@SpringBootTest
@AutoConfigureMockMvc
class CircuitBreakerControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "circuitbreaker-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID {
        val body = objectMapper.writeValueAsString(
            mapOf("userId" to userId.toString(), "sourceCode" to sourceCode, "commitRef" to "test")
        )
        val response = mockMvc.perform(
            post("/build-challenges/circuit-breaker/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun awaitCompleted(submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId")).andReturn().response.contentAsString
            val status = JsonPath.read<String>(response, "$.status")
            if (status == "COMPLETED" || status == "ERROR") return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    @Test
    fun `a correct circuit breaker implementation passes all four stages`() {
        val submissionId = submit(CORRECT_CIRCUIT_BREAKER)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(4)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
    }

    @Test
    fun `an unimplemented stub fails every stage`() {
        val submissionId = submit(STUB_CIRCUIT_BREAKER)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    private companion object {
        // Mirrors challenges/circuit-breaker/circuit_breaker.py's reference solution —
        // verified against the real sandbox before being written into the seeded
        // stage tests (see PLAN.md step 14 notes).
        val CORRECT_CIRCUIT_BREAKER = """
            import threading
            import time

            class CircuitOpenError(Exception):
                pass

            class CircuitBreaker:
                def __init__(self, failure_threshold=3, recovery_timeout=5.0):
                    self.failure_threshold = failure_threshold
                    self.recovery_timeout = recovery_timeout
                    self._lock = threading.Lock()
                    self._failure_count = 0
                    self._state = "CLOSED"
                    self._opened_at = None

                @property
                def state(self):
                    with self._lock:
                        self._maybe_transition_to_half_open()
                        return self._state

                def _maybe_transition_to_half_open(self):
                    if self._state == "OPEN" and self._opened_at is not None:
                        if time.time() - self._opened_at >= self.recovery_timeout:
                            self._state = "HALF_OPEN"

                def call(self, fn, *args, **kwargs):
                    with self._lock:
                        self._maybe_transition_to_half_open()
                        if self._state == "OPEN":
                            raise CircuitOpenError("circuit is open")
                        trial = self._state == "HALF_OPEN"

                    try:
                        result = fn(*args, **kwargs)
                    except Exception:
                        with self._lock:
                            if trial:
                                self._state = "OPEN"
                                self._opened_at = time.time()
                            else:
                                self._failure_count += 1
                                if self._failure_count >= self.failure_threshold:
                                    self._state = "OPEN"
                                    self._opened_at = time.time()
                        raise

                    with self._lock:
                        self._failure_count = 0
                        self._state = "CLOSED"
                        self._opened_at = None
                    return result
        """.trimIndent()

        val STUB_CIRCUIT_BREAKER = """
            class CircuitOpenError(Exception):
                pass

            class CircuitBreaker:
                def __init__(self, failure_threshold=3, recovery_timeout=5.0):
                    raise NotImplementedError
                def call(self, fn, *args, **kwargs):
                    raise NotImplementedError
                @property
                def state(self):
                    raise NotImplementedError
        """.trimIndent()
    }
}
