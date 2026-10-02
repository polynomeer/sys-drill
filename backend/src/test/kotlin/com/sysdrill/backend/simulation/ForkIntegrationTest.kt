package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.NOTIFICATION_SCENARIO_ID
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
 * PLAN.md Round E14 (docs/DRILLS_EXPANSION_PLAN.md M6, ADR-0046) — Counterfactual Replay
 * and the sandbox-contamination fix. The source incident is a notification incident where
 * only ADD_CONSUMERS was tried (300s in): 12 consumers × 3.33/s can't touch 1500/s, so the
 * original never recovers within the 30-minute horizon. A fork from the start that applies
 * all three fixes does.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ForkIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "fork-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "fork")).id!!

    private fun shift(sessionId: UUID, seconds: Int) =
        jdbcTemplate.update("update applied_actions set created_at = created_at - make_interval(secs => ?) where session_id = ?", seconds, sessionId)

    private fun applyLive(sessionId: UUID, user: UUID, action: String) = mockMvc.perform(
        post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"actionType":"$action"}""")
    ).andExpect(status().isOk)

    /** Incident started 500s ago, ADD_CONSUMERS 300s into it. */
    private fun sourceIncident(user: UUID): UUID {
        val sessionId = mockMvc.startSession(user, NOTIFICATION_SCENARIO_ID)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        shift(sessionId, 300)
        applyLive(sessionId, user, "ADD_CONSUMERS")
        shift(sessionId, 200)
        return sessionId
    }

    private fun fork(sessionId: UUID, user: UUID, atStep: Int) = mockMvc.perform(
        post("/sessions/$sessionId/forks").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"atStep":$atStep}""")
    )

    private fun applyFork(forkId: String, user: UUID, action: String) = mockMvc.perform(
        post("/forks/$forkId/actions").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"actionType":"$action"}""")
    )

    private fun getJson(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `a fork keeps the actions up to its step, at their original offsets`() {
        val user = newUser()
        val sessionId = sourceIncident(user)
        val json = fork(sessionId, user, 1).andExpect(status().isCreated).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(json, "$.prefixActions")).containsExactly("ADD_CONSUMERS")
        assertThat(JsonPath.read<Int>(json, "$.forkedAtSeconds")).isBetween(299, 302)
        assertThat(JsonPath.read<List<String>>(json, "$.actions")).isEmpty()
    }

    @Test
    fun `doing it differently from the start recovers where the original never did`() {
        val user = newUser()
        val sessionId = sourceIncident(user)
        val forkId = JsonPath.read<String>(fork(sessionId, user, 0).andExpect(status().isCreated).andReturn().response.contentAsString, "$.forkId")
        listOf("ADD_CONSUMERS", "ENABLE_CIRCUIT_BREAKER", "ADJUST_RETRY_BACKOFF").forEach {
            applyFork(forkId, user, it).andExpect(status().isOk)
        }

        val comparison = getJson("/forks/$forkId/comparison", user)
        assertThat(JsonPath.read<Any?>(comparison, "$.original.recoveredAtSeconds")).isNull()
        assertThat(JsonPath.read<Int>(comparison, "$.fork.recoveredAtSeconds")).isLessThan(120)
        assertThat(JsonPath.read<Double>(comparison, "$.fork.impactSeconds"))
            .isLessThan(JsonPath.read<Double>(comparison, "$.original.impactSeconds"))

        val series = getJson("/forks/$forkId/series", user)
        assertThat(JsonPath.read<List<Any>>(series, "$.points")).isNotEmpty()

        // The source session's own record is untouched.
        val timeline = getJson("/sessions/$sessionId/simulation/timeline", user)
        assertThat(JsonPath.read<List<String?>>(timeline, "$[*].actionType")).containsExactly(null, "ADD_CONSUMERS")
    }

    @Test
    fun `forks are private, domain-checked and bounded`() {
        val user = newUser()
        val sessionId = sourceIncident(user)
        val forkId = JsonPath.read<String>(fork(sessionId, user, 0).andReturn().response.contentAsString, "$.forkId")
        applyFork(forkId, user, "INCREASE_DB_POOL").andExpect(status().isBadRequest) // coupon action
        mockMvc.perform(get("/forks/$forkId").header("Authorization", bearerHeader(newUser()))).andExpect(status().isNotFound)
        fork(sessionId, user, 9).andExpect(status().isBadRequest)
        fork(sessionId, newUser(), 0).andExpect(status().isNotFound)
    }

    @Test
    fun `actions applied after the session completed are sandbox and stay out of the record`() {
        val user = newUser()
        val sessionId = sourceIncident(user)
        jdbcTemplate.update("update sessions set completed_at = now() where id = ?", sessionId)
        applyLive(sessionId, user, "ENABLE_CIRCUIT_BREAKER") // the old "샌드박스에서 계속 실험하기" path

        val timeline = getJson("/sessions/$sessionId/simulation/timeline", user)
        assertThat(JsonPath.read<List<String?>>(timeline, "$[*].actionType")).containsExactly(null, "ADD_CONSUMERS")
        val postmortem = getJson("/sessions/$sessionId/postmortem", user)
        assertThat(JsonPath.read<Int>(postmortem, "$.mttrSeconds")).isBetween(299, 302)
    }
}
