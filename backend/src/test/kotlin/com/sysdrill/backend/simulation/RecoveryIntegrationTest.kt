package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.NOTIFICATION_SCENARIO_ID
import com.sysdrill.backend.support.PAYMENT_SCENARIO_ID
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md Round E13 (docs/DRILLS_EXPANSION_PLAN.md M5) — mitigation vs recovery.
 * Incidents are pushed minutes into the past with SQL so a backlog has had time to build.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecoveryIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "recovery-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "recovery")).id!!

    private fun startIncidentInPast(user: UUID, scenarioId: UUID, seconds: Int): UUID {
        val sessionId = mockMvc.startSession(user, scenarioId)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk)
        jdbcTemplate.update("update applied_actions set created_at = created_at - make_interval(secs => ?) where session_id = ?", seconds, sessionId)
        return sessionId
    }

    private fun apply(sessionId: UUID, user: UUID, action: String) = mockMvc.perform(
        post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"actionType":"$action"}""")
    ).andExpect(status().isOk)

    private fun resolve(sessionId: UUID, user: UUID): String = mockMvc.perform(
        post("/sessions/$sessionId/simulation/resolve").header("Authorization", bearerHeader(user))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    private fun getJson(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `symptoms fixed but backlog left is a partial recovery, declared once, without touching MTTR`() {
        val user = newUser()
        val sessionId = startIncidentInPast(user, NOTIFICATION_SCENARIO_ID, 300)
        listOf("ADD_CONSUMERS", "ENABLE_CIRCUIT_BREAKER", "ADJUST_RETRY_BACKOFF").forEach { apply(sessionId, user, it) }

        val preview = getJson("/sessions/$sessionId/simulation/recovery", user)
        assertThat(JsonPath.read<Boolean>(preview, "$.resolved")).isFalse()
        assertThat(JsonPath.read<Boolean>(preview, "$.symptomsOk")).isTrue()
        assertThat(JsonPath.read<Int>(preview, "$.backlog")).isGreaterThan(100_000)
        assertThat(JsonPath.read<String>(preview, "$.status")).isEqualTo("PARTIAL")

        val first = resolve(sessionId, user)
        assertThat(JsonPath.read<String>(first, "$.status")).isEqualTo("PARTIAL")
        assertThat(JsonPath.read<Int>(first, "$.resolvedSeconds")).isBetween(299, 310)
        val again = resolve(sessionId, user)
        assertThat(JsonPath.read<String>(again, "$.resolvedAt")).isEqualTo(JsonPath.read<String>(first, "$.resolvedAt"))

        // The marker is not a replay step and doesn't move MTTR.
        val timeline = getJson("/sessions/$sessionId/simulation/timeline", user)
        assertThat(JsonPath.read<List<String?>>(timeline, "$[*].actionType")).doesNotContain("INCIDENT_RESOLVED").hasSize(4)
        val postmortem = getJson("/sessions/$sessionId/postmortem", user)
        assertThat(JsonPath.read<String>(postmortem, "$.recoveryStatus")).isEqualTo("PARTIAL")
        assertThat(JsonPath.read<Int>(postmortem, "$.residualBacklog")).isGreaterThan(100_000)
        assertThat(JsonPath.read<Int>(postmortem, "$.mttrSeconds")).isBetween(299, 310) // last action, as before

        val series = getJson("/sessions/$sessionId/simulation/series", user)
        assertThat(JsonPath.read<String>(series, "$.resolvedAt")).isNotNull()
    }

    @Test
    fun `declaring while still failing, without idempotent retries, flags duplicate payments`() {
        val user = newUser()
        val sessionId = startIncidentInPast(user, PAYMENT_SCENARIO_ID, 200)
        val report = resolve(sessionId, user)
        assertThat(JsonPath.read<String>(report, "$.status")).isEqualTo("NOT_RECOVERED")
        assertThat(JsonPath.read<List<String>>(report, "$.integrity[*].key")).containsExactly("payment-duplicates")
        assertThat(JsonPath.read<Boolean>(report, "$.integrity[0].ok")).isFalse()
        assertThat(JsonPath.read<String>(report, "$.integrity[0].detail")).contains("멱등 재시도 없이")
    }

    @Test
    fun `everything fixed on a non-backlog domain is a full recovery, and there is nothing to resolve before an incident`() {
        val user = newUser()
        val sessionId = startIncidentInPast(user, com.sysdrill.backend.support.COUPON_SCENARIO_ID, 200)
        listOf("STRENGTHEN_RATE_LIMIT", "INCREASE_CACHE_TTL", "INCREASE_DB_POOL").forEach { apply(sessionId, user, it) }
        assertThat(JsonPath.read<String>(resolve(sessionId, user), "$.status")).isEqualTo("RECOVERED")

        val idle = mockMvc.startSession(user)
        mockMvc.perform(post("/sessions/$idle/simulation/resolve").header("Authorization", bearerHeader(user)))
            .andExpect(status().isConflict)
    }
}
