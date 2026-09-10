package com.sysdrill.backend.mentor

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * AI 4역할 Slice 3 (Mentor) — an on-demand hint for a draft that hasn't been
 * submitted yet. No LLM_ANTHROPIC_API_KEY is configured in this test
 * environment, so AnthropicLlmClient's offline fallback (a fixed canned JSON
 * shaped for design-evaluation, not mentor hints — see its kdoc) serves the
 * request; it has no `hints` key, so `hints` always comes back empty here —
 * these tests verify the pipeline runs end to end (session/domain lookup,
 * RuleEvaluator, the `mentor_hint` prompt template actually being found,
 * LLM call, parse) without erroring, not that hints have content.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MentorControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "mentor-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @Test
    fun `a hint request for a completely empty draft succeeds`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(
            post("/sessions/$sessionId/mentor-hint").header("Authorization", bearerHeader(userId))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hints").isArray)
    }

    @Test
    fun `a hint request for a partial draft succeeds`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(
            post("/sessions/$sessionId/mentor-hint").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"rawText":"Redis 캐시를 앞단에 둡니다."}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hints").isArray)
    }

    @Test
    fun `a caller who does not own the session gets not-found`() {
        val ownerId = userId
        val strangerId = userRepository.save(
            User(email = "stranger-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "stranger")
        ).id!!
        val sessionId = mockMvc.startSession(ownerId)

        mockMvc.perform(
            post("/sessions/$sessionId/mentor-hint").header("Authorization", bearerHeader(strangerId))
        ).andExpect(status().isNotFound)
    }
}
