package com.sysdrill.backend.simulation.realinfra

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.util.UUID

/**
 * PLAN.md step 25 — real-infra sessions can't replay their timeline by
 * recomputation (ADR-0016), so this confirms each step's real measurement
 * was actually captured in AppliedAction.parameters at the time it happened,
 * not silently dropped or recomputed into something implausible. RANDOM_PORT,
 * not the MOCK environment most controller tests use — same reason as
 * RealInfraCouponEngineTest: CouponLoadRunner needs a real `local.server.port`
 * to reach the app from inside its k6 container; MockMvc still dispatches
 * requests the normal (simulated, no real socket) way regardless.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RealInfraCouponTimelineTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val toxiproxy: ToxiproxySessionProxy,
) {
    private lateinit var userId: UUID
    private val provisionedSessions = mutableListOf<UUID>()

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "realinfra-timeline-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @AfterEach
    fun cleanUp() {
        provisionedSessions.forEach {
            dataSourceRegistry.evict(it)
            toxiproxy.evict(it)
            schemaProvisioner.drop(it)
        }
        provisionedSessions.clear()
    }

    @Test
    fun `the timeline holds a real captured snapshot per step, not a recomputed one`() {
        val sessionId = mockMvc.startSession(userId).also { provisionedSessions += it }

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident?realInfra=true").header("Authorization", bearerHeader(userId))).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"actionType":"STRENGTHEN_RATE_LIMIT"}""")
        ).andExpect(status().isOk)

        val timeline = mockMvc.perform(get("/sessions/$sessionId/simulation/timeline").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Int>(timeline, "$.length()")).isEqualTo(2)
        assertThat(JsonPath.read<Any?>(timeline, "$[0].actionType")).isNull()
        assertThat(JsonPath.read<String>(timeline, "$[1].actionType")).isEqualTo("STRENGTHEN_RATE_LIMIT")

        // Real measurements, not formula output — same plausibility-range
        // philosophy as ADR-0014, not exact values.
        for (step in 0..1) {
            assertThat(JsonPath.read<Double>(timeline, "$[$step].systemState.trafficRps")).isGreaterThan(0.0)
            assertThat(JsonPath.read<Double>(timeline, "$[$step].systemState.errorRate")).isBetween(0.0, 1.0)
            assertThat(JsonPath.read<Double>(timeline, "$[$step].systemState.externalDependencyLatencyMs"))
                .isEqualTo(toxiproxy.configuredLatencyMs.toDouble())
        }
    }
}
