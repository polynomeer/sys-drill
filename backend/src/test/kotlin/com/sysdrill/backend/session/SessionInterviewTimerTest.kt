package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md step 28 — interview-timer mode. Shrinks the INITIAL phase's time
 * limit to 1 second (a dedicated Spring context, since no other test needs
 * this override) so the "submitted after the deadline" branch can be
 * verified without a real 10-minute wait.
 */
@SpringBootTest(properties = ["sysdrill.session.interview-timer.initial-seconds=1"])
@AutoConfigureMockMvc
class SessionInterviewTimerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "interview-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @Test
    fun `a non-interview-mode session has no phase deadline`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.interviewMode").value(false))
            .andExpect(jsonPath("$.phaseDeadlineAt").doesNotExist())
    }

    @Test
    fun `an interview-mode session has a phase deadline shortly in the future`() {
        val sessionId = mockMvc.startSession(userId, interviewMode = true)

        val body = mockMvc.perform(get("/sessions/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.interviewMode").value(true))
            .andReturn().response.contentAsString

        val deadline = Instant.parse(JsonPath.read<String>(body, "$.phaseDeadlineAt"))
        assertThat(deadline).isAfter(Instant.now())
        assertThat(deadline).isBefore(Instant.now().plusSeconds(5))
    }

    @Test
    fun `submitting before the deadline is marked on time, and after it is marked late`() {
        val onTimeSessionId = mockMvc.startSession(userId, interviewMode = true)
        mockMvc.perform(
            post("/sessions/$onTimeSessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"바로 제출합니다."}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.onTime").value(true))

        val lateSessionId = mockMvc.startSession(userId, interviewMode = true)
        Thread.sleep(1500)
        mockMvc.perform(
            post("/sessions/$lateSessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText":"늦게 제출합니다."}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.onTime").value(false))
    }
}
