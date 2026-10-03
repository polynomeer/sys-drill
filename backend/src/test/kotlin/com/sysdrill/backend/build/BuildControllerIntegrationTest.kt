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
import java.io.File
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
        // V49 — the raw sandbox output and timing are kept for the /bridge test log panel.
        assertThat(JsonPath.read<String>(response, "$.stages[0].output")).contains("RESULT:FAIL")
        assertThat(JsonPath.read<Int>(response, "$.stages[0].durationMs")).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `the challenge endpoint lists every stage with instructions before any submission`() {
        for (slug in listOf("rate-limiter", "rate-limiter-ts", "rate-limiter-java", "rate-limiter-kotlin", "rate-limiter-go")) {
            val response = mockMvc.perform(get("/build-challenges/$slug").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk).andReturn().response.contentAsString
            assertThat(JsonPath.read<List<Int>>(response, "$.stages[*].stageOrder")).containsExactly(1, 2, 3, 4, 5, 6)
            assertThat(JsonPath.read<List<String?>>(response, "$.stages[*].instructions")).allMatch { !it.isNullOrBlank() }
            assertThat(JsonPath.read<String>(response, "$.stages[0].instructions")).contains("목표")
        }
    }

    @Test
    fun `the challenge endpoint 404s for an unknown slug`() {
        mockMvc.perform(get("/build-challenges/does-not-exist").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isNotFound)
    }

    /**
     * PLAN.md Round B11 — the shipped stub (challenges/…, mirrored in /bridge)
     * fails stage 1 as-is and passes it once the commented-out lines under
     * "Stage 1 — uncomment" are uncommented; for Python, stage 2 stays a real
     * task because the stub's expire() is a no-op.
     */
    @Test
    fun `the python stub passes stage 1 only after uncommenting, and stage 2 is still open`() {
        val stub = File("../challenges/rate-limiter/rate_limiter.py").readText()

        val raw = awaitCompleted(submit(stub), Duration.ofSeconds(240))
        assertThat(JsonPath.read<String>(raw, "$.stages[0].status")).isEqualTo("FAILED")

        val uncommented = awaitCompleted(submit(uncommentStageOne(stub, lines = 4)), Duration.ofSeconds(240))
        assertThat(JsonPath.read<String>(uncommented, "$.stages[0].status")).isEqualTo("PASSED")
        assertThat(JsonPath.read<String>(uncommented, "$.stages[1].status")).isEqualTo("FAILED")
    }

    @Test
    fun `the typescript stub passes stage 1 after uncommenting`() {
        val stub = File("../challenges/rate-limiter-ts/rate_limiter.ts").readText()
        val submissionId = mockMvc.submitBuildChallenge(objectMapper, "rate-limiter-ts", userId, uncommentStageOne(stub, lines = 3))
        val response = awaitCompleted(submissionId, Duration.ofSeconds(240))
        assertThat(JsonPath.read<String>(response, "$.stages[0].status")).isEqualTo("PASSED")
    }

    /**
     * ADR-0051 — the compiled-language stubs follow the same contract, plus one more step the
     * instructions spell out: the not-implemented line after the uncommented ones must go
     * (Java rejects it as unreachable code). Stage 2 stays open as in Python — expire() is a no-op.
     */
    @Test
    fun `the java, kotlin and go stubs pass stage 1 only after uncommenting, and stage 2 is still open`() {
        val stubs = mapOf(
            "rate-limiter-java" to ("RateLimiter.java" to "throw new UnsupportedOperationException"),
            "rate-limiter-kotlin" to ("RateLimiter.kt" to "TODO(\"not implemented\")"),
            "rate-limiter-go" to ("rate_limiter.go" to "panic(\"not implemented\")"),
        )
        stubs.forEach { (slug, file) ->
            val (fileName, notImplemented) = file
            val stub = File("../challenges/$slug/$fileName").readText()

            val raw = awaitCompleted(mockMvc.submitBuildChallenge(objectMapper, slug, userId, stub), Duration.ofSeconds(240))
            assertThat(JsonPath.read<String>(raw, "$.stages[0].status")).describedAs(slug).isEqualTo("FAILED")

            val solved = uncommentStageOne(stub, lines = 3, dropFirstLineContaining = notImplemented)
            val uncommented = awaitCompleted(mockMvc.submitBuildChallenge(objectMapper, slug, userId, solved), Duration.ofSeconds(240))
            assertThat(JsonPath.read<String>(uncommented, "$.stages[0].status")).describedAs(slug).isEqualTo("PASSED")
            assertThat(JsonPath.read<String>(uncommented, "$.stages[1].status")).describedAs(slug).isEqualTo("FAILED")
        }
    }

    /**
     * Strips the first "# " / "// " from the [lines] lines right after the "Stage 1 — uncomment" marker,
     * then drops the first later line containing [dropFirstLineContaining], if given.
     */
    private fun uncommentStageOne(source: String, lines: Int, dropFirstLineContaining: String? = null): String {
        val all = source.lines().toMutableList()
        val marker = all.indexOfFirst { it.contains("Stage 1 — uncomment") }
        check(marker >= 0) { "stub has no Stage 1 marker" }
        for (i in marker + 1..marker + lines) {
            all[i] = all[i].replaceFirst("# ", "").replaceFirst("// ", "")
        }
        if (dropFirstLineContaining != null) {
            val stubLine = (marker + lines + 1 until all.size).first { all[it].contains(dropFirstLineContaining) }
            all.removeAt(stubLine)
        }
        return all.joinToString("\n")
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
