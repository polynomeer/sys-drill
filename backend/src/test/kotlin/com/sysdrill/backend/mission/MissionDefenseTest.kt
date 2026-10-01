package com.sysdrill.backend.mission

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round E10 — M4 (design defense from the INITIAL feedback's follow-up questions)
 * and M11 (status update with the INCIDENT answer). Runs on the official coupon scenario:
 * neither needs scenario content. The offline evaluator's follow-up questions are what
 * becomes the defense here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionDefenseTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val missionService: MissionService,
) {

    private fun newUser(): UUID =
        userRepository.save(User(email = "defense-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "defense")).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun defense(sessionId: UUID, userId: UUID): String = mockMvc.perform(
        get("/sessions/$sessionId/defense").header("Authorization", bearerHeader(userId))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `the INITIAL feedback's follow-up questions become at most two defense questions`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user)
        assertThat(JsonPath.read<Boolean>(defense(sessionId, user), "$.available")).isFalse() // nothing evaluated yet

        mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user))
                .content("""{"rawText":"설계","clientRequestId":"${UUID.randomUUID()}"}""")
        ).andExpect(status().isCreated)
        awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)

        val json = defense(sessionId, user)
        assertThat(JsonPath.read<Boolean>(json, "$.available")).isTrue()
        assertThat(JsonPath.read<Boolean>(json, "$.required")).isFalse()
        assertThat(JsonPath.read<List<String>>(json, "$.questions")).hasSize(2).allMatch { it.contains("(오프라인 예시)") }
    }

    @Test
    fun `defense answers and the status update reach the prompt, unanswered ones are marked`() {
        val sessionId = UUID.randomUUID()
        val followup = Submission(
            sessionId = sessionId,
            phase = "FOLLOWUP",
            structuredJson = """{"defense":[{"question":"SPOF는?","answer":"Redis 장애 시 DB 유니크 제약으로 폴백합니다."},{"question":"10배면?","answer":""}]}""",
        )
        val defenseSection = missionService.defensePromptSection(followup)!!
        assertThat(defenseSection)
            .contains("Q. SPOF는?").contains("A. Redis 장애 시 DB 유니크 제약으로 폴백합니다.")
            .contains("Q. 10배면?").contains("A. (답하지 않음)")

        val incident = Submission(
            sessionId = sessionId,
            phase = "INCIDENT",
            structuredJson = """{"statusUpdate":"결제 지연을 조사 중입니다. 30분 뒤 다시 알리겠습니다."}""",
        )
        assertThat(missionService.statusUpdatePromptSection(incident))
            .contains("## 고객 공지 초안").contains("결제 지연을 조사 중입니다.").contains("과장 여부")

        assertThat(missionService.statusUpdatePromptSection(Submission(sessionId = sessionId, phase = "INCIDENT"))).isNull()
        assertThat(missionService.defensePromptSection(Submission(sessionId = sessionId, phase = "FOLLOWUP"))).isNull()
    }
}
