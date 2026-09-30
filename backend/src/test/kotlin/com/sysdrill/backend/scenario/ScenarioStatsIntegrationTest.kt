package com.sysdrill.backend.scenario

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 시나리오 난이도 신호.
 *
 * 통계는 **모든 사용자**의 완료 세션을 집계하므로 누적 데이터에 따라 절대값이
 * 달라진다(벤치마크와 같은 성질). 그래서 "세션을 하나 더 완료하면 그 시나리오의
 * 완료 수가 정확히 1 증가한다"는 **상대 변화**를 단언한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScenarioStatsIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioStatsService: ScenarioStatsService,
) {

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    @Test
    fun `세션을 완료하면 그 시나리오의 완료 수가 1 늘고 평균 점수가 생긴다`() {
        val userId = userRepository.save(
            User(email = "stats-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "stats-user")
        ).id!!

        val sessionId = mockMvc.startSession(userId)
        val scenarioId = scenarioRepository.findAll()
            .first { it.domain == "coupon" && it.organizationId == null && it.creatorUserId == null }
            .id!!

        val before = scenarioStatsService.byScenarioId(listOf(scenarioId))[scenarioId]?.completedCount ?: 0

        repeat(3) {
            mockMvc.perform(
                post("/sessions/$sessionId/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"API 서버 하나로 처리합니다."}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)

        val after = scenarioStatsService.byScenarioId(listOf(scenarioId))[scenarioId]
        assertThat(after).isNotNull
        assertThat(after!!.completedCount).isEqualTo(before + 1)
        // 오프라인 폴백 평가는 매 단계 60점이므로 평균은 0~100 안의 실제 값이어야 한다.
        assertThat(after.averageScore).isNotNull().isBetween(0, 100)
    }

    @Test
    fun `완료 세션이 없는 시나리오는 통계에 나타나지 않는다`() {
        // 존재하지 않는 시나리오 id — 빈 맵이어야 하고 예외가 나면 안 된다.
        assertThat(scenarioStatsService.byScenarioId(listOf(UUID.randomUUID()))).isEmpty()
        assertThat(scenarioStatsService.byScenarioId(emptyList())).isEmpty()
    }
}
