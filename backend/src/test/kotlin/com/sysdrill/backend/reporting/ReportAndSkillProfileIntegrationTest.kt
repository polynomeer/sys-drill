package com.sysdrill.backend.reporting

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.startSession
import org.hamcrest.Matchers.containsString
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives a full session to completion (INITIAL -> FOLLOWUP -> INCIDENT ->
 * COMPLETED, mirroring the real browser walkthrough from PLAN.md step 7) to
 * verify step 8's two pieces land: ReportService synthesizes a report on
 * completion, and SkillProfileService accumulates repeated RuleEvaluator
 * findings + a score trend across all three evaluations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportAndSkillProfileIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "report-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
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
        error("Session $sessionId did not reach $expected in time")
    }

    /** Deliberately omits every RuleEvaluator concept so weakness counts accumulate predictably. */
    private fun submitAndWaitForFeedback(sessionId: UUID) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"그냥 API 서버 하나로 처리합니다."}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, SessionStatus.FEEDBACK_READY)
    }

    @Test
    fun `completing all three steps generates a report and updates the skill profile`() {
        val sessionId = mockMvc.startSession(userId)

        repeat(2) {
            submitAndWaitForFeedback(sessionId)
            mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk)
        }
        submitAndWaitForFeedback(sessionId)
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
            .andExpect(jsonPath("$.timelineFeedback[0].phase").value("INITIAL"))
            .andExpect(jsonPath("$.timelineFeedback[1].phase").value("FOLLOWUP"))
            .andExpect(jsonPath("$.timelineFeedback[2].phase").value("INCIDENT"))
            .andExpect(jsonPath("$.summary").value(containsString("3개 단계")))

        mockMvc.perform(get("/users/$userId/skill-profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weaknesses.MISSING_IDEMPOTENCY").value(3))
            .andExpect(jsonPath("$.weaknesses.MISSING_CONCURRENCY_CONTROL").value(3))
            .andExpect(jsonPath("$.weaknesses.MISSING_RATE_LIMIT").value(3))
            .andExpect(jsonPath("$.weaknesses.MISSING_OBSERVABILITY").value(3))
            .andExpect(jsonPath("$.trend.length()").value(3))

        mockMvc.perform(get("/users/$userId/sessions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].scenarioTitle").value("선착순 쿠폰"))
    }

    @Test
    fun `report is not found before the session completes`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(get("/sessions/$sessionId/report")).andExpect(status().isNotFound)
    }

    @Test
    fun `skill profile defaults to empty for a user with no evaluations yet`() {
        mockMvc.perform(get("/users/${UUID.randomUUID()}/skill-profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weaknesses").isEmpty)
            .andExpect(jsonPath("$.trend").isEmpty)
    }
}
