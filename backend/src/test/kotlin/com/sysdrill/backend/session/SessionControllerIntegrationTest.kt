package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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
import java.util.UUID

/**
 * Exercises the session HTTP API end to end against the real local Postgres
 * (Flyway V2 seeds the "선착순 쿠폰" scenario used below). Evaluation itself is
 * out of scope for this step, so the FEEDBACK_READY -> * leg of `advance` is
 * verified by forcing that state directly through the repository, matching
 * PLAN.md step 2's "AI 평가 없이 상태 전이만 검증" scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val sessionPhaseRepository: SessionPhaseRepository,
) {
    private val couponScenarioId = UUID.fromString("a0000000-0000-0000-0000-000000000002")
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

    private fun startSession(): UUID {
        val body = """{"userId":"$userId","scenarioId":"$couponScenarioId"}"""
        val response = mockMvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.currentPhase").value("INITIAL"))
            .andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    @Test
    fun `starting a session returns the first scenario step in IN_PROGRESS`() {
        startSession()
    }

    @Test
    fun `starting a session for an unknown scenario returns 404`() {
        val body = """{"userId":"$userId","scenarioId":"${UUID.randomUUID()}"}"""
        mockMvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `submitting an answer moves the session to SUBMITTED`() {
        val sessionId = startSession()

        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"Load Balancer -> API -> Redis -> Postgres"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.phase").value("INITIAL"))

        mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
    }

    @Test
    fun `submitting twice without a client request id is rejected as a conflict`() {
        val sessionId = startSession()
        val body = """{"rawText":"first submission"}"""

        mockMvc.perform(post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)

        mockMvc.perform(post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
    }

    @Test
    fun `resubmitting with the same client request id is idempotent`() {
        val sessionId = startSession()
        val body = """{"rawText":"first submission","clientRequestId":"retry-key-1"}"""

        val firstResponse = mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        val secondResponse = mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        assert(firstResponse == secondResponse) { "expected idempotent replay to return the same submission" }

        mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
    }

    @Test
    fun `advance is rejected while a session is still IN_PROGRESS`() {
        val sessionId = startSession()
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `advance moves a FEEDBACK_READY session to its next step`() {
        val sessionId = startSession()
        forceFeedbackReady(sessionId)

        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.currentPhase").value("FOLLOWUP"))
    }

    @Test
    fun `advance completes a FEEDBACK_READY session on its last step`() {
        val sessionId = startSession()
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

    /** Simulates "as if the (not-yet-built) evaluation worker had succeeded". */
    private fun forceFeedbackReady(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId).orElseThrow()
        session.status = SessionStatus.FEEDBACK_READY
        sessionRepository.save(session)
    }
}
