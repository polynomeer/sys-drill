package com.sysdrill.backend.mission

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** PLAN.md Round E20 — M7 assumptions (chosen at INITIAL, broken by the pinned FOLLOWUP) and M8 cost·complexity. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.evaluation.rate-limit-per-minute=100"])
class MissionAssumptionCostTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val missionService: MissionService,
    @Autowired val costEstimateService: CostEstimateService,
    @Autowired val objectMapper: tools.jackson.databind.ObjectMapper,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "m78-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "m78")).id!!

    private fun scenario(withMission: Boolean): UUID {
        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = "가정 비용 테스트 ${UUID.randomUUID()}"))
        val scenario = scenarioRepository.save(Scenario(contentId = content.id!!, domain = "notification", creatorUserId = newUser()))
        val version = scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED"))
        val mission = if (!withMission) "" else """,
             "assumptions":[{"id":"steady","text":"발송량은 하루 내내 고르다"},{"id":"provider","text":"외부 provider는 항상 200ms 안에 응답한다"},{"id":"order","text":"순서 보장은 필요 없다"}],
             "constraints":{"budgetPerMonth":1000,"teamSize":2,"opsExperience":{"queue":"LOW","cache":"HIGH"}}"""
        scenarioStepRepository.save(ScenarioStep(scenarioVersionId = version.id!!, stepOrder = 1, stepType = "INITIAL", content = """{"prompt":"알림 시스템을 설계하세요."$mission}"""))
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = version.id!!, stepOrder = 2, stepType = "FOLLOWUP", triggerCondition = """{"afterStepOrder":1}""",
                content = """{"prompt":"provider 하나가 3초씩 응답하지 않습니다.","breaks":["provider","steady"]}""",
            )
        )
        return scenario.id!!
    }

    private fun json(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    @Test
    fun `chosen assumptions are fixed at submit and the pinned follow-up shows which broke`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, scenario(withMission = true))
        val before = json("/sessions/$sessionId/assumptions", user)
        assertThat(JsonPath.read<Boolean>(before, "$.open")).isTrue()
        assertThat(JsonPath.read<List<String>>(before, "$.options[*].id")).containsExactly("steady", "provider", "order")
        assertThat(JsonPath.read<Any?>(before, "$.broken")).isNull()

        val structured = """{"assumptions":{"selected":["provider","order"],"custom":["재시도는 3회까지"]}}"""
        mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content(objectMapper.writeValueAsString(mapOf("rawText" to "설계", "clientRequestId" to UUID.randomUUID().toString(), "structuredJson" to structured)))
        ).andExpect(status().isCreated)
        awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
        val submitted = json("/sessions/$sessionId/assumptions", user)
        assertThat(JsonPath.read<Boolean>(submitted, "$.open")).isFalse()
        assertThat(JsonPath.read<List<String>>(submitted, "$.selected")).containsExactly("provider", "order")
        assertThat(JsonPath.read<List<String>>(submitted, "$.custom")).containsExactly("재시도는 3회까지")

        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        val followup = json("/sessions/$sessionId/assumptions", user)
        assertThat(JsonPath.read<List<String>>(followup, "$.broken[*].id")).containsExactly("steady", "provider")

        val session = sessionRepository.findById(sessionId).orElseThrow()
        val followupSubmission = com.sysdrill.backend.submission.Submission(sessionId = sessionId, phase = "FOLLOWUP", rawText = "x")
        val section = missionService.assumptionPromptSection(session, followupSubmission)!!
        assertThat(section).contains("외부 provider는 항상 200ms 안에 응답한다 — 사용자가 명시했던 가정")
        assertThat(section).contains("발송량은 하루 내내 고르다 — 사용자는 이 가정을 적지 않았음")
    }

    @Test
    fun `cost and complexity follow the saved canvas against the budget and team`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, scenario(withMission = true))
        assertThat(JsonPath.read<Boolean>(json("/sessions/$sessionId/cost-estimate", user), "$.drawn")).isFalse()

        val graph = """{"nodes":[
            {"id":"g","data":{"kind":"gateway"}},{"id":"s","data":{"kind":"service"}},
            {"id":"q","data":{"kind":"queue","traitValues":{"consumerCount":10}}},{"id":"c","data":{"kind":"cache"}}],
            "edges":[{"source":"g","target":"s"},{"source":"s","target":"q"},{"source":"s","target":"c"}]}"""
        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content(objectMapper.writeValueAsString(mapOf("graph" to graph)))
        ).andExpect(status().isOk)

        val estimate = json("/sessions/$sessionId/cost-estimate", user)
        // 150 + 200 + 300 + 250 nodes + 10 consumers × 30
        assertThat(JsonPath.read<Double>(estimate, "$.monthlyCost")).isEqualTo(1200.0)
        assertThat(JsonPath.read<Int>(estimate, "$.budgetDeltaPct")).isEqualTo(20)
        // gateway 8 + service 6 + queue 18×1.5 + cache 12×0.7 = 49.4
        assertThat(JsonPath.read<Int>(estimate, "$.complexity")).isEqualTo(49)
        assertThat(JsonPath.read<Int>(estimate, "$.teamCapacity")).isEqualTo(30)
        assertThat(JsonPath.read<Double>(estimate, "$.actionCostDeltas.ADD_CONSUMERS")).isEqualTo(240.0)
        assertThat(costEstimateService.promptSection(sessionRepository.findById(sessionId).orElseThrow())).contains("+20%", "팀 역량 30")
    }

    @Test
    fun `a scenario without mission content hides both`() {
        val user = newUser()
        val sessionId = mockMvc.startSession(user, scenario(withMission = false))
        assertThat(JsonPath.read<Boolean>(json("/sessions/$sessionId/assumptions", user), "$.available")).isFalse()
        assertThat(JsonPath.read<Boolean>(json("/sessions/$sessionId/cost-estimate", user), "$.available")).isFalse()
    }
}
