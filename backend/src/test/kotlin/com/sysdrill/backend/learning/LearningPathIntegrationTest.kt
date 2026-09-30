package com.sysdrill.backend.learning

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.3 (슬라이스 3).
 *
 * 경로는 사용자 본인의 이력에서만 파생하므로, 벤치마크(§6.1)와 달리 누적된
 * 공유 데이터의 영향을 받지 않는다 — 매 테스트가 새 사용자를 만들면 결정론적이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LearningPathIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {

    private fun newUser(): UUID = userRepository.save(
        User(email = "path-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "path-user")
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    /** 규칙 기반 평가가 모든 개념을 "누락"으로 잡도록 일부러 빈약한 답안을 낸다. */
    private fun completeSession(userId: UUID): UUID {
        val sessionId = mockMvc.startSession(userId)
        repeat(3) {
            mockMvc.perform(
                post("/sessions/$sessionId/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"그냥 API 서버 하나로 처리합니다."}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)
        return sessionId
    }

    @Test
    fun `이력이 없으면 경로 대신 시작 안내를 준다`() {
        val userId = newUser()

        mockMvc.perform(get("/learning/path").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendedCategory").doesNotExist())
            .andExpect(jsonPath("$.steps").isEmpty)
            .andExpect(jsonPath("$.rationale").value(org.hamcrest.Matchers.containsString("아직 평가 이력이 없습니다")))
    }

    @Test
    fun `세션을 완료하면 가장 약한 역량으로 경로가 만들어지고 각 단계에 근거가 붙는다`() {
        val userId = newUser()
        completeSession(userId)

        mockMvc.perform(get("/learning/path").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendedCategory").isNotEmpty)
            .andExpect(jsonPath("$.categoryLabel").isNotEmpty)
            .andExpect(jsonPath("$.rationale").value(org.hamcrest.Matchers.containsString("가장 많습니다")))
            // max-steps 기본값 3 을 넘지 않는다 — 경로가 또 하나의 목록이 되면 안 된다.
            .andExpect(jsonPath("$.steps.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(3)))
            .andExpect(jsonPath("$.steps[0].label").isNotEmpty)
            .andExpect(jsonPath("$.steps[0].evidence").isNotEmpty)
            .andExpect(jsonPath("$.steps[0].weaknessCount").isNumber)
    }

    @Test
    fun `쿠폰 세션에서 계속 지적받은 개념은 IN_PROGRESS 로 남는다`() {
        val userId = newUser()
        completeSession(userId)

        // 빈약한 답안이라 최근 완료 세션에서도 같은 riskKey 를 다시 지적받았다.
        // 따라서 그 도메인 이력이 있는 단계는 ADDRESSED 가 될 수 없다.
        mockMvc.perform(get("/learning/path").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.steps[?(@.status == 'ADDRESSED')]")
                    .value(org.hamcrest.Matchers.empty<Any>())
            )
    }

    @Test
    fun `경로는 저장되지 않아 두 번 불러도 같은 결과가 나온다`() {
        val userId = newUser()
        completeSession(userId)

        val first = mockMvc.perform(get("/learning/path").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val second = mockMvc.perform(get("/learning/path").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString

        // 동률 정렬을 키 순서로 고정했으므로 순서까지 동일해야 한다.
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first)
    }
}
