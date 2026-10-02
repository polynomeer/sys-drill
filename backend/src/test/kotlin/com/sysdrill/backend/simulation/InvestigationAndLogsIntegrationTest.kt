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

/** PLAN.md Round E17 — investigation recording (O0-b) and generated server logs (O4). */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationAndLogsIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "inv-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "inv")).id!!

    private fun getJson(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    private fun look(sessionId: UUID, user: UUID, kind: String, target: String) = mockMvc.perform(
        post("/sessions/$sessionId/simulation/investigations").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"kind":"$kind","target":"$target"}""")
    )

    @Test
    fun `logs follow the series deterministically and looks are debounced into the postmortem`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user) // coupon
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        jdbcTemplate.update("update applied_actions set created_at = created_at - interval '120 seconds' where session_id = ?", sessionId)

        val logs = getJson("/sessions/$sessionId/simulation/logs", user)
        val messages = JsonPath.read<List<String>>(logs, "$[*].message")
        assertThat(messages.first()).contains("incident window opened")
        assertThat(JsonPath.read<List<String>>(logs, "$[*].level")).contains("ERROR")
        assertThat(JsonPath.read<List<String>>(logs, "$[*].service")).contains("coupon-api")
        // Same session, same points → same lines (only the newest window may grow).
        val again = JsonPath.read<List<String>>(getJson("/sessions/$sessionId/simulation/logs", user), "$[*].traceId")
        assertThat(again).startsWith(*JsonPath.read<List<String>>(logs, "$[*].traceId").take(10).toTypedArray())

        look(sessionId, user, "OPEN_PANEL", "logs").andExpect(status().isNoContent)
        look(sessionId, user, "OPEN_PANEL", "logs").andExpect(status().isNoContent) // debounced
        look(sessionId, user, "INSPECT_NODE", "redis").andExpect(status().isNoContent)
        look(sessionId, newUser(), "OPEN_PANEL", "logs").andExpect(status().isNotFound) // not the owner
        look(sessionId, user, "NOT_A_KIND", "x").andExpect(status().isBadRequest)

        val postmortem = getJson("/sessions/$sessionId/postmortem", user)
        assertThat(JsonPath.read<List<String>>(postmortem, "$.investigations[*].kind")).containsExactly("OPEN_PANEL", "INSPECT_NODE")
        assertThat(JsonPath.read<Int>(postmortem, "$.investigations[0].elapsedSeconds")).isBetween(119, 130)
    }
}
