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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** PLAN.md Round E25 (docs/OBSERVABILITY_UI_PLAN.md O6) — log trace ids open waterfalls; Jaeger traces map to the same shape. */
@SpringBootTest
@AutoConfigureMockMvc
class TraceIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
    @Autowired val traceService: TraceService,
    @Autowired val objectMapper: ObjectMapper,
) {
    private fun json(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `a log line's trace id opens a synthetic waterfall that adds up to the request`() {
        val user = userRepository.save(User(email = "trace-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "trace")).id!!
        val sessionId = mockMvc.startSession(user)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        jdbcTemplate.update("update applied_actions set created_at = created_at - interval '120 seconds' where session_id = ?", sessionId)

        val list = json("/sessions/$sessionId/simulation/traces", user)
        assertThat(JsonPath.read<String>(list, "$.source")).isEqualTo("SYNTHETIC")
        val logTraceIds = JsonPath.read<List<String?>>(json("/sessions/$sessionId/simulation/logs", user), "$[*].traceId").filterNotNull().toSet()
        val traceId = JsonPath.read<List<String>>(list, "$.traces[*].traceId").first()
        assertThat(logTraceIds).contains(traceId)

        val trace = json("/sessions/$sessionId/simulation/traces/$traceId", user)
        val root = JsonPath.read<Int>(trace, "$.spans[0].durationMs")
        val children = JsonPath.read<List<Int>>(trace, "$.spans[1:].durationMs")
        assertThat(JsonPath.read<List<String>>(trace, "$.spans[*].service")).contains("coupon-api", "redis", "coupon-db")
        assertThat(children.sum()).isLessThanOrEqualTo(root)
        mockMvc.perform(get("/sessions/$sessionId/simulation/traces/deadbeef00").header("Authorization", bearerHeader(user))).andExpect(status().isNotFound)
    }

    @Test
    fun `a Jaeger trace becomes ordered spans with parents and offsets`() {
        val jaeger = objectMapper.readTree(
            """{"traceID":"abc123","processes":{"p1":{"serviceName":"backend"}},"spans":[
              {"traceID":"abc123","spanID":"b","operationName":"coupon.db.select_remaining","references":[{"refType":"CHILD_OF","spanID":"a"}],
               "startTime":1700000000005000,"duration":42000,"processID":"p1","tags":[{"key":"sysdrill.session_id","value":"x"}]},
              {"traceID":"abc123","spanID":"a","operationName":"http get /remaining","references":[],
               "startTime":1700000000000000,"duration":50000,"processID":"p1","tags":[{"key":"error","value":"true"}]}]}"""
        )
        val view = traceService.toView(jaeger)!!
        assertThat(view.spans.map { it.spanId }).containsExactly("a", "b")
        assertThat(view.spans[1].parentSpanId).isEqualTo("a")
        assertThat(view.spans[1].startMs).isEqualTo(5)
        assertThat(view.spans[1].durationMs).isEqualTo(42)
        assertThat(view.spans[0].error).isTrue()
        assertThat(view.durationMs).isEqualTo(50)
    }
}
