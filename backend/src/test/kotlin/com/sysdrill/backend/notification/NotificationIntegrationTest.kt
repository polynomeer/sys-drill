package com.sysdrill.backend.notification

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.build.BuildChallengeRepository
import com.sysdrill.backend.build.BuildSubmission
import com.sysdrill.backend.build.BuildSubmissionRepository
import com.sysdrill.backend.build.BuildSubmissionStatus
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.FakeEmailConfig
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round B16 — the notification feed is derived from existing rows and
 * split into new/seen by `users.notifications_seen_at` alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeEmailConfig::class)
class NotificationIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val buildSubmissionRepository: BuildSubmissionRepository,
    @Autowired val buildChallengeRepository: BuildChallengeRepository,
) {

    private fun user(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private fun feed(userId: UUID): String =
        mockMvc.perform(get("/me/notifications").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString

    private fun types(body: String): List<String> = JsonPath.read(body, "$.items[*].type")

    private fun post(path: String, userId: UUID, body: String = "{}"): String =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(userId)).content(body))
            .andReturn().response.contentAsString

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(90)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    @Test
    fun `graded work, invitations and new discussion messages show up, and opening the list marks them seen`() {
        val me = user("notified")
        val other = user("other")
        val admin = user("admin")

        // Nothing yet.
        assertThat(JsonPath.read<Int>(feed(me.id!!), "$.unseenCount")).isZero()

        // A graded design submission.
        val sessionId = mockMvc.startSession(me.id!!)
        post("/sessions/$sessionId/submissions", me.id!!, """{"rawText":"Redis로 재고를 차감합니다."}""")
        awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)

        // A finished Build run.
        val challengeId = buildChallengeRepository.findBySlug("rate-limiter")!!.id!!
        buildSubmissionRepository.save(
            BuildSubmission(userId = me.id!!, challengeId = challengeId, sourceCode = "x = 1", status = BuildSubmissionStatus.COMPLETED, score = 2)
                .apply { completedAt = Instant.now() }
        )

        // An organization invitation to my email.
        val orgId = JsonPath.read<String>(post("/organizations", admin.id!!, """{"name":"알림 조직"}"""), "$.id")
        post("/organizations/$orgId/invitations", admin.id!!, """{"email":"${me.email}","role":"MEMBER"}""")

        // Discussion: a message before I joined doesn't count, one after does, my own never does.
        post("/scenarios/$COUPON_SCENARIO_ID/discussion", other.id!!, """{"body":"먼저 쓴 글"}""")
        Thread.sleep(20)
        post("/scenarios/$COUPON_SCENARIO_ID/discussion", me.id!!, """{"body":"제 질문입니다"}""")
        Thread.sleep(20)
        post("/scenarios/$COUPON_SCENARIO_ID/discussion", other.id!!, """{"body":"답글입니다"}""")

        val body = feed(me.id!!)
        assertThat(types(body)).contains("EVALUATION_READY", "BUILD_GRADED", "ORGANIZATION_INVITATION", "DISCUSSION_MESSAGE")
        val discussionBodies = JsonPath.read<List<String>>(body, "$.items[?(@.type == 'DISCUSSION_MESSAGE')].body")
        assertThat(discussionBodies).anyMatch { it.contains("답글입니다") }
        assertThat(discussionBodies).noneMatch { it.contains("먼저 쓴 글") || it.contains("제 질문입니다") }
        assertThat(JsonPath.read<Int>(body, "$.unseenCount")).isEqualTo(types(body).size)

        // Opening the list: everything seen; a newer message is new again.
        mockMvc.perform(post("/me/notifications/seen").header("Authorization", bearerHeader(me.id!!))).andExpect(status().isNoContent)
        assertThat(JsonPath.read<Int>(feed(me.id!!), "$.unseenCount")).isZero()
        Thread.sleep(20)
        post("/scenarios/$COUPON_SCENARIO_ID/discussion", other.id!!, """{"body":"또 다른 답글"}""")
        assertThat(JsonPath.read<Int>(feed(me.id!!), "$.unseenCount")).isEqualTo(1)
    }

    @Test
    fun `the feed needs a signed-in caller`() {
        mockMvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized)
    }
}
