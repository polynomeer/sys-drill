package com.sysdrill.backend.mission

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.SkillProfile
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersion
import com.sysdrill.backend.scenario.ScenarioVersionRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round E8 — M1 (clarifying questions) and the FOLLOWUP variant pin.
 *
 * No official scenario has mission content yet (that's ADR-0048's single v2 bump, Round E24),
 * so this builds its own scenario straight through the repositories.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionClarificationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val skillProfileRepository: SkillProfileRepository,
    @Autowired val missionService: MissionService,
    @Autowired val submissionRepository: com.sysdrill.backend.submission.SubmissionRepository,
) {

    private fun newUser(): UUID =
        userRepository.save(User(email = "mission-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "mission")).id!!

    private fun missionScenario(): UUID {
        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = "미션 테스트 ${UUID.randomUUID()}"))
        val scenario = scenarioRepository.save(
            Scenario(
                contentId = content.id!!,
                domain = "coupon",
                // A creator keeps it out of the official pool (Drill Score, certification) — other tests count that pool.
                creatorUserId = newUser(),
                baseRequirements = """{"functional":["쿠폰 발급"],"nonFunctional":{"targetUsers":1000000,"totalCoupons":100000}}""",
            )
        )
        val version = scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED"))
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!, stepOrder = 1, stepType = "INITIAL",
                content = """
                {"prompt":"선착순 쿠폰 시스템을 설계하세요. 이벤트 시작 시 약 100만 명이 접속합니다.",
                 "clarifications":[
                   {"id":"total","question":"쿠폰 수량은 얼마인가요?","answer":"10만 개입니다.","critical":true,"requirementKey":"totalCoupons"},
                   {"id":"dup","question":"중복 발급이 허용되나요?","answer":"절대 허용되지 않습니다.","critical":true},
                   {"id":"color","question":"버튼 색은 정해졌나요?","answer":"디자인팀이 정합니다."}
                 ],
                 "estimation":[
                   {"key":"peakRps","label":"피크 발급 요청","unit":"req/s","answer":30000},
                   {"key":"writeRps","label":"초당 발급 확정","unit":"req/s","answer":2000}
                 ]}
                """.trimIndent(),
            )
        )
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!, stepOrder = 2, stepType = "FOLLOWUP",
                triggerCondition = """{"afterStepOrder":1}""",
                content = """
                {"variants":[
                  {"key":"idem","targetRiskKey":"MISSING_IDEMPOTENCY","prompt":"재시도가 몰립니다."},
                  {"key":"rate","targetRiskKey":"MISSING_RATE_LIMIT","prompt":"트래픽이 20배가 됩니다."}
                ]}
                """.trimIndent(),
            )
        )
        return scenario.id!!
    }

    private fun clarifications(sessionId: UUID, userId: UUID): String = mockMvc.perform(
        get("/sessions/$sessionId/clarifications").header("Authorization", bearerHeader(userId))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    private fun ask(sessionId: UUID, userId: UUID, questionId: String) = mockMvc.perform(
        post("/sessions/$sessionId/clarifications/$questionId").header("Authorization", bearerHeader(userId))
    )

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun submit(sessionId: UUID, userId: UUID) = mockMvc.perform(
        post("/sessions/$sessionId/submissions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(userId))
            .content("""{"rawText":"Redis 카운터로 처리합니다.","clientRequestId":"${UUID.randomUUID()}"}""")
    ).andExpect(status().isCreated)

    @Test
    fun `answers stay hidden until asked, and the overview doesn't print a requirement a question reveals`() {
        val scenarioId = missionScenario()
        val user = newUser()
        val overview = mockMvc.perform(get("/scenarios/$scenarioId").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Map<String, Any>>(overview, "$.baseRequirements.nonFunctional")).containsOnlyKeys("targetUsers")

        val sessionId = mockMvc.startSession(user, scenarioId)
        val before = clarifications(sessionId, user)
        assertThat(JsonPath.read<Boolean>(before, "$.available")).isTrue()
        assertThat(JsonPath.read<List<Any?>>(before, "$.questions[*].answer")).containsOnlyNulls()
        assertThat(JsonPath.read<List<Any?>>(before, "$.questions[*].critical")).containsOnlyNulls()

        ask(sessionId, user, "total").andExpect(status().isOk)
        val after = clarifications(sessionId, user)
        assertThat(JsonPath.read<String>(after, "$.questions[0].id")).isEqualTo("total")
        assertThat(JsonPath.read<String>(after, "$.questions[0].answer")).isEqualTo("10만 개입니다.")
        assertThat(JsonPath.read<Any?>(after, "$.questions[1].answer")).isNull()

        ask(sessionId, user, "nope").andExpect(status().isNotFound)
        ask(sessionId, newUser(), "dup").andExpect(status().isNotFound) // not the owner
    }

    @Test
    fun `the prompt section separates what was asked from the critical requirements that weren't`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, missionScenario())
        ask(sessionId, user, "total").andExpect(status().isOk)
        ask(sessionId, user, "color").andExpect(status().isOk)

        val section = missionService.clarificationPromptSection(sessionRepository.findById(sessionId).orElseThrow())!!
        val (asked, missed) = section.split("## 확인하지 않은 핵심 요구사항")
        assertThat(asked).contains("쿠폰 수량은 얼마인가요?").contains("버튼 색은").doesNotContain("중복 발급")
        assertThat(missed).contains("중복 발급이 허용되나요?").contains("절대 허용되지 않습니다.").doesNotContain("쿠폰 수량")
    }

    @Test
    fun `asking closes at submit, the report summary counts critical questions, and the tail-design variant is pinned`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, missionScenario())
        ask(sessionId, user, "dup").andExpect(status().isOk)

        submit(sessionId, user)
        ask(sessionId, user, "total").andExpect(status().isConflict)
        val summary = clarifications(sessionId, user)
        assertThat(JsonPath.read<Boolean>(summary, "$.canAsk")).isFalse()
        assertThat(JsonPath.read<Int>(summary, "$.criticalAsked")).isEqualTo(1)
        assertThat(JsonPath.read<Int>(summary, "$.criticalTotal")).isEqualTo(2)
        assertThat(JsonPath.read<List<String>>(summary, "$.questions[*].answer")).doesNotContainNull()

        awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        val pinned = missionService.state(sessionRepository.findById(sessionId).orElseThrow()).followupVariantKey
        assertThat(pinned).isIn("idem", "rate")
        val promptBefore = currentPrompt(sessionId, user)

        // Make the *other* variant the adaptive pick — the shown twist must not change.
        val other = if (pinned == "idem") "MISSING_RATE_LIMIT" else "MISSING_IDEMPOTENCY"
        val profile = skillProfileRepository.findByUserId(user) ?: SkillProfile(userId = user)
        profile.weaknesses = """{"$other":99}"""
        skillProfileRepository.save(profile)
        assertThat(currentPrompt(sessionId, user)).isEqualTo(promptBefore)
    }

    /** PLAN.md Round E9 (M2) — estimates ride on the INITIAL submission and are judged after it. */
    @Test
    fun `estimation fields come without answers, then judged results after the INITIAL submit`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, missionScenario())
        val before = mockMvc.perform(get("/sessions/$sessionId/estimation").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(before, "$.open")).isTrue()
        assertThat(before).doesNotContain("30000").doesNotContain("answer")

        mockMvc.perform(
            post("/sessions/$sessionId/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user))
                .content("""{"rawText":"설계","clientRequestId":"${UUID.randomUUID()}","structuredJson":"{\"estimates\":{\"peakRps\":20000,\"writeRps\":100}}"}""")
        ).andExpect(status().isCreated)

        val after = mockMvc.perform(get("/sessions/$sessionId/estimation").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(after, "$.results[*].direction")).containsExactly("ON_TARGET", "UNDER")

        val session = sessionRepository.findById(sessionId).orElseThrow()
        val submission = submissionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).first()
        val section = missionService.estimationPromptSection(session, submission)!!
        assertThat(section).contains("피크 발급 요청: 사용자 20,000 req/s / 실제 30,000 req/s → 적중")
        assertThat(section).contains("초당 발급 확정: 사용자 100 req/s / 실제 2,000 req/s → 과소 추정 (실제의 5%)")
    }

    private fun currentPrompt(sessionId: UUID, userId: UUID): String = JsonPath.read(
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString,
        "$.currentStepPrompt",
    )
}
