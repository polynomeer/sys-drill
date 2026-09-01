package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Drives the simulation HTTP API end to end: start a session for the seeded
 * "선착순 쿠폰" scenario, trigger its incident, watch metrics degrade, apply
 * the three PLAN.md step 4 actions, and confirm recovery — the exact flow
 * step 4's completion criterion asks for.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimulationControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "sim-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun applyAction(sessionId: UUID, action: SimulationActionType) {
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"actionType":"${action.name}"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `getting simulation state before an incident has started is a 404`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(get("/sessions/$sessionId/simulation/state").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `starting the incident degrades metrics, and the three actions recover it`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trafficRps").value(6000.0))
            .andExpect(jsonPath("$.errorRate").value(0.3))

        applyAction(sessionId, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        applyAction(sessionId, SimulationActionType.INCREASE_CACHE_TTL)

        mockMvc.perform(post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(userId))
            .content("""{"actionType":"INCREASE_DB_POOL"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errorRate").value(0.001))
            .andExpect(jsonPath("$.p95LatencyMs").value(80.0))

        val finalState = mockMvc.perform(get("/sessions/$sessionId/simulation/state").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Double>(finalState, "$.dbReadLoad")).isLessThan(0.6)
        assertThat(JsonPath.read<Double>(finalState, "$.dbWriteLoad")).isLessThan(0.6)
    }

    @Test
    fun `the timeline replays each step's metrics for a rule-based session, from AppliedAction rows alone`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)
        applyAction(sessionId, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        applyAction(sessionId, SimulationActionType.INCREASE_CACHE_TTL)
        applyAction(sessionId, SimulationActionType.INCREASE_DB_POOL)

        val timeline = mockMvc.perform(get("/sessions/$sessionId/simulation/timeline").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Int>(timeline, "$.length()")).isEqualTo(4)

        // Step 0 — synthetic "incident started" step, no action, matches the raw incident state.
        assertThat(JsonPath.read<Any?>(timeline, "$[0].actionType")).isNull()
        assertThat(JsonPath.read<Double>(timeline, "$[0].systemState.trafficRps")).isEqualTo(6000.0)
        assertThat(JsonPath.read<Double>(timeline, "$[0].systemState.errorRate")).isEqualTo(0.3)

        // Steps 1-3 — each stored action, replayed in order.
        assertThat(JsonPath.read<String>(timeline, "$[1].actionType")).isEqualTo("STRENGTHEN_RATE_LIMIT")
        assertThat(JsonPath.read<String>(timeline, "$[2].actionType")).isEqualTo("INCREASE_CACHE_TTL")
        assertThat(JsonPath.read<String>(timeline, "$[3].actionType")).isEqualTo("INCREASE_DB_POOL")

        // Final replayed step matches the live /state endpoint's already-verified recovery numbers.
        assertThat(JsonPath.read<Double>(timeline, "$[3].systemState.errorRate")).isEqualTo(0.001)
        assertThat(JsonPath.read<Double>(timeline, "$[3].systemState.p95LatencyMs")).isEqualTo(80.0)
    }

    @Test
    fun `the timeline is empty for a session with no incident started`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(get("/sessions/$sessionId/simulation/timeline").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
