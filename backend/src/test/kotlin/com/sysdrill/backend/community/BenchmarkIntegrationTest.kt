package com.sysdrill.backend.community

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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 벤치마크는 **한 시나리오 버전을 푼 모든 사용자**를 집계하므로, 표본 수는 이
 * 테스트가 만든 세션만이 아니라 DB에 이미 있는 세션까지 포함한다. 따라서
 * `sampleSize` 같은 절대값을 단언하면 개발 머신(누적 데이터 있음)과 CI(매번
 * 새 DB)에서 결과가 달라진다.
 *
 * 대신 **표본 하한을 극단으로 설정해** 어떤 데이터 상태에서도 결정론적으로
 * 한쪽 분기만 타게 만든다 — 검증 대상은 표본의 개수가 아니라 "하한 아래에서는
 * 감추고 위에서는 연다"는 규칙 자체다(docs/TESTING.md §1의 결정론 기준).
 */
abstract class BenchmarkIntegrationTestBase(
    protected val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) {
    protected fun newUser(): UUID = userRepository.save(
        User(email = "bench-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "bench-user")
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    /** INITIAL → FOLLOWUP → INCIDENT → COMPLETED. */
    protected fun completeSession(userId: UUID): UUID {
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
        return sessionId
    }

    protected fun getBenchmark(sessionId: UUID, userId: UUID) =
        mockMvc.perform(get("/sessions/$sessionId/benchmark").header("Authorization", bearerHeader(userId)))
}

/** 하한을 도달 불가능하게 높여, 어떤 누적 데이터가 있어도 반드시 감추는 분기를 탄다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.community.benchmark.min-sample-size=1000000"])
class BenchmarkSuppressedIntegrationTest(
    @Autowired mockMvc: MockMvc,
    @Autowired userRepository: UserRepository,
    @Autowired sessionRepository: SessionRepository,
) : BenchmarkIntegrationTestBase(mockMvc, userRepository, sessionRepository) {

    @Test
    fun `표본이 하한에 못 미치면 내 값만 주고 분포와 순위는 감춘다`() {
        val userId = newUser()
        val sessionId = completeSession(userId)

        getBenchmark(sessionId, userId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.minSampleSize").value(1000000))
            // 오프라인 폴백 평가는 매 단계 60점이므로 세션 평균도 60 — 내 값은 감추지 않는다.
            .andExpect(jsonPath("$.score.mine").value(60))
            .andExpect(jsonPath("$.score.distribution").doesNotExist())
            .andExpect(jsonPath("$.score.topPercent").doesNotExist())
            .andExpect(jsonPath("$.mttrSeconds.distribution").doesNotExist())
    }
}

/** 하한을 1로 낮춰, 내 세션 하나만으로도 반드시 여는 분기를 탄다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.community.benchmark.min-sample-size=1"])
class BenchmarkOpenIntegrationTest(
    @Autowired mockMvc: MockMvc,
    @Autowired userRepository: UserRepository,
    @Autowired sessionRepository: SessionRepository,
) : BenchmarkIntegrationTestBase(mockMvc, userRepository, sessionRepository) {

    @Test
    fun `하한을 넘으면 분포와 순위가 열리고 지표별 방향이 함께 온다`() {
        val userId = newUser()
        val sessionId = completeSession(userId)

        getBenchmark(sessionId, userId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.score.distribution.p50").isNumber)
            .andExpect(jsonPath("$.score.distribution.p90").isNumber)
            .andExpect(jsonPath("$.score.topPercent").isNumber)
            // 방향은 지표의 성질이라 데이터와 무관하게 고정이다.
            .andExpect(jsonPath("$.score.higherIsBetter").value(true))
            .andExpect(jsonPath("$.mttdSeconds.higherIsBetter").value(false))
            .andExpect(jsonPath("$.mttrSeconds.higherIsBetter").value(false))
    }

    @Test
    fun `남의 세션 벤치마크는 404 — 조직 전용 시나리오의 집계가 새지 않는다`() {
        val owner = newUser()
        val sessionId = completeSession(owner)

        getBenchmark(sessionId, newUser()).andExpect(status().isNotFound)
    }

    @Test
    fun `인시던트에 도달하지 않은 세션은 MTTR 이 비지만 오류가 아니다`() {
        val userId = newUser()
        val sessionId = mockMvc.startSession(userId) // 제출 없이 바로 조회

        getBenchmark(sessionId, userId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mttrSeconds.mine").doesNotExist())
    }
}
