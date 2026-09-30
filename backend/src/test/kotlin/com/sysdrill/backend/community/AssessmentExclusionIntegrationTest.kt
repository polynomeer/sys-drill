package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.certification.CertificationService
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.ScenarioStatsService
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.FakeEmailConfig
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ADR-0043 — a hiring-assessment session is the organization's evaluation
 * artifact: it never becomes public content (writeups) and never feeds
 * community or public aggregates (Drill Score/ranking, certification,
 * benchmark population, scenario stats). Sessions on organization scenarios
 * can't be shared either (docs/LEARNING_COMMUNITY_PLAN.md §7).
 *
 * The assessment runs through the real flow (org → assessment → candidate
 * starts via token → three graded phases), since "it's just a session" is
 * exactly why these leaks were possible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeEmailConfig::class)
class AssessmentExclusionIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val drillScoreService: DrillScoreService,
    @Autowired val certificationService: CertificationService,
    @Autowired val scenarioStatsService: ScenarioStatsService,
) {

    private fun user(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private fun json(method: String, path: String, userId: UUID, body: String? = null): String {
        val builder = when (method) {
            "POST" -> post(path)
            "PUT" -> put(path)
            else -> get(path)
        }.header("Authorization", bearerHeader(userId))
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body)
        return mockMvc.perform(builder).andReturn().response.contentAsString
    }

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(60)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun finish(sessionId: UUID, userId: UUID, phases: Int) {
        repeat(phases) {
            mockMvc.perform(
                post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"Redis 재고 카운터와 멱등키로 중복 발급을 막습니다."}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)
    }

    private fun createOrg(adminId: UUID): UUID =
        UUID.fromString(JsonPath.read(json("POST", "/organizations", adminId, """{"name":"채용 조직"}"""), "$.id"))

    /** Candidate completes a coupon assessment through the real token flow. Returns the session id. */
    private fun completedAssessment(admin: User, candidate: User): UUID {
        val orgId = createOrg(admin.id!!)
        val created = json(
            "POST", "/organizations/$orgId/assessments", admin.id!!,
            """{"candidateEmail":"${candidate.email}","scenarioId":"$COUPON_SCENARIO_ID"}""",
        )
        val token = JsonPath.read<String>(created, "$.token")
        val sessionId = UUID.fromString(JsonPath.read(json("POST", "/organizations/assessments/$token/start", candidate.id!!), "$.id"))
        finish(sessionId, candidate.id!!, phases = 3)
        return sessionId
    }

    @Test
    fun `an assessment session can't be shared and feeds no score, certification, benchmark or stats`() {
        val admin = user("hiring-admin")
        val candidate = user("candidate")
        val statsBefore = scenarioStatsService.byScenarioId(listOf(COUPON_SCENARIO_ID))[COUPON_SCENARIO_ID]?.completedCount ?: 0

        val sessionId = completedAssessment(admin, candidate)

        // Writeups: publishing is refused.
        mockMvc.perform(
            put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(candidate.id!!))
                .content("""{"visibility":"PUBLIC","anonymous":false}""")
        ).andExpect(status().isConflict)

        // Even a row made public before the rule existed stays invisible to others.
        sessionRepository.save(sessionRepository.findById(sessionId).orElseThrow().apply {
            visibility = "PUBLIC"; sharedAt = Instant.now()
        })
        val viewer = user("viewer")
        finishCouponSessionFor(viewer)
        assertThat(json("GET", "/scenarios/$COUPON_SCENARIO_ID/writeups", viewer.id!!)).doesNotContain(sessionId.toString())
        mockMvc.perform(get("/writeups/$sessionId").header("Authorization", bearerHeader(viewer.id!!)))
            .andExpect(status().isNotFound)

        // Drill Score / ranking and certification: the candidate has nothing on coupon.
        assertThat(drillScoreService.forUser(candidate.id!!).domainBests.map { it.domain }).doesNotContain("coupon")
        val coupon = certificationService.status(candidate.id!!).domains.first { it.domain == "coupon" }
        assertThat(coupon.bestScore).isNull()

        // Scenario stats: only the viewer's ordinary completion was added.
        val statsAfter = scenarioStatsService.byScenarioId(listOf(COUPON_SCENARIO_ID))[COUPON_SCENARIO_ID]!!.completedCount
        assertThat(statsAfter).isEqualTo(statsBefore + 1)

        // Benchmark: the assessment isn't in the viewer's population, but still sees its own figures.
        val viewerSession = sessionRepository.findByUserIdOrderByStartedAtDesc(viewer.id!!).first().id!!
        val viewerSample = JsonPath.read<Int>(json("GET", "/sessions/$viewerSession/benchmark", viewer.id!!), "$.sampleSize")
        val ownSample = JsonPath.read<Int>(json("GET", "/sessions/$sessionId/benchmark", candidate.id!!), "$.sampleSize")
        assertThat(ownSample).isEqualTo(viewerSample + 1)
    }

    @Test
    fun `a session on an organization scenario can't be shared`() {
        val admin = user("org-admin")
        val orgId = createOrg(admin.id!!)
        val scenario = json(
            "POST", "/organizations/$orgId/scenarios", admin.id!!,
            """{"title":"사내 정산","domain":"internal-settlement","initialPrompt":"사내 정산 배치를 설계하세요.","followupPrompt":"건수가 10배가 됐습니다."}""",
        )
        val scenarioId = UUID.fromString(JsonPath.read(scenario, "$.id"))
        val sessionId = mockMvc.startSession(admin.id!!, scenarioId = scenarioId)
        finish(sessionId, admin.id!!, phases = 2)

        mockMvc.perform(
            put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"visibility":"PUBLIC","anonymous":false}""")
        ).andExpect(status().isConflict)
    }

    private fun finishCouponSessionFor(user: User) {
        val sessionId = mockMvc.startSession(user.id!!)
        finish(sessionId, user.id!!, phases = 3)
    }
}
