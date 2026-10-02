package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
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
import java.time.Instant
import java.util.UUID

/** PLAN.md Round E31 (docs/COMMUNITY_EXPANSION_PLAN.md C13, ADR-0050) — admin-made challenges and their boards. */
@SpringBootTest
@AutoConfigureMockMvc
class ChallengeEventTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
    @Autowired val service: ChallengeEventService,
) {
    private val scenario = UUID.fromString("b5000000-0000-0000-0000-000000000002") // deployment: a rollback recovers fully

    private fun user(name: String, admin: Boolean = false): UUID = userRepository.save(
        User(email = "ch-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = name,
            platformRole = if (admin) PlatformRole.PLATFORM_ADMIN else PlatformRole.USER)
    ).id!!

    /** A completed deployment run with an evaluated score, optionally with a declared recovery after [resolvedAfter] seconds. */
    private fun run(userId: UUID, startedAt: Instant, score: Int, resolvedAfter: Long?, public: Boolean = false): UUID {
        val versionId = jdbcTemplate.queryForObject("select id from scenario_versions where scenario_id = ? order by version_no desc limit 1", UUID::class.java, scenario)!!
        val sessionId = UUID.randomUUID()
        jdbcTemplate.update(
            "insert into sessions (id, user_id, scenario_version_id, status, started_at, completed_at, visibility) values (?, ?, ?, 'COMPLETED', ?, ?, ?)",
            sessionId, userId, versionId, java.sql.Timestamp.from(startedAt), java.sql.Timestamp.from(startedAt.plusSeconds(1200)), if (public) "PUBLIC" else "PRIVATE",
        )
        jdbcTemplate.update(
            "insert into reports (session_id, version, timeline_feedback) values (?, 1, ?::jsonb)",
            sessionId, """[{"phase":"INITIAL","submissionId":"${UUID.randomUUID()}","totalScore":$score,"topRisks":[],"onTime":null}]""",
        )
        val incident = startedAt.plusSeconds(600)
        jdbcTemplate.update("insert into applied_actions (session_id, action_type, effect, created_at) values (?, 'INCIDENT_STARTED', '시작', ?)", sessionId, java.sql.Timestamp.from(incident))
        if (resolvedAfter != null) {
            jdbcTemplate.update("insert into applied_actions (session_id, action_type, effect, created_at) values (?, 'ROLLBACK', '조치', ?)", sessionId, java.sql.Timestamp.from(incident.plusSeconds(10)))
            jdbcTemplate.update("insert into applied_actions (session_id, action_type, effect, created_at) values (?, 'INCIDENT_RESOLVED', '복구 선언', ?)", sessionId, java.sql.Timestamp.from(incident.plusSeconds(resolvedAfter)))
        }
        return sessionId
    }

    @Test
    fun `only admins create, runs inside the window rank by score then declared recovery, writeups open after the end`() {
        val admin = user("admin", admin = true)
        val start = Instant.now().minusSeconds(7200)
        val body = """{"title":"배포 챌린지","scenarioId":"$scenario","startsAt":"$start","endsAt":"${Instant.now().plusSeconds(3600)}"}"""
        mockMvc.perform(post("/admin/events").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user("x"))).content(body))
            .andExpect(status().isForbidden)
        val created = mockMvc.perform(post("/admin/events").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(admin)).content(body))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val eventId = UUID.fromString(JsonPath.read(created, "$.id"))
        assertThat(JsonPath.read<String>(created, "$.phase")).isEqualTo("LIVE")

        val fast = user("빠른")
        val slow = user("느린")
        val outside = user("밖")
        run(fast, start.plusSeconds(60), 80, resolvedAfter = 400, public = true)
        run(slow, start.plusSeconds(60), 80, resolvedAfter = 900)
        run(outside, start.minusSeconds(600), 99, resolvedAfter = 100)

        val live = service.board(eventId, fast)
        assertThat(live.entries.map { it.nickname }).containsExactly("빠른", "느린")
        assertThat(live.entries[0].resolvedSeconds).isEqualTo(400)
        assertThat(live.entries[0].writeupSessionId).isNull() // no spoilers while it runs
        assertThat(live.entries[0].mine).isTrue()

        val after = service.board(eventId, fast, now = Instant.now().plusSeconds(7200))
        assertThat(after.debriefOpen).isTrue()
        assertThat(after.entries[0].writeupSessionId).isNotNull()
        assertThat(after.entries[1].writeupSessionId).isNull() // private run
    }
}
