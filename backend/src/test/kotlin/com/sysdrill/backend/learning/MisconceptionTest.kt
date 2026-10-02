package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.AppliedAction
import com.sysdrill.backend.simulation.AppliedActionRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/** PLAN.md Round E29 (docs/LEARNING_EXPANSION_PLAN.md L10) — the same wrong first move three times becomes a card. */
@SpringBootTest
@AutoConfigureMockMvc
class MisconceptionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val appliedActionRepository: AppliedActionRepository,
) {
    private fun versionOf(domain: String) = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(
        scenarioRepository.findByOrganizationIdIsNull().first { it.creatorUserId == null && it.domain == domain }.id!!, "PUBLISHED",
    )!!.id!!

    private fun incident(user: UUID, domain: String, firstAction: String) {
        val now = Instant.now()
        val session = sessionRepository.save(
            Session(userId = user, scenarioVersionId = versionOf(domain), status = SessionStatus.COMPLETED, completedAt = now.plusSeconds(600))
        )
        appliedActionRepository.save(AppliedAction(sessionId = session.id!!, actionType = "INCIDENT_STARTED", effect = "인시던트 시작"))
        appliedActionRepository.save(AppliedAction(sessionId = session.id!!, actionType = firstAction, effect = "조치"))
    }

    private fun cards(user: UUID): String =
        mockMvc.perform(get("/learning/misconceptions").header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `three sessions opening with the same bad fix produce the domain card and the cross-domain one`() {
        val user = userRepository.save(User(email = "mis-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "mis")).id!!
        repeat(2) { incident(user, "coupon", "INCREASE_DB_POOL") }
        incident(user, "coupon", "STRENGTHEN_RATE_LIMIT")
        assertThat(JsonPath.read<List<String>>(cards(user), "$[*].key")).isEmpty()

        incident(user, "coupon", "INCREASE_DB_POOL")
        incident(user, "autoscaling", "SCALE_OUT_REPLICAS")
        val json = cards(user)
        assertThat(JsonPath.read<List<String>>(json, "$[*].key")).containsExactly("pool-fixes-it", "capacity-first")
        assertThat(JsonPath.read<String>(json, "$[0].evidence")).contains("4개 중 3번", "DB Pool 증가")
        assertThat(JsonPath.read<String>(json, "$[0].labSlug")).isEqualTo("engine-coupon-bottleneck")
        assertThat(JsonPath.read<String>(json, "$[0].failureDomain")).isEqualTo("coupon")
    }
}
