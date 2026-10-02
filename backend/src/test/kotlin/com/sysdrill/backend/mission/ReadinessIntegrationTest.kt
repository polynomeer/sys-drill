package com.sysdrill.backend.mission

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
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
import java.util.UUID

/** PLAN.md Round E26 (docs/OBSERVABILITY_UI_PLAN.md O7) — what wasn't prepared is missing during the incident. */
@SpringBootTest
@AutoConfigureMockMvc
class ReadinessIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
    @Autowired val readinessService: ReadinessService,
) {
    private fun json(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    private fun confirm(sessionId: UUID, user: UUID, logging: Boolean, tracing: Boolean) = mockMvc.perform(
        put("/sessions/$sessionId/readiness").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
            .content("""{"structuredLogging":$logging,"tracing":$tracing}""")
    )

    @Test
    fun `readiness is fixed at deploy and switched-off signals are missing in the incident`() {
        val user = userRepository.save(User(email = "ready-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "ready")).id!!
        val sessionId = mockMvc.startSession(user)
        mockMvc.perform(
            put("/sessions/$sessionId/ops/alert-rules").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content("""[{"metric":"errorRatePct","op":">","threshold":5,"forSeconds":0,"severity":"CRITICAL"}]""")
        ).andExpect(status().isOk)

        val before = json("/sessions/$sessionId/readiness", user)
        assertThat(JsonPath.read<List<Boolean>>(before, "$.items[?(@.key == 'alerts')].checked")).containsExactly(true)
        assertThat(JsonPath.read<List<Boolean>>(before, "$.items[?(@.key == 'slo')].checked")).containsExactly(false)
        assertThat(JsonPath.read<Boolean>(before, "$.confirmed")).isFalse()

        confirm(sessionId, user, logging = false, tracing = false).andExpect(status().isOk)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        jdbcTemplate.update("update applied_actions set created_at = created_at - interval '120 seconds' where session_id = ?", sessionId)
        confirm(sessionId, user, logging = true, tracing = true).andExpect(status().isConflict)
        assertThat(JsonPath.read<Boolean>(json("/sessions/$sessionId/readiness", user), "$.locked")).isTrue()

        val traces = json("/sessions/$sessionId/simulation/traces", user)
        assertThat(JsonPath.read<Boolean>(traces, "$.available")).isFalse()
        assertThat(JsonPath.read<String>(traces, "$.note")).contains("트레이싱이 비활성")
        val logs = json("/sessions/$sessionId/simulation/logs", user)
        assertThat(JsonPath.read<List<String>>(logs, "$[*].service").toSet()).containsExactly("app")
        assertThat(JsonPath.read<List<String?>>(logs, "$[*].traceId")).allMatch { it == null }

        mockMvc.perform(
            post("/sessions/$sessionId/simulation/investigations").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content("""{"kind":"OPEN_PANEL","target":"logs"}""")
        ).andExpect(status().isNoContent)
        val section = readinessService.observabilityPromptSection(sessionRepository.findById(sessionId).orElseThrow())!!
        assertThat(section).contains("알림 규칙 1개", "구조화 로그 끔, 트레이싱 끔", "열어본 화면: logs")
    }
}
