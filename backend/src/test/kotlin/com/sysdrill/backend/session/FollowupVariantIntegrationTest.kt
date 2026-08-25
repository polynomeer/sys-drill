package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.SkillProfileService
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md step 12: a FOLLOWUP step's content can hold multiple authored
 * variants (`{"variants": [...]}`), selected either adaptively (targeting
 * the user's most frequent RuleEvaluator weakness) or, absent a clear
 * weakness signal, deterministically from the session's seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FollowupVariantIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val skillProfileService: SkillProfileService,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "variant-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun awaitSessionStatus(sessionId: UUID, expected: String, timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/sessions/$sessionId")).andReturn().response.contentAsString
            if (JsonPath.read<String>(response, "$.status") == expected) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $expected within $timeout")
    }

    private fun advanceToFollowup(sessionId: UUID) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"그냥 API 서버 하나로 처리합니다."}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, "FEEDBACK_READY")
        mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk)
    }

    private fun currentPrompt(sessionId: UUID): String =
        JsonPath.read(mockMvc.perform(get("/sessions/$sessionId")).andReturn().response.contentAsString, "$.currentStepPrompt")

    @Test
    fun `the same seed picks the same FOLLOWUP variant for a user with no weakness signal`() {
        val sessionA = mockMvc.startSession(userId, COUPON_SCENARIO_ID, seed = "reused-seed")
        advanceToFollowup(sessionA)
        val promptA = currentPrompt(sessionA)

        val otherUserId = userRepository.save(
            User(email = "variant-other-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "someone-else")
        ).id!!
        val sessionB = mockMvc.startSession(otherUserId, COUPON_SCENARIO_ID, seed = "reused-seed")
        advanceToFollowup(sessionB)
        val promptB = currentPrompt(sessionB)

        assertThat(promptA).isEqualTo(promptB)
    }

    @Test
    fun `a recorded weakness selects the variant that targets it, regardless of seed`() {
        // Two evaluations' worth of MISSING_RATE_LIMIT so it dominates any other riskKey this user might pick up.
        skillProfileService.recordEvaluation(userId, listOf("MISSING_RATE_LIMIT", "MISSING_RATE_LIMIT"), totalScore = 60)

        val sessionId = mockMvc.startSession(userId, COUPON_SCENARIO_ID, seed = "irrelevant-because-weakness-wins")
        advanceToFollowup(sessionId)

        assertThat(currentPrompt(sessionId)).contains("매크로/봇")
    }
}
