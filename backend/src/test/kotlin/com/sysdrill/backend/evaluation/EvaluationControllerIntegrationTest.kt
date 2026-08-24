package com.sysdrill.backend.evaluation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.startSession
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
 * End to end for PLAN.md step 5's completion criterion: "선착순 쿠폰 시나리오
 * 제출에 대해 Rule+AI 하이브리드 평가 결과가 구조화된 형태로 저장되고 조회 API로
 * 확인 가능하다." No LLM_ANTHROPIC_API_KEY is configured here, so the "AI" half
 * comes from AnthropicLlmClient's offline fallback (see its kdoc) — the rule
 * engine half and the storage/retrieval plumbing are exercised for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "feedback-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun awaitSessionStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected within $timeout")
    }

    @Test
    fun `a hybrid evaluation is stored with rule findings and the offline AI result, and readable via the feedback API`() {
        val sessionId = mockMvc.startSession(userId)

        // Mentions none of RuleEvaluator's four concepts, so all four should be flagged.
        val submitResponse = mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"API 서버 하나로 쿠폰 발급을 처리합니다."}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val submissionId = JsonPath.read<String>(submitResponse, "$.id")

        awaitSessionStatus(sessionId, SessionStatus.FEEDBACK_READY)

        mockMvc.perform(get("/submissions/$submissionId/feedback"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalScore").value(60)) // offline fallback's canned score
            .andExpect(jsonPath("$.rubricScores['아키텍처 적합성']").value(12))
            .andExpect(jsonPath("$.modelProvider").value("anthropic"))
            .andExpect(jsonPath("$.modelName").value("offline-fallback"))
            .andExpect(jsonPath("$.riskFlags.length()").value(4)) // RuleEvaluator's four MISSING_* findings
            .andExpect(jsonPath("$.riskFlags[?(@.riskKey == 'MISSING_IDEMPOTENCY')]").exists())
            .andExpect(jsonPath("$.strengths[0]").value("오프라인 모드: 실제 LLM 평가가 아닙니다."))
    }

    @Test
    fun `feedback for a submission with no evaluation yet is a 404`() {
        mockMvc.perform(get("/submissions/${UUID.randomUUID()}/feedback"))
            .andExpect(status().isNotFound)
    }
}
