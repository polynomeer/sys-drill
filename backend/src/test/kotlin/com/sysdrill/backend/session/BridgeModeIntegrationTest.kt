package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Bridge Mode (PLAN.md step 10): Build(Rate Limiter) -> Design -> Tail Design
 * -> Wargame as one continuous flow, tied together by sessions.build_submission_id
 * and surfaced back in the session's report. Drives the real async Build
 * pipeline (real docker run per stage, see BuildControllerIntegrationTest)
 * and the real evaluation pipeline end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BridgeModeIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "bridge-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submitBuild(ownerId: UUID): UUID =
        mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", ownerId, STUB_RATE_LIMITER, commitRef = "bridge-test")

    private fun awaitBuildCompleted(submissionId: UUID, ownerId: UUID, timeout: Duration = Duration.ofSeconds(60)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId").header("Authorization", bearerHeader(ownerId)))
                .andExpect(status().isOk).andReturn().response.contentAsString
            if (JsonPath.read<String>(response, "$.status") == "COMPLETED") return
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    private fun awaitSessionStatus(sessionId: UUID, expected: String, timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk).andReturn().response.contentAsString
            if (JsonPath.read<String>(response, "$.status") == expected) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $expected within $timeout")
    }

    private fun submitAndWaitForFeedback(sessionId: UUID) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"rawText":"그냥 API 서버 하나로 처리합니다."}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, "FEEDBACK_READY")
    }

    @Test
    fun `starting a session rejects a build submission owned by a different user`() {
        val otherUserId = userRepository.save(
            User(email = "other-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "someone-else")
        ).id!!
        val submissionId = submitBuild(otherUserId)
        awaitBuildCompleted(submissionId, otherUserId)

        val body = """{"scenarioId":"${COUPON_SCENARIO_ID}","buildSubmissionId":"$submissionId"}"""
        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content(body)
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `starting a session rejects a build submission that has not completed`() {
        val body = """{"scenarioId":"${COUPON_SCENARIO_ID}","buildSubmissionId":"${UUID.randomUUID()}"}"""
        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content(body)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `Build then Design-TailDesign-Wargame links the report to the build submission`() {
        val submissionId = submitBuild(userId)
        awaitBuildCompleted(submissionId, userId)

        val sessionId = mockMvc.startSession(userId, buildSubmissionId = submissionId)
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.buildSubmissionId").value(submissionId.toString()))

        repeat(2) {
            submitAndWaitForFeedback(sessionId)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)
        }
        submitAndWaitForFeedback(sessionId)
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        val reportResponse = mockMvc.perform(get("/sessions/$sessionId/report").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.buildSummary.submissionId").value(submissionId.toString()))
            .andExpect(jsonPath("$.buildSummary.challengeTitle").value("Build your own Rate Limiter"))
            .andExpect(jsonPath("$.buildSummary.totalStages").value(6))
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Int>(reportResponse, "$.buildSummary.score")).isEqualTo(0)
    }

    private companion object {
        // Mirrors challenges/rate-limiter/rate_limiter.py's stub — every method
        // raises NotImplementedError, so every stage fails but the submission
        // still reaches COMPLETED (score 0). Bridge Mode only requires
        // COMPLETED, not a passing score, so this keeps the test fast.
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
