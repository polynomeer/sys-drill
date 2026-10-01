package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md Round E4 (ADR-0045) — `GET /sessions/{id}/simulation/series` end to end.
 * The sampler's arithmetic is pinned by [TelemetrySamplerTest]; this checks the
 * wiring: the window starts a minute before the incident, the action rows and
 * their timestamps are what the series is built from, and access matches /state.
 *
 * A real incident only lasts seconds inside a test, so the rows' `created_at` are
 * shifted into the past with plain SQL to put the incident minutes back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimulationSeriesIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {

    private fun newUser(): UUID =
        userRepository.save(User(email = "series-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "series")).id!!

    private fun series(sessionId: UUID, userId: UUID): String = mockMvc.perform(
        get("/sessions/$sessionId/simulation/series").header("Authorization", bearerHeader(userId))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    private fun shiftIntoPast(sessionId: UUID, seconds: Int) {
        jdbcTemplate.update(
            "update applied_actions set created_at = created_at - make_interval(secs => ?) where session_id = ?",
            seconds, sessionId,
        )
    }

    @Test
    fun `no incident yet is an empty series`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user)
        val json = series(sessionId, user)
        assertThat(JsonPath.read<Any?>(json, "$.incidentStartedAt")).isNull()
        assertThat(JsonPath.read<List<Any>>(json, "$.points")).isEmpty()
    }

    @Test
    fun `an incident five minutes old has a calm lead-in, then turns critical, and recovers after the fixes`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk)
        shiftIntoPast(sessionId, 300)

        val before = series(sessionId, user)
        val statuses: List<String> = JsonPath.read(before, "$.points[*].status")
        assertThat(JsonPath.read<String>(before, "$.engineMode")).isEqualTo("RULE_BASED")
        assertThat(statuses.first()).isEqualTo("HEALTHY") // a minute before the incident
        assertThat(statuses.last()).isEqualTo("CRITICAL") // coupon at full incident load, nothing applied
        assertThat(statuses.size).isLessThanOrEqualTo(121)

        listOf("STRENGTHEN_RATE_LIMIT", "INCREASE_CACHE_TTL", "INCREASE_DB_POOL").forEach { action ->
            mockMvc.perform(
                post("/sessions/$sessionId/simulation/actions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(user))
                    .content("""{"actionType":"$action"}""")
            ).andExpect(status().isOk)
        }
        val after: List<String> = JsonPath.read(series(sessionId, user), "$.points[*].status")
        // The fixes were applied "now", after five critical minutes — the last point reflects them.
        assertThat(after).contains("CRITICAL")
        assertThat(after.last()).isIn("RECOVERED", "RECOVERING")
    }

    @Test
    fun `only the owner or a spectator may read it`() {
        val owner = newUser()
        val sessionId = mockMvc.startSession(owner)
        // Same guard as /state and /timeline: a stranger can't even learn the session exists.
        mockMvc.perform(get("/sessions/$sessionId/simulation/series").header("Authorization", bearerHeader(newUser())))
            .andExpect(status().isNotFound)
    }
}
