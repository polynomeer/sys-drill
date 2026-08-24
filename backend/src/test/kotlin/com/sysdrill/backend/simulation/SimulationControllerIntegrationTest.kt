package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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
                .content("""{"actionType":"${action.name}"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `getting simulation state before an incident has started is a 404`() {
        val sessionId = mockMvc.startSession(userId)
        mockMvc.perform(get("/sessions/$sessionId/simulation/state"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `starting the incident degrades metrics, and the three actions recover it`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trafficRps").value(6000.0))
            .andExpect(jsonPath("$.errorRate").value(0.3))

        applyAction(sessionId, SimulationActionType.STRENGTHEN_RATE_LIMIT)
        applyAction(sessionId, SimulationActionType.INCREASE_CACHE_TTL)

        mockMvc.perform(post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
            .content("""{"actionType":"INCREASE_DB_POOL"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errorRate").value(0.001))
            .andExpect(jsonPath("$.p95LatencyMs").value(80.0))

        val finalState = mockMvc.perform(get("/sessions/$sessionId/simulation/state"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Double>(finalState, "$.dbReadLoad")).isLessThan(0.6)
        assertThat(JsonPath.read<Double>(finalState, "$.dbWriteLoad")).isLessThan(0.6)
    }
}
