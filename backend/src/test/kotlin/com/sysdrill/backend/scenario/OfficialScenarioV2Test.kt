package com.sysdrill.backend.scenario

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.mission.MissionService
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * PLAN.md Round E24 (ADR-0048) — the official scenarios' single v2 bump: every one carries the
 * mission content, new sessions land on v2, and the overview gives none of the answers away.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfficialScenarioV2Test(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val missionService: MissionService,
    @Autowired val objectMapper: ObjectMapper,
) {
    private val official by lazy { scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null } }

    private fun latest(scenarioId: UUID) = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId, "PUBLISHED")!!

    @Test
    fun `every official scenario's latest version is v2 with complete mission content`() {
        assertThat(official).hasSize(7)
        official.forEach { scenario ->
            val version = latest(scenario.id!!)
            assertThat(version.versionNo).describedAs(scenario.domain).isEqualTo(2)
            val steps = scenarioStepRepository.findByScenarioVersionIdOrderByStepOrder(version.id!!)
            assertThat(steps.map { it.stepType }).describedAs(scenario.domain).containsExactly("INITIAL", "FOLLOWUP", "INCIDENT")
            val initial = missionService.parseInitial(steps.first())
            assertThat(initial.clarifications.count { it.critical }).describedAs(scenario.domain).isGreaterThanOrEqualTo(3)
            assertThat(initial.estimation).describedAs(scenario.domain).isNotEmpty()
            assertThat(initial.constraints?.budgetPerMonth).describedAs(scenario.domain).isNotNull()
            val assumptionIds = initial.assumptions.map { it.id }.toSet()
            val variants = objectMapper.readTree(steps[1].content).get("variants")
            assertThat(variants.size()).isEqualTo(3)
            variants.forEach { v ->
                val breaksNode = v.get("breaks")
                val breaks: List<String> = (0 until breaksNode.size()).map { breaksNode.get(it).asString() }
                assertThat(breaks).describedAs("${scenario.domain} ${v.get("key")}").isNotEmpty()
                assertThat(assumptionIds).describedAs("${scenario.domain} ${v.get("key")}").containsAll(breaks.toList())
            }
        }
    }

    @Test
    fun `a new session runs v2, and the overview hides what the questions reveal`() {
        val user = userRepository.save(User(email = "v2-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "v2")).id!!
        val coupon = official.first { it.domain == "coupon" }
        val sessionId = mockMvc.startSession(user, coupon.id!!)
        assertThat(sessionRepository.findById(sessionId).orElseThrow().scenarioVersionId).isEqualTo(latest(coupon.id!!).id)

        val overview = mockMvc.perform(get("/scenarios/${coupon.id}").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val nonFunctional = JsonPath.read<Map<String, Any?>>(overview, "$.baseRequirements.nonFunctional")
        assertThat(nonFunctional).doesNotContainKeys("targetUsers", "totalCoupons", "duplicateIssueAllowed", "responseMode")
        assertThat(overview).doesNotContain("1만 장입니다", "100000")
        assertThat(JsonPath.read<String>(overview, "$.initialPrompt")).doesNotContain("100만")

        val clarifications = mockMvc.perform(get("/sessions/$sessionId/clarifications").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Any?>>(clarifications, "$.questions[*].answer")).allMatch { it == null }
        val estimation = mockMvc.perform(get("/sessions/$sessionId/estimation").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(estimation).doesNotContain("\"answer\"")
    }
}
