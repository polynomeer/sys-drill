package com.sysdrill.backend.session

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.startSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Exercises the session HTTP API end to end against the real local Postgres
 * (Flyway V2 seeds the "선착순 쿠폰" scenario used below). Since PLAN.md step 3,
 * a submission's SUBMITTED -> EVALUATING -> FEEDBACK_READY leg really runs
 * (see EvaluationWorkerIntegrationTest), so tests here that only care about
 * `advance`'s FEEDBACK_READY -> * leg force that state directly through the
 * repository instead of racing the async pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        val user = userRepository.save(
            User(
                email = "user-${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                nickname = "drill-user",
            )
        )
        userId = user.id!!
    }

    @Test
    fun `starting a session returns the first scenario step in IN_PROGRESS`() {
        val body = """{"userId":"$userId","scenarioId":"$COUPON_SCENARIO_ID"}"""
        mockMvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.currentPhase").value("INITIAL"))
            .andExpect(jsonPath("$.currentStepPrompt").value(org.hamcrest.Matchers.containsString("선착순")))
    }

    @Test
    fun `starting a session for an unknown scenario returns 404`() {
        val body = """{"userId":"$userId","scenarioId":"${UUID.randomUUID()}"}"""
        mockMvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `submitting an answer is accepted for the session's current phase`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"Load Balancer -> API -> Redis -> Postgres"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.phase").value("INITIAL"))
    }

    @Test
    fun `submitting twice without a client request id is rejected as a conflict`() {
        val sessionId = mockMvc.startSession(userId)
        val body = """{"rawText":"first submission"}"""

        mockMvc.perform(post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)

        mockMvc.perform(post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
    }

    @Test
    fun `resubmitting with the same client request id is idempotent`() {
        val sessionId = mockMvc.startSession(userId)
        val body = """{"rawText":"first submission","clientRequestId":"retry-key-1"}"""

        val firstResponse = mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        val secondResponse = mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        assert(firstResponse == secondResponse) { "expected idempotent replay to return the same submission" }
    }

    @Test
    fun `advance is rejected while a session is still IN_PROGRESS`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `advance moves a FEEDBACK_READY session to its next step`() {
        val sessionId = mockMvc.startSession(userId)
        forceFeedbackReady(sessionId)

        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.currentPhase").value("FOLLOWUP"))
    }

    @Test
    fun `advance completes a FEEDBACK_READY session on its last step`() {
        val sessionId = mockMvc.startSession(userId)
        forceFeedbackReady(sessionId)
        mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk) // -> FOLLOWUP
        forceFeedbackReady(sessionId)
        mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk) // -> INCIDENT
        forceFeedbackReady(sessionId)

        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").exists())
    }

    /** Isolates `advance`'s FEEDBACK_READY -> * leg from the real (but still stub) evaluation pipeline. */
    private fun forceFeedbackReady(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId).orElseThrow()
        session.status = SessionStatus.FEEDBACK_READY
        sessionRepository.save(session)
    }
}
