package com.sysdrill.backend.community

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * ADR-0042 / docs/LEARNING_COMMUNITY_PLAN.md §6.6.
 *
 * 랭킹은 **모든 사용자**를 집계하므로 순위·백분위·참여자 수는 누적 데이터에
 * 따라 달라진다(벤치마크와 같은 성질). 그래서 절대 순위는 단언하지 않고,
 * 데이터 상태와 무관하게 참인 두 가지만 단언한다 —
 * 하나는 **내 점수**(내 세션만으로 결정된다 — 오프라인 폴백은 매 단계 60점),
 * 다른 하나는 **불변식**(반복해도 오르지 않는다, 노출을 끄면 보드에서 사라진다).
 *
 * 난이도 가중은 값을 박아 넣지 않고 [DrillScoreService.weightOf] 로 계산한다 —
 * 공식 시나리오의 난이도는 이미 한 번 조정된 적이 있고(V43), 그때 깨져야 할 것은
 * 수식이 아니라 수식을 베껴 쓴 테스트이기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RankingIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val drillScoreService: DrillScoreService,
    @Autowired val rankingService: RankingService,
) {

    private fun newUser(nickname: String = "rank-user"): UUID = userRepository.save(
        User(email = "rank-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = nickname)
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun completeSession(userId: UUID) {
        val sessionId = mockMvc.startSession(userId)
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
    }

    @Test
    fun `훈련하기 전에는 0점 훈련생이고 근거도 비어 있다`() {
        val userId = newUser()

        mockMvc.perform(get("/community/rankings/me").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.tier").value("TRAINEE"))
            .andExpect(jsonPath("$.tierLabel").value("훈련생"))
            .andExpect(jsonPath("$.breakdown").isEmpty)
            // 다음 목표는 항상 보여준다 — 0점이어도 "100점 남았다"를 알 수 있어야 한다.
            .andExpect(jsonPath("$.pointsToNextTier").value(100))
            .andExpect(jsonPath("$.nextTierLabel").value("운영자"))
    }

    @Test
    fun `세션을 완료하면 점수와 근거가 생긴다`() {
        val userId = newUser()
        completeSession(userId)
        val expected = expectedCouponPoints(userId)

        mockMvc.perform(get("/community/rankings/me").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.score").value(expected))
            .andExpect(jsonPath("$.breakdown.length()").value(1))
            .andExpect(jsonPath("$.breakdown[0].domain").value("coupon"))
            // 오프라인 폴백 평가는 매 단계 60점이므로 세션 평균도 60이다.
            .andExpect(jsonPath("$.breakdown[0].bestScore").value(60))
            .andExpect(jsonPath("$.breakdown[0].points").value(expected))
            // 근거에는 사람이 읽을 제목이 있어야 한다 — 도메인 슬러그만으로는 설명이 안 된다.
            .andExpect(jsonPath("$.breakdown[0].title").value("선착순 쿠폰"))
    }

    @Test
    fun `같은 도메인을 반복해도 점수가 오르지 않는다`() {
        val userId = newUser()
        completeSession(userId)
        val afterFirst = drillScoreService.forUser(userId)

        completeSession(userId)
        val afterSecond = drillScoreService.forUser(userId)

        // ADR-0042 의 핵심 — 합산이 아니라 도메인별 최고점이라 반복에 보상이 없다.
        assertThat(afterSecond.total).isEqualTo(afterFirst.total)
        assertThat(afterSecond.domainBests).hasSameSizeAs(afterFirst.domainBests)
    }

    @Test
    fun `종합 보드에 내가 나오고 노출을 끄면 사라진다`() {
        val nickname = "rank-${UUID.randomUUID().toString().take(8)}"
        val userId = newUser(nickname)
        completeSession(userId)
        val expected = expectedCouponPoints(userId)

        // 누적 데이터 때문에 상위 20위 안에 든다고 보장할 수 없으므로, 보드 전체를
        // 훑을 수 있게 limit 를 넉넉히 준 서비스 호출로 확인한다.
        assertThat(rankedNicknames(userId)).contains(nickname)

        mockMvc.perform(
            put("/community/rankings/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"optOut":true}""")
        )
            .andExpect(status().isOk)
            // 노출을 꺼도 내 점수와 티어는 그대로 본다 — 감추는 건 남에게 보이는 쪽뿐이다.
            .andExpect(jsonPath("$.optedOut").value(true))
            .andExpect(jsonPath("$.score").value(expected))
            .andExpect(jsonPath("$.rank").doesNotExist())
            .andExpect(jsonPath("$.topPercent").doesNotExist())

        assertThat(rankedNicknames(userId)).doesNotContain(nickname)
    }

    @Test
    fun `모르는 보드 이름은 400 - 조용히 종합 보드로 대체하지 않는다`() {
        val userId = newUser()

        mockMvc.perform(get("/community/rankings?board=nope").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰 없이는 랭킹을 볼 수 없다`() {
        mockMvc.perform(get("/community/rankings")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/community/rankings/me")).andExpect(status().isUnauthorized)
    }

    /**
     * 완료한 도메인이 coupon 하나뿐인 사용자의 기대 점수 — 60점 × 그 시나리오의
     * 난이도 가중. 난이도는 시드에서 읽어 오므로 시드가 바뀌어도 따라간다.
     */
    private fun expectedCouponPoints(userId: UUID): Int {
        val best = drillScoreService.forUser(userId).domainBests.single { it.domain == "coupon" }
        return (60 * DrillScoreService.weightOf(best.difficulty)).roundToInt()
    }

    private fun rankedNicknames(viewerId: UUID): List<String> =
        rankingService.board(RankingBoard.OVERALL, null, viewerId, limit = Int.MAX_VALUE)
            .entries.map { it.nickname }
}
