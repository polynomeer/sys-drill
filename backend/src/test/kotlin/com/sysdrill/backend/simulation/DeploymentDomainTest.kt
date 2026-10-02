package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.evaluation.RuleEvaluator
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** PLAN.md Round E30 (docs/DRILLS_EXPANSION_PLAN.md M9, ADR-0049) — the canary rollout domain and the change review. */
@SpringBootTest
@AutoConfigureMockMvc
class DeploymentDomainTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val domain = RuleBasedSimulationEngine.DOMAIN_DEPLOYMENT

    private fun errorAt(second: Long, traits: DesignTraits = DesignTraits(), actions: List<TimedAction> = emptyList()): Double =
        TelemetrySampler.sampleAt(domain, traits, start, actions, listOf(start.plusSeconds(second))).single().state.errorRate

    @Test
    fun `the canary spreads with time unless paused, and rollback ends it`() {
        // 10% → 20% → 40% … every 60s; after the 90s ramp the error rate is share × 60%.
        assertThat(errorAt(100)).isCloseTo(0.001 + 0.20 * 0.6, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(errorAt(200)).isCloseTo(0.001 + 0.80 * 0.6, org.assertj.core.data.Offset.offset(1e-9))
        val paused = listOf(TimedAction(start.plusSeconds(30), SimulationActionType.PAUSE_ROLLOUT))
        assertThat(errorAt(200, actions = paused)).isCloseTo(0.001 + 0.10 * 0.6, org.assertj.core.data.Offset.offset(1e-9))
        val rolledBack = listOf(TimedAction(start.plusSeconds(100), SimulationActionType.ROLLBACK))
        assertThat(errorAt(200, actions = rolledBack)).isEqualTo(0.001)
        val promoted = listOf(TimedAction(start.plusSeconds(95), SimulationActionType.CONTINUE_ROLLOUT))
        assertThat(errorAt(100, actions = promoted)).isGreaterThan(errorAt(100))
    }

    @Test
    fun `a designed auto-rollback bar rolls back by itself`() {
        val guarded = DesignTraits(autoRollbackErrorPct = 10)
        // 20% canary = 12% errors ≥ 10% from second 60 → rolled back 30s later.
        assertThat(errorAt(100, guarded)).isEqualTo(0.001)
        assertThat(errorAt(100)).isGreaterThan(0.1)
    }

    @Test
    fun `the deployment rubric knows its two concepts`() {
        assertThat(RuleEvaluator.evaluate("롤링 배포", domain).map { it.riskKey }).containsExactlyInAnyOrder("MISSING_CANARY_ANALYSIS", "MISSING_ROLLBACK_PLAN")
        assertThat(RuleEvaluator.evaluate("카나리 5%로 시작하고 에러율 임계를 넘으면 자동 롤백", domain)).isEmpty()
    }

    @Test
    fun `the riskiest change is picked before the incident and revealed after recovery`() {
        val user = userRepository.save(User(email = "deploy-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "deploy")).id!!
        val sessionId = mockMvc.startSession(user, UUID.fromString("b5000000-0000-0000-0000-000000000002"))
        fun review() = mockMvc.perform(get("/sessions/$sessionId/change-review").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        fun pick(id: String) = mockMvc.perform(
            put("/sessions/$sessionId/change-review").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user)).content("""{"pick":"$id"}""")
        )
        assertThat(JsonPath.read<List<String>>(review(), "$.changes[*].id")).contains("retry", "price-rounding")
        pick("nope").andExpect(status().isBadRequest)
        pick("retry").andExpect(status().isOk)

        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        pick("ttl").andExpect(status().isConflict)
        val during = review()
        assertThat(JsonPath.read<String>(during, "$.pick")).isEqualTo("retry")
        assertThat(JsonPath.read<Any?>(during, "$.culpritId")).isNull()

        jdbcTemplate.update("update applied_actions set created_at = created_at - interval '120 seconds' where session_id = ?", sessionId)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user)).content("""{"actionType":"ROLLBACK"}""")
        ).andExpect(status().isOk)
        mockMvc.perform(post("/sessions/$sessionId/simulation/resolve").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        assertThat(JsonPath.read<String>(review(), "$.culpritId")).isEqualTo("price-rounding")

        val logs = mockMvc.perform(get("/sessions/$sessionId/simulation/logs").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(logs).contains("PriceCalculator")
    }
}
