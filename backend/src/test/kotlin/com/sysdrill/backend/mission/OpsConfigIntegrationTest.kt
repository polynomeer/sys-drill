package com.sysdrill.backend.mission

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** PLAN.md Round E12 — SLO / alert-rule config end to end, and alerts surfacing on the series. */
@SpringBootTest
@AutoConfigureMockMvc
class OpsConfigIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "ops-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "ops")).id!!

    private fun putJson(path: String, user: UUID, body: String) = mockMvc.perform(
        put(path).contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user)).content(body)
    )

    private fun getJson(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `defaults, domain suggestions, and validation`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user) // coupon
        val ops = getJson("/sessions/$sessionId/ops", user)
        assertThat(JsonPath.read<Any?>(ops, "$.slo")).isNull()
        assertThat(JsonPath.read<Double>(ops, "$.sloDefaults.availabilityPct")).isEqualTo(99.9)
        assertThat(JsonPath.read<List<String>>(ops, "$.suggestedRules[*].metric")).contains("dbWriteLoadPct")
        assertThat(JsonPath.read<List<Any>>(ops, "$.alertRules")).isEmpty()

        putJson("/sessions/$sessionId/ops/slo", user, """{"availabilityPct":120,"p95Ms":300,"errorRatePct":1}""").andExpect(status().isBadRequest)
        putJson("/sessions/$sessionId/ops/alert-rules", user, """[{"metric":"nope","op":">","threshold":1,"forSeconds":10}]""").andExpect(status().isBadRequest)
        putJson("/sessions/$sessionId/ops/alert-rules", user, """[{"metric":"errorRatePct","op":">=","threshold":1,"forSeconds":10}]""").andExpect(status().isBadRequest)
        putJson("/sessions/$sessionId/ops/slo", newUser(), """{"availabilityPct":99,"p95Ms":300,"errorRatePct":1}""").andExpect(status().isNotFound)
    }

    @Test
    fun `re-saving keeps a rule's createdAt, and fired alerts show up on the series`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user)
        putJson("/sessions/$sessionId/ops/slo", user, """{"availabilityPct":99.5,"p95Ms":300,"errorRatePct":1}""").andExpect(status().isOk)
        val saved = putJson(
            "/sessions/$sessionId/ops/alert-rules", user,
            """[{"metric":"errorRatePct","op":">","threshold":5,"forSeconds":60,"severity":"CRITICAL"}]""",
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val id = JsonPath.read<String>(saved, "$.alertRules[0].id")
        val createdAt = JsonPath.read<String>(saved, "$.alertRules[0].createdAt")

        val resaved = putJson(
            "/sessions/$sessionId/ops/alert-rules", user,
            """[{"id":"$id","metric":"errorRatePct","op":">","threshold":10,"forSeconds":60,"severity":"CRITICAL"}]""",
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<String>(resaved, "$.alertRules[0].createdAt")).isEqualTo(createdAt)
        assertThat(JsonPath.read<Double>(resaved, "$.alertRules[0].threshold")).isEqualTo(10.0)

        // Rule existed before the incident; push the incident and the rule five minutes back.
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk)
        jdbcTemplate.update("update applied_actions set created_at = created_at - interval '300 seconds' where session_id = ?", sessionId)
        jdbcTemplate.update(
            """
            update sessions set mission_state = jsonb_set(mission_state, '{alertRules,0,createdAt}',
              to_jsonb(to_char((now() - interval '400 seconds') at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')))
            where id = ?
            """.trimIndent(),
            sessionId,
        )

        val series = getJson("/sessions/$sessionId/simulation/series", user)
        assertThat(JsonPath.read<List<String>>(series, "$.alerts[*].severity")).containsExactly("CRITICAL")
        assertThat(JsonPath.read<Boolean>(series, "$.alerts[0].falseAlarm")).isFalse()
        assertThat(JsonPath.read<Double>(series, "$.slo.targets.availabilityPct")).isEqualTo(99.5)
        assertThat(JsonPath.read<Boolean>(series, "$.slo.errorRateMet")).isFalse()

        val postmortem = getJson("/sessions/$sessionId/postmortem", user)
        assertThat(JsonPath.read<Int>(postmortem, "$.firstAlertSeconds")).isBetween(60, 120)
        assertThat(JsonPath.read<Int>(postmortem, "$.alertRuleCount")).isEqualTo(1)
    }
}
