package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ADR-0040 / docs/LEARNING_COMMUNITY_PLAN.md §6.5 — 시나리오별 토론.
 *
 * 스레드는 누적 데이터를 공유하므로 메시지 **개수**가 아니라 "내가 쓴 글이 보이는가
 * / 사라졌는가"를 단언한다(슬라이스 1·5·6 테스트와 같은 이유).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiscussionIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
) {

    private val couponScenarioId: UUID by lazy {
        scenarioRepository.findAll()
            .first { it.domain == "coupon" && it.organizationId == null && it.creatorUserId == null }
            .id!!
    }

    private fun newUser(nickname: String = "talk-user", admin: Boolean = false): UUID = userRepository.save(
        User(
            email = "talk-${UUID.randomUUID()}@example.com",
            passwordHash = "hash",
            nickname = nickname,
            platformRole = if (admin) PlatformRole.PLATFORM_ADMIN else PlatformRole.USER,
        )
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun completeSession(userId: UUID): UUID {
        val sessionId = mockMvc.startSession(userId)
        repeat(3) {
            mockMvc.perform(
                post("/sessions/$sessionId/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"Redis 카운터로 처리합니다."}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)
        return sessionId
    }

    private fun share(sessionId: UUID, userId: UUID) = mockMvc.perform(
        put("/sessions/$sessionId/visibility")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(userId))
            .content("""{"visibility":"PUBLIC","anonymous":false}""")
    ).andExpect(status().isOk)

    private fun postMessage(userId: UUID, body: String, quotedSessionId: UUID? = null): UUID {
        val quote = if (quotedSessionId == null) "" else ",\"quotedSessionId\":\"$quotedSessionId\""
        val json = mockMvc.perform(
            post("/scenarios/$couponScenarioId/discussion")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("{\"body\":\"$body\"$quote}")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(json, "$.id"))
    }

    private fun thread(userId: UUID): String = mockMvc.perform(
        get("/scenarios/$couponScenarioId/discussion").header("Authorization", bearerHeader(userId))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    private fun threadBodies(userId: UUID): List<String> =
        JsonPath.read<List<String>>(thread(userId), "$.messages[*].body")

    /**
     * 필터 표현식을 중첩해서 쓰는 대신 메시지 하나를 통째로 꺼내 단언한다 —
     * `$.messages[?(...)].quoted[0].locked` 류는 JsonPath 구현에 따라 리스트가
     * 한 겹 더 씌워져 읽는 사람이 실패 원인을 짚기 어려워진다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun messageById(userId: UUID, messageId: UUID): Map<String, Any?> {
        val messages = JsonPath.read<List<Map<String, Any?>>>(thread(userId), "$.messages")
        return messages.single { it["id"] == messageId.toString() }
    }

    @Test
    fun `완료하지 않아도 스레드를 읽고 쓸 수 있다`() {
        val asker = newUser("talk-asker")
        val body = "질문-${UUID.randomUUID()}"
        postMessage(asker, body)

        mockMvc.perform(get("/scenarios/$couponScenarioId/discussion").header("Authorization", bearerHeader(asker)))
            .andExpect(status().isOk)
            // 완료 조건이 걸리는 것은 인용된 풀이뿐이다 — 토론 자체는 열려 있다.
            .andExpect(jsonPath("$.completedByMe").value(false))
            // 스레드가 비어 보이지 않도록 집계 신호를 함께 준다(§6.5 빈 스레드 대응).
            .andExpect(jsonPath("$.completedCount").isNumber)
            .andExpect(jsonPath("$.scenarioTitle").value("선착순 쿠폰"))

        assertThat(threadBodies(asker)).contains(body)
    }

    @Test
    fun `인용된 풀이는 완료자에게만 열린다`() {
        val author = newUser("talk-author")
        val sessionId = completeSession(author)
        share(sessionId, author)
        val messageId = postMessage(author, "제 풀이를 참고하세요", quotedSessionId = sessionId)

        // 완료자: 인용이 열린다.
        val reader = newUser("talk-reader")
        completeSession(reader)
        val opened = messageById(reader, messageId)["quoted"] as Map<*, *>
        assertThat(opened["locked"]).isEqualTo(false)
        assertThat(opened["authorNickname"]).isEqualTo("talk-author")

        // 미완료자: 본문은 보이지만 인용은 잠긴다.
        val stranger = newUser()
        val seen = messageById(stranger, messageId)
        assertThat(seen["body"]).isEqualTo("제 풀이를 참고하세요")
        val locked = seen["quoted"] as Map<*, *>
        assertThat(locked["locked"]).isEqualTo(true)
        assertThat(locked["authorNickname"]).isNull()
    }

    @Test
    fun `내가 볼 수 없는 풀이는 인용할 수 없다 - 인용이 우회로가 되면 안 된다`() {
        val author = newUser()
        val sessionId = completeSession(author)
        share(sessionId, author)

        // 이 시나리오를 완료하지 않은 사람이 그 풀이를 인용하려 하면 막힌다.
        mockMvc.perform(
            post("/scenarios/$couponScenarioId/discussion")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(newUser()))
                .content("""{"body":"몰래 인용","quotedSessionId":"$sessionId"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `빈 본문은 400`() {
        mockMvc.perform(
            post("/scenarios/$couponScenarioId/discussion")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(newUser()))
                .content("""{"body":"   "}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `신고는 한 번만 기록되고 자기 글은 신고할 수 없다`() {
        val author = newUser()
        val messageId = postMessage(author, "신고-${UUID.randomUUID()}")

        mockMvc.perform(
            post("/discussions/$messageId/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(author))
                .content("{}")
        ).andExpect(status().isBadRequest)

        val reporter = newUser()
        repeat(2) {
            // 두 번째 신고도 오류가 아니다 — 재시도와 더블클릭이 실패가 되면 안 된다.
            mockMvc.perform(
                post("/discussions/$messageId/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(reporter))
                    .content("""{"reason":"스포일러"}""")
            ).andExpect(status().isNoContent)
        }

        assertThat(messageById(reporter, messageId)["reportedByMe"]).isEqualTo(true)
        // 신고한 사람 본인에게만 표시된다 — 남에게는 이 글이 신고됐다는 신호가 가지 않는다.
        assertThat(messageById(newUser(), messageId)["reportedByMe"]).isEqualTo(false)

        val admin = newUser(admin = true)
        val reported = mockMvc.perform(get("/admin/discussions/reported").header("Authorization", bearerHeader(admin)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val row = JsonPath.read<List<Map<String, Any?>>>(reported, "$")
            .single { it["id"] == messageId.toString() }
        assertThat(row["reportCount"]).isEqualTo(1)
    }

    @Test
    fun `관리자가 숨기면 스레드에서 사라지고 되돌릴 수 있다`() {
        val author = newUser()
        val body = "숨김-${UUID.randomUUID()}"
        val messageId = postMessage(author, body)
        val admin = newUser(admin = true)

        mockMvc.perform(
            put("/admin/discussions/$messageId/hidden")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin))
                .content("""{"hidden":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hidden").value(true))

        assertThat(threadBodies(author)).doesNotContain(body)

        // 지운 게 아니라 숨긴 것이므로 되돌아온다.
        mockMvc.perform(
            put("/admin/discussions/$messageId/hidden")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin))
                .content("""{"hidden":false}""")
        ).andExpect(status().isOk)

        assertThat(threadBodies(author)).contains(body)
    }

    @Test
    fun `모더레이션은 PLATFORM_ADMIN 만 할 수 있다`() {
        val author = newUser()
        val messageId = postMessage(author, "권한-${UUID.randomUUID()}")

        mockMvc.perform(get("/admin/discussions/reported").header("Authorization", bearerHeader(author)))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            put("/admin/discussions/$messageId/hidden")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(author))
                .content("""{"hidden":true}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `토큰 없이는 스레드를 읽을 수 없다`() {
        mockMvc.perform(get("/scenarios/$couponScenarioId/discussion")).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/discussions/${UUID.randomUUID()}/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isUnauthorized)
    }
}
