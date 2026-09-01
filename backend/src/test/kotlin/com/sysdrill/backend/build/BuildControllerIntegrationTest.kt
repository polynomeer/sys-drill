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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async pipeline (submit -> BuildJobQueue -> BuildRunnerWorker
 * -> 6x real `docker run` sandbox executions -> BuildStageResult rows) end to
 * end, per PLAN.md step 9's completion criterion. Slow by this codebase's
 * standards (~6 container starts per submission) but exercises the real
 * sandbox, not a mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "build-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID =
        mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", userId, sourceCode)

    private fun awaitCompleted(submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
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
    fun `a correct rate limiter implementation passes all six stages`() {
        val submissionId = submit(CORRECT_RATE_LIMITER)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(6)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("학습 포인트")
    }

    @Test
    fun `an unimplemented stub fails every stage with concrete feedback`() {
        val submissionId = submit(STUB_RATE_LIMITER)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    @Test
    fun `submitting to an unknown challenge is a 404`() {
        val body = objectMapper.writeValueAsString(mapOf("sourceCode" to "x = 1"))
        mockMvc.perform(
            post("/build-challenges/does-not-exist/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content(body)
        ).andExpect(status().isNotFound)
    }

    private companion object {
        // Mirrors challenges/rate-limiter/rate_limiter.py's reference solution
        // (see PLAN.md step 9 notes — verified against the real sandbox before
        // being written into the seeded stage tests).
        val CORRECT_RATE_LIMITER = """
            import threading
            import time

            class InMemoryStore:
                def __init__(self):
                    self._data = {}
                    self._lock = threading.Lock()

                def incr(self, key):
                    with self._lock:
                        self._data[key] = self._data.get(key, 0) + 1
                        return self._data[key]

                def expire(self, key, seconds):
                    def _clear():
                        time.sleep(seconds)
                        with self._lock:
                            self._data.pop(key, None)
                    threading.Thread(target=_clear, daemon=True).start()

            class FaultyStore:
                def incr(self, key):
                    raise ConnectionError("store unavailable")
                def expire(self, key, seconds):
                    raise ConnectionError("store unavailable")

            class RateLimiter:
                def __init__(self, capacity, window_seconds=1.0, store=None, fail_mode="open"):
                    self.capacity = capacity
                    self.window_seconds = window_seconds
                    self.store = store if store is not None else InMemoryStore()
                    self.fail_mode = fail_mode
                    self._allowed = 0
                    self._rejected = 0
                    self._metrics_lock = threading.Lock()

                def allow(self, key):
                    try:
                        count = self.store.incr(key)
                        if count == 1:
                            self.store.expire(key, self.window_seconds)
                        admitted = count <= self.capacity
                    except Exception:
                        admitted = self.fail_mode == "open"
                    with self._metrics_lock:
                        if admitted:
                            self._allowed += 1
                        else:
                            self._rejected += 1
                    return admitted

                @property
                def metrics(self):
                    with self._metrics_lock:
                        total = self._allowed + self._rejected
                        reject_rate = self._rejected / total if total else 0.0
                        return {"allowed": self._allowed, "rejected": self._rejected, "reject_rate": reject_rate}
        """.trimIndent()

        val STUB_RATE_LIMITER = """
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
}
