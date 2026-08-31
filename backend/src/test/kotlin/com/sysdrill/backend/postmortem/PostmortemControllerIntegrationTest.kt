package com.sysdrill.backend.postmortem

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.support.startSession
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md step 26 — MTTD/MTTR and the action timeline are always recomputed
 * from AppliedAction rows via SimulationService.getTimeline (ADR-0011
 * lineage, step 25); this test drives real wall-clock time through incident
 * actions and a real session-completion flow, so per ADR-0014 discipline the
 * timing assertions are range/relative, not exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostmortemControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "postmortem-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun awaitSessionStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun submitAndWaitForFeedback(sessionId: UUID) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"그냥 API 서버 하나로 처리합니다."}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, SessionStatus.FEEDBACK_READY)
    }

    private fun completeSession(sessionId: UUID) {
        repeat(2) {
            submitAndWaitForFeedback(sessionId)
            mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk)
        }
        submitAndWaitForFeedback(sessionId)
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `a postmortem draft is available before the session completes, computed from applied actions alone`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident")).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionType":"${SimulationActionType.STRENGTHEN_RATE_LIMIT.name}"}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionType":"${SimulationActionType.INCREASE_CACHE_TTL.name}"}""")
        ).andExpect(status().isOk)

        val body = mockMvc.perform(get("/sessions/$sessionId/postmortem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(false))
            .andExpect(jsonPath("$.rootCause").doesNotExist())
            .andExpect(jsonPath("$.actionsTimeline.length()").value(2))
            .andExpect(jsonPath("$.actionsTimeline[0].actionType").value("STRENGTHEN_RATE_LIMIT"))
            .andExpect(jsonPath("$.actionsTimeline[1].actionType").value("INCREASE_CACHE_TTL"))
            .andReturn().response.contentAsString

        val mttd = JsonPath.read<Int>(body, "$.mttdSeconds")
        val mttr = JsonPath.read<Int>(body, "$.mttrSeconds")
        assertThat(mttd).isGreaterThanOrEqualTo(0)
        assertThat(mttr).isGreaterThanOrEqualTo(mttd)
        assertThat(JsonPath.read<Double>(body, "$.metricsBefore.errorRate")).isEqualTo(0.3)
    }

    @Test
    fun `the postmortem draft has no timing data for a session with no incident started`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(get("/sessions/$sessionId/postmortem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(false))
            .andExpect(jsonPath("$.mttdSeconds").doesNotExist())
            .andExpect(jsonPath("$.mttrSeconds").doesNotExist())
            .andExpect(jsonPath("$.actionsTimeline.length()").value(0))
    }

    @Test
    fun `saving a postmortem before the session completes is rejected`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(
            put("/sessions/$sessionId/postmortem").contentType(MediaType.APPLICATION_JSON)
                .content("""{"rootCause":"DB 커넥션 풀 고갈"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `saving a postmortem after completion persists the narrative while metrics stay freshly computed`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident")).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionType":"${SimulationActionType.INCREASE_DB_POOL.name}"}""")
        ).andExpect(status().isOk)

        completeSession(sessionId)

        mockMvc.perform(
            put("/sessions/$sessionId/postmortem").contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"rootCause":"DB 커넥션 풀 고갈로 대기 요청 누적",
                        |"mitigationActions":["DB 풀 크기 임시 증설"],
                        |"rootFixActions":["read replica 도입 예정"],
                        |"preventionItems":["풀 사용률 알림 추가"]}""".trimMargin()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(true))
            .andExpect(jsonPath("$.rootCause").value("DB 커넥션 풀 고갈로 대기 요청 누적"))
            .andExpect(jsonPath("$.mitigationActions[0]").value("DB 풀 크기 임시 증설"))

        mockMvc.perform(get("/sessions/$sessionId/postmortem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(true))
            .andExpect(jsonPath("$.rootCause").value("DB 커넥션 풀 고갈로 대기 요청 누적"))
            .andExpect(jsonPath("$.rootFixActions[0]").value("read replica 도입 예정"))
            .andExpect(jsonPath("$.preventionItems[0]").value("풀 사용률 알림 추가"))
            .andExpect(jsonPath("$.actionsTimeline.length()").value(1))
    }
}
