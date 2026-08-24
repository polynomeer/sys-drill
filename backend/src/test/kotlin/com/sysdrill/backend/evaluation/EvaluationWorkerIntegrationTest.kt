package com.sysdrill.backend.evaluation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.evaluation.llm.AnthropicLlmClient
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async pipeline (submit -> EvaluationRequestPublisher ->
 * Redis queue -> EvaluationWorker -> HybridRuleAiEvaluator -> Evaluation row
 * -> session transition) end to end. No LLM_ANTHROPIC_API_KEY is configured
 * in this test environment, so AnthropicLlmClient runs in its offline
 * fallback mode (see its kdoc) — the pipeline mechanics are still exercised
 * for real; only the "AI" half is canned rather than a live call.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationWorkerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val evaluationRepository: EvaluationRepository,
    @Autowired val evaluationQueue: EvaluationQueue,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "worker-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submitRawText(sessionId: UUID, rawText: String): UUID {
        val escaped = rawText.replace("\"", "\\\"")
        val response = mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"$escaped"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun awaitSessionStatus(
        sessionId: UUID,
        expected: SessionStatus,
        timeout: Duration = Duration.ofSeconds(10),
    ) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        val actual = sessionRepository.findById(sessionId).orElseThrow().status
        throw AssertionError("Session $sessionId did not reach $expected within $timeout (last seen: $actual)")
    }

    @Test
    fun `the async pipeline evaluates a submission and moves the session to FEEDBACK_READY`() {
        val sessionId = mockMvc.startSession(userId)
        val submissionId = submitRawText(sessionId, "Load Balancer -> API -> Redis -> Postgres")

        awaitSessionStatus(sessionId, SessionStatus.FEEDBACK_READY)

        assertThat(evaluationRepository.existsBySubmissionIdAndIsActiveTrue(submissionId)).isTrue()
    }

    @Test
    fun `a submission that always fails exhausts retries, is dead-lettered, and fails the session`() {
        val sessionId = mockMvc.startSession(userId)
        val submissionId = submitRawText(sessionId, "please ${AnthropicLlmClient.FORCE_FAILURE_MARKER}")

        awaitSessionStatus(sessionId, SessionStatus.EVALUATION_FAILED)

        assertThat(evaluationRepository.existsBySubmissionIdAndIsActiveTrue(submissionId)).isFalse()
        assertThat(evaluationQueue.deadLetterCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `duplicate delivery of the same job is processed at most once`() {
        val sessionId = mockMvc.startSession(userId)
        val submissionId = submitRawText(sessionId, "Load Balancer -> API -> Redis -> Postgres")

        // Simulate an at-least-once redelivery racing the real job the API already enqueued.
        evaluationQueue.enqueue(submissionId)

        awaitSessionStatus(sessionId, SessionStatus.FEEDBACK_READY)
        Thread.sleep(300) // let any duplicate in-flight processing settle

        assertThat(evaluationRepository.findBySubmissionId(submissionId).count { it.isActive }).isEqualTo(1)
    }
}
