package com.sysdrill.backend.simulation.realinfra

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.simulation.SimulationStateStore
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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
    @Autowired val stateStore: SimulationStateStore,
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

    /**
     * ADR-0037 gap fix — real-infra mode used to always start from
     * RealInfraCouponEngine.INITIAL_DB_POOL_SIZE (4), silently discarding a
     * saved SystemTopology the moment the user flipped the real-infra toggle.
     * 10 is deliberately outside both the rule-based default (50) and the
     * real-infra undersized default (4), and within max-db-pool-size (20,
     * see application.yml) so the real HikariCP pool this test provisions
     * actually gets sized to it.
     */
    @Test
    fun `starting a real-infra coupon incident honors a saved topology's dbPoolSize`() {
        val sessionId = mockMvc.startSession(userId).also { provisionedSessions += it }
        // Two connected nodes, not one — deriveDesignTraits only aggregates nodes
        // with at least one edge attaching them to something else on the canvas
        // (an orphaned node is decorative and excluded), same setup as
        // SimulationControllerIntegrationTest's topology tests.
        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content(
                    """{"graph":"{\"nodes\":[{\"id\":\"n1\",\"data\":{\"kind\":\"db\",\"traitValues\":{\"dbPoolSize\":10}}},{\"id\":\"n2\",\"data\":{\"kind\":\"db\",\"traitValues\":{}}}],\"edges\":[{\"source\":\"n1\",\"target\":\"n2\"}]}"}"""
                )
        ).andExpect(status().isOk)

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident?realInfra=true").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)

        assertThat(stateStore.find(sessionId)?.traits?.dbPoolSize).isEqualTo(10)
    }
}
