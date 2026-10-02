package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** PLAN.md Round E22 (docs/COMMUNITY_EXPANSION_PLAN.md C10) — anchored reviews under the writeup gate. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.evaluation.rate-limit-per-minute=100"])
class WriteupCommentTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val objectMapper: tools.jackson.databind.ObjectMapper,
) {
    private fun newUser(admin: Boolean = false): UUID = userRepository.save(
        User(
            email = "wc-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "wc",
            platformRole = if (admin) PlatformRole.PLATFORM_ADMIN else PlatformRole.USER,
        )
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    /** Coupon, three steps, a canvas and one incident action. */
    private fun complete(user: UUID, share: Boolean): UUID {
        val sessionId = mockMvc.startSession(user)
        val graph = """{"nodes":[{"id":"gw","data":{"kind":"gateway","label":"API Gateway"}},{"id":"db1","data":{"kind":"db","label":"쿠폰 DB"}}],"edges":[{"source":"gw","target":"db1"}]}"""
        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content(objectMapper.writeValueAsString(mapOf("graph" to graph)))
        ).andExpect(status().isOk)
        repeat(3) { phase ->
            if (phase == 2) {
                mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
                mockMvc.perform(
                    post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearerHeader(user)).content("""{"actionType":"INCREASE_DB_POOL"}""")
                ).andExpect(status().isOk)
            }
            mockMvc.perform(
                post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(user)).content("""{"rawText":"설계","clientRequestId":"${UUID.randomUUID()}"}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)
        if (share) visibility(sessionId, user, "PUBLIC")
        return sessionId
    }

    private fun visibility(sessionId: UUID, user: UUID, v: String) = mockMvc.perform(
        put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"visibility":"$v","anonymous":false}""")
    ).andExpect(status().isOk)

    private fun comment(sessionId: UUID, user: UUID, body: String) = mockMvc.perform(
        post("/writeups/$sessionId/comments").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user)).content(body)
    )

    @Test
    fun `reviews pin to real nodes and steps, follow the writeup gate and moderation`() {
        val author = newUser()
        val reader = newUser()
        val stranger = newUser()
        val admin = newUser(admin = true)
        val writeup = complete(author, share = true)
        complete(reader, share = false)

        val initial = mockMvc.perform(get("/writeups/$writeup/comments").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(initial, "$.anchors[?(@.type == 'NODE')].label")).containsExactly("API Gateway", "쿠폰 DB")
        assertThat(JsonPath.read<List<String>>(initial, "$.anchors[?(@.type == 'TIMELINE')].ref")).containsExactly("step:0", "step:1")

        comment(writeup, reader, """{"anchorType":"NODE","anchorRef":"db1","kind":"RISK","body":"DB 하나가 SPOF 아닌가요?"}""").andExpect(status().isCreated)
        val posted = comment(writeup, reader, """{"anchorType":"TIMELINE","anchorRef":"step:1","kind":"ALTERNATIVE","body":"여기서 rate limit을 먼저 걸었다면?"}""")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val stepLabel = JsonPath.read<List<String>>(initial, "$.anchors[?(@.ref == 'step:1')].label").single()
        assertThat(stepLabel).endsWith("INCREASE_DB_POOL")
        assertThat(JsonPath.read<List<String>>(posted, "$.comments[*].anchorLabel")).containsExactly("쿠폰 DB", stepLabel)
        comment(writeup, reader, """{"anchorType":"NODE","anchorRef":"nope","kind":"RISK","body":"x"}""").andExpect(status().isBadRequest)
        comment(writeup, stranger, """{"kind":"QUESTION","body":"x"}""").andExpect(status().isForbidden)

        val commentId = JsonPath.read<String>(posted, "$.comments[0].id")
        mockMvc.perform(
            post("/writeup-comments/$commentId/reports").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(author)).content("""{"reason":"spam"}""")
        ).andExpect(status().isNoContent)
        mockMvc.perform(get("/admin/writeup-comments/reported").header("Authorization", bearerHeader(reader))).andExpect(status().isForbidden)
        mockMvc.perform(
            put("/admin/writeup-comments/$commentId/hidden").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(admin)).content("""{"hidden":true}""")
        ).andExpect(status().isOk)
        val afterHide = mockMvc.perform(get("/writeups/$writeup/comments").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(afterHide, "$.comments[*].kind")).containsExactly("ALTERNATIVE")

        visibility(writeup, author, "PRIVATE")
        mockMvc.perform(get("/writeups/$writeup/comments").header("Authorization", bearerHeader(reader))).andExpect(status().isNotFound)
    }
}
