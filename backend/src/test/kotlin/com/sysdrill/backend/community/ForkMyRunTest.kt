package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
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

/**
 * PLAN.md Round E16 (docs/COMMUNITY_EXPANSION_PLAN.md C9) — "Fork My Run": a reader forks a
 * public writeup's incident under exactly the ADR-0041 gate, and the fork is the reader's alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.evaluation.rate-limit-per-minute=100"])
class ForkMyRunTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "fmr-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "fmr")).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    /** Coupon, three steps, with one incident action. */
    private fun complete(user: UUID, share: Boolean): UUID {
        val sessionId = mockMvc.startSession(user)
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
        if (share) {
            mockMvc.perform(
                put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(user)).content("""{"visibility":"PUBLIC","anonymous":true}""")
            ).andExpect(status().isOk)
        }
        return sessionId
    }

    private fun fork(sessionId: UUID, user: UUID, atStep: Int) = mockMvc.perform(
        post("/sessions/$sessionId/forks").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user)).content("""{"atStep":$atStep}""")
    )

    @Test
    fun `a reader who completed the scenario can fork a public writeup, and nobody else can`() {
        val author = newUser()
        val reader = newUser()
        val stranger = newUser()
        val published = complete(author, share = true)
        val private = complete(author, share = false)
        complete(reader, share = false)

        val detail = mockMvc.perform(get("/writeups/$published").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(detail, "$.summary.forkable")).isTrue()
        assertThat(JsonPath.read<List<String>>(detail, "$.summary.actions")).containsExactly("INCREASE_DB_POOL")

        val forked = fork(published, reader, 1).andExpect(status().isCreated).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(forked, "$.prefixActions")).containsExactly("INCREASE_DB_POOL")
        val forkId = JsonPath.read<String>(forked, "$.forkId")
        mockMvc.perform(get("/forks/$forkId").header("Authorization", bearerHeader(reader))).andExpect(status().isOk)
        mockMvc.perform(get("/forks/$forkId").header("Authorization", bearerHeader(author))).andExpect(status().isNotFound)

        fork(published, stranger, 0).andExpect(status().isForbidden) // hasn't completed coupon
        fork(private, reader, 0).andExpect(status().isNotFound) // not shared
    }
}
