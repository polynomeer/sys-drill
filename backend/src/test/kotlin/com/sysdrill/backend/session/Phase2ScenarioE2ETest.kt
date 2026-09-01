package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.BATCH_SETTLEMENT_SCENARIO_ID
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.PAYMENT_SCENARIO_ID
import com.sysdrill.backend.support.RESERVATION_SCENARIO_ID
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
 * PLAN.md steps 18/19/20's "추가 시나리오" completion criterion — same pattern
 * as MvpScenarioE2ETest (step 11), covering scenarios added after the
 * Phase 1 MVP (주문/결제, 예약 시스템, ...). Numeric SystemState fields are
 * read via JsonPath + AssertJ rather than jsonPath(...).value(Matchers.closeTo(...))
 * — see PLAN.md step 4 notes on Hamcrest's closeTo misbehaving against the
 * BigDecimal JsonPath returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
class Phase2ScenarioE2ETest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID
    private val delta = Offset.offset(0.001)

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "phase2-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun awaitSessionStatus(sessionId: UUID, expected: String, timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId))).andReturn().response.contentAsString
            if (JsonPath.read<String>(response, "$.status") == expected) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $expected within $timeout")
    }

    private fun submitAndWaitForFeedback(sessionId: UUID, rawText: String) {
        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"rawText":"${rawText}"}""")
        ).andExpect(status().isCreated)
        awaitSessionStatus(sessionId, "FEEDBACK_READY")
    }

    private fun applyAction(sessionId: UUID, actionType: String) =
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"actionType":"$actionType"}""")
        )

    @Test
    fun `payment scenario completes Design, FOLLOWUP, and Wargame with domain-specific actions`() {
        val sessionId = mockMvc.startSession(userId, scenarioId = PAYMENT_SCENARIO_ID)
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("payment"))

        submitAndWaitForFeedback(sessionId, "outbox 테이블에 이벤트를 기록하고 별도 디스패처가 PG를 호출합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)

        submitAndWaitForFeedback(sessionId, "멱등성 키로 이중 결제를 방지합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPhase").value("INCIDENT"))

        val incidentResponse = mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(incidentResponse, "$.errorRate")).isCloseTo(0.30, delta)

        // an out-of-domain action (product-browsing's) must be rejected for a payment session
        applyAction(sessionId, "ADD_READ_REPLICA").andExpect(status().isConflict)

        applyAction(sessionId, "ADD_DISPATCHER_WORKERS").andExpect(status().isOk)
        applyAction(sessionId, "ENABLE_IDEMPOTENT_PG_RETRY").andExpect(status().isOk)
        val recovered = applyAction(sessionId, "ISOLATE_PAYMENT_POOL")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(recovered, "$.connectionPoolUsage")).isCloseTo(0.2, delta)
        assertThat(JsonPath.read<Double>(recovered, "$.errorRate")).isCloseTo(0.001, delta)

        submitAndWaitForFeedback(sessionId, "디스패처 증설, 멱등성 키, 커넥션 풀 격리로 대응했습니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
    }

    @Test
    fun `payment FOLLOWUP variant targets the user's recorded weakness over the session seed`() {
        // Mentions idempotency and retry/backoff explicitly (so those concepts
        // aren't flagged) but says nothing about outbox/saga/transaction
        // boundaries, so MISSING_TRANSACTION_BOUNDARY is the sole recorded
        // weakness — no tie with another concept for selectVariant to break.
        val primerSessionId = mockMvc.startSession(userId, scenarioId = PAYMENT_SCENARIO_ID, seed = "primer")
        submitAndWaitForFeedback(primerSessionId, "멱등성 idempotency key로 중복 결제를 막고 retry backoff로 재시도합니다.")

        val sessionId = mockMvc.startSession(userId, scenarioId = PAYMENT_SCENARIO_ID, seed = "irrelevant-because-weakness-wins")
        submitAndWaitForFeedback(sessionId, "멱등성 키와 retry backoff로 대응합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentStepPrompt").value(org.hamcrest.Matchers.containsString("하나의 트랜잭션으로 묶을 수 없다")))
    }

    @Test
    fun `reservation scenario completes Design, FOLLOWUP, and Wargame with domain-specific actions`() {
        val sessionId = mockMvc.startSession(userId, scenarioId = RESERVATION_SCENARIO_ID)
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("reservation"))

        submitAndWaitForFeedback(sessionId, "좌석별로 락을 걸고 재고를 compare-and-swap으로 원자적으로 확정합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)

        submitAndWaitForFeedback(sessionId, "예약 홀드 타임아웃을 짧게 설정해 자동 해제합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPhase").value("INCIDENT"))

        val incidentResponse = mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(incidentResponse, "$.errorRate")).isCloseTo(0.30, delta)
        assertThat(JsonPath.read<Long>(incidentResponse, "$.queueLag")).isEqualTo(880L)

        // an out-of-domain action (coupon's) must be rejected for a reservation session
        applyAction(sessionId, "STRENGTHEN_RATE_LIMIT").andExpect(status().isConflict)

        applyAction(sessionId, "ENABLE_FINE_GRAINED_LOCKING").andExpect(status().isOk)
        applyAction(sessionId, "SHORTEN_HOLD_TIMEOUT").andExpect(status().isOk)
        val recovered = applyAction(sessionId, "ENABLE_ATOMIC_INVENTORY_CHECK")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Long>(recovered, "$.queueLag")).isEqualTo(0L)
        assertThat(JsonPath.read<Double>(recovered, "$.errorRate")).isCloseTo(0.001, delta)

        submitAndWaitForFeedback(sessionId, "락 세분화, 홀드 타임아웃 단축, 원자적 재고 확인으로 대응했습니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
    }

    @Test
    fun `reservation FOLLOWUP variant targets the user's recorded weakness over the session seed`() {
        // Mentions locking and inventory consistency explicitly (so those
        // concepts aren't flagged) but says nothing about hold timeouts, so
        // MISSING_RESERVATION_TIMEOUT is the sole recorded weakness.
        val primerSessionId = mockMvc.startSession(userId, scenarioId = RESERVATION_SCENARIO_ID, seed = "primer")
        submitAndWaitForFeedback(primerSessionId, "락으로 좌석 잠금을 관리하고 재고를 원자적으로 compare-and-swap으로 확정합니다.")

        val sessionId = mockMvc.startSession(userId, scenarioId = RESERVATION_SCENARIO_ID, seed = "irrelevant-because-weakness-wins")
        submitAndWaitForFeedback(sessionId, "락과 compare-and-swap으로 대응합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentStepPrompt").value(org.hamcrest.Matchers.containsString("결제를 완료하지 않은 채 이탈")))
    }

    @Test
    fun `batch-settlement scenario completes Design, FOLLOWUP, and Wargame with domain-specific actions`() {
        val sessionId = mockMvc.startSession(userId, scenarioId = BATCH_SETTLEMENT_SCENARIO_ID)
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("batch-settlement"))

        submitAndWaitForFeedback(sessionId, "레코드를 청크 단위로 분할 처리하고 체크포인트로 실패 지점부터 재개합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)

        submitAndWaitForFeedback(sessionId, "재처리 시 이미 반영된 레코드는 정합성을 위해 멱등하게 건너뜁니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPhase").value("INCIDENT"))

        val incidentResponse = mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Double>(incidentResponse, "$.errorRate")).isCloseTo(0.6, delta)
        assertThat(JsonPath.read<Long>(incidentResponse, "$.queueLag")).isEqualTo(600000L)

        // an out-of-domain action (coupon's) must be rejected for a batch-settlement session
        applyAction(sessionId, "STRENGTHEN_RATE_LIMIT").andExpect(status().isConflict)

        applyAction(sessionId, "ENABLE_CHECKPOINT_RESTART").andExpect(status().isOk)
        applyAction(sessionId, "REDUCE_CHUNK_SIZE").andExpect(status().isOk)
        val recovered = applyAction(sessionId, "ENABLE_IDEMPOTENT_RECONCILIATION")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<Long>(recovered, "$.queueLag")).isEqualTo(1000L)
        assertThat(JsonPath.read<Double>(recovered, "$.errorRate")).isCloseTo(0.0, delta)

        submitAndWaitForFeedback(sessionId, "체크포인트 재개, 청크 축소, 멱등한 재처리로 대응했습니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get("/sessions/$sessionId/report").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timelineFeedback.length()").value(3))
    }

    @Test
    fun `batch-settlement FOLLOWUP variant targets the user's recorded weakness over the session seed`() {
        // Mentions chunking and checkpoint/restart explicitly (so those
        // concepts aren't flagged) but says nothing about reconciliation, so
        // MISSING_RECONCILIATION is the sole recorded weakness — no tie with
        // another concept for selectVariant to break.
        val primerSessionId = mockMvc.startSession(userId, scenarioId = BATCH_SETTLEMENT_SCENARIO_ID, seed = "primer")
        submitAndWaitForFeedback(primerSessionId, "레코드를 작은 단위로 분할 처리하고, 실패 시 체크포인트부터 재개합니다.")

        val sessionId = mockMvc.startSession(userId, scenarioId = BATCH_SETTLEMENT_SCENARIO_ID, seed = "irrelevant-because-weakness-wins")
        submitAndWaitForFeedback(sessionId, "청크 단위로 분할 처리하고 체크포인트로 재개합니다.")
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentStepPrompt").value(org.hamcrest.Matchers.containsString("중복 반영")))
    }
}
