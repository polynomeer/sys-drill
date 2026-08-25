package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.NOTIFICATION_SCENARIO_ID
import com.sysdrill.backend.support.PRODUCT_BROWSING_SCENARIO_ID
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
 * PLAN.md step 11's "E2E 테스트: 시나리오 3종 각각 Design→꼬리설계→Wargame→Report 완주".
 * The coupon scenario's full loop is already covered end to end by
 * ReportAndSkillProfileIntegrationTest (step 8) and by a real browser
 * walkthrough (step 7's PLAN.md notes); this class gives the two new
 * scenarios (notification, product-browsing) the same coverage — Design ->
 * FOLLOWUP -> INCIDENT/Wargame (domain-specific simulation actions) ->
 * COMPLETED -> report — so all three MVP scenarios are proven, not just
 * their pure simulation math (SimulationEngineTest covers that already).
 *
 * Numeric SystemState fields are read via JsonPath + AssertJ rather than
 * jsonPath(...).value(Matchers.closeTo(...)) — see PLAN.md step 4 notes:
 * Hamcrest's closeTo misbehaves against the BigDecimal JsonPath returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MvpScenarioE2ETest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID
    private val delta = Offset.offset(0.001)

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "e2e-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
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

    private fun submitAndWaitForFeedback(sessionId: UUID, rawText: String) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"${rawText}"}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, "FEEDBACK_READY")
    }

    private fun applyAction(sessionId: UUID, actionType: String) =
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionType":"$actionType"}""")
        )

    @Test
    fun `notification scenario completes Design, FOLLOWUP, and Wargame with domain-specific actions`() {
        val sessionId = mockMvc.startSession(userId, scenarioId = NOTIFICATION_SCENARIO_ID)
        mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("notification"))

        submitAndWaitForFeedback(sessionId, "이벤트를 큐에 넣고 비동기로 처리합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk)

        submitAndWaitForFeedback(sessionId, "컨슈머 수를 늘려서 대응합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPhase").value("INCIDENT"))

        val incidentResponse = mockMvc.perform(post("/sessions/$sessionId/simulation/incident"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Long>(incidentResponse, "$.queueLag")).isGreaterThan(0)

        // an out-of-domain action (coupon's) must be rejected for a notification session
        applyAction(sessionId, "STRENGTHEN_RATE_LIMIT").andExpect(status().isConflict)

        applyAction(sessionId, "ENABLE_CIRCUIT_BREAKER").andExpect(status().isOk)
        applyAction(sessionId, "ADD_CONSUMERS").andExpect(status().isOk)
        val recovered = applyAction(sessionId, "ADJUST_RETRY_BACKOFF")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Long>(recovered, "$.queueLag")).isEqualTo(0)
        assertThat(JsonPath.read<Double>(recovered, "$.errorRate")).isCloseTo(0.001, delta)

        submitAndWaitForFeedback(sessionId, "Circuit Breaker와 컨슈머 증설, Retry Backoff 조정으로 대응했습니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
    }

    @Test
    fun `product-browsing scenario completes Design, FOLLOWUP, and Wargame with domain-specific actions`() {
        val sessionId = mockMvc.startSession(userId, scenarioId = PRODUCT_BROWSING_SCENARIO_ID)
        mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("product-browsing"))

        submitAndWaitForFeedback(sessionId, "가격/재고/리뷰를 캐시에 저장하고 조회합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance")).andExpect(status().isOk)

        submitAndWaitForFeedback(sessionId, "가격은 짧은 TTL, 리뷰는 긴 TTL로 분리합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPhase").value("INCIDENT"))

        val incidentResponse = mockMvc.perform(post("/sessions/$sessionId/simulation/incident"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(incidentResponse, "$.cacheHitRatio")).isCloseTo(0.2, delta)

        // an out-of-domain action (notification's) must be rejected for a product-browsing session
        applyAction(sessionId, "ADD_CONSUMERS").andExpect(status().isConflict)

        applyAction(sessionId, "SPLIT_CACHE_POLICY").andExpect(status().isOk)
        applyAction(sessionId, "ENABLE_SINGLE_FLIGHT").andExpect(status().isOk)
        val recovered = applyAction(sessionId, "ADD_READ_REPLICA")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(recovered, "$.dbReadLoad")).isCloseTo(0.5, delta)
        assertThat(JsonPath.read<Double>(recovered, "$.errorRate")).isCloseTo(0.001, delta)

        submitAndWaitForFeedback(sessionId, "캐시 정책 분리, single-flight, read replica로 대응했습니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
    }
}
