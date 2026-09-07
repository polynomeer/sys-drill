package com.sysdrill.backend.certification

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.evaluation.Evaluation
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
import com.sysdrill.backend.support.bearerHeader
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Phase 5 — "SysDrill Certified Incident Responder" (docs/adr/0032): a live-computed judgement over official Flyway-seeded domains only, never issued or persisted. */
@SpringBootTest
@AutoConfigureMockMvc
class CertificationControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val submissionRepository: SubmissionRepository,
    @Autowired val evaluationRepository: EvaluationRepository,
    @Value("\${sysdrill.certification.passing-score}") val passingScore: Int,
) {
    private fun createUser(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private val officialScenarios by lazy { scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null } }

    private fun completeOfficialDomain(userId: UUID, domain: String, score: Int) {
        val scenario = officialScenarios.first { it.domain == domain }
        val version = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenario.id!!, "PUBLISHED")!!
        completeSession(userId, version.id!!, score)
    }

    private fun completeSession(userId: UUID, scenarioVersionId: UUID, score: Int) {
        val session = sessionRepository.save(
            Session(userId = userId, scenarioVersionId = scenarioVersionId, status = SessionStatus.COMPLETED, completedAt = Instant.now())
        )
        val submission = submissionRepository.save(Submission(sessionId = session.id!!, phase = "INITIAL"))
        evaluationRepository.save(Evaluation(submissionId = submission.id!!, totalScore = score, isActive = true))
    }

    @Test
    fun `a user with no completed sessions is not certified in any domain`() {
        val user = createUser("cert-empty")

        val response = mockMvc.perform(get("/certifications/me").header("Authorization", bearerHeader(user.id!!)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Boolean>(response, "$.certified")).isFalse()
        val domains = JsonPath.read<List<Map<String, Any?>>>(response, "$.domains")
        assertThat(domains).isNotEmpty()
        assertThat(domains).allSatisfy { assertThat(it["passed"]).isEqualTo(false) }
        assertThat(domains).allSatisfy { assertThat(it["bestScore"]).isNull() }
    }

    @Test
    fun `passing only some official domains is not enough for overall certification`() {
        val user = createUser("cert-partial")
        completeOfficialDomain(user.id!!, "coupon", passingScore + 10)

        val response = mockMvc.perform(get("/certifications/me").header("Authorization", bearerHeader(user.id!!)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Boolean>(response, "$.certified")).isFalse()
        val domains = JsonPath.read<List<Map<String, Any?>>>(response, "$.domains")
        val coupon = domains.first { it["domain"] == "coupon" }
        assertThat(coupon["passed"]).isEqualTo(true)
        assertThat(domains.filter { it["domain"] != "coupon" }).allSatisfy { assertThat(it["passed"]).isEqualTo(false) }
    }

    @Test
    fun `passing every official domain yields overall certification`() {
        val user = createUser("cert-full")
        officialScenarios.map { it.domain }.distinct().forEach { domain ->
            completeOfficialDomain(user.id!!, domain, passingScore + 5)
        }

        mockMvc.perform(get("/certifications/me").header("Authorization", bearerHeader(user.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.certified").value(true))
    }

    @Test
    fun `a high score on a marketplace scenario does not count toward certification`() {
        val creator = createUser("cert-mp-creator")
        val publishResponse = mockMvc.perform(
            post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content(
                    """{"title":"인증 무관 시나리오","difficulty":"EASY","domain":"cert-irrelevant",
                        "initialPrompt":"초기","followupPrompt":"꼬리"}"""
                )
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = UUID.fromString(JsonPath.read(publishResponse, "$.id"))
        val version = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId, "PUBLISHED")!!
        completeSession(creator.id!!, version.id!!, 100)

        val response = mockMvc.perform(get("/certifications/me").header("Authorization", bearerHeader(creator.id!!)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(JsonPath.read<Boolean>(response, "$.certified")).isFalse()
        val domains = JsonPath.read<List<Map<String, Any?>>>(response, "$.domains")
        assertThat(domains).noneMatch { it["domain"] == "cert-irrelevant" }
    }

    @Test
    fun `GET certifications by id is public, GET certifications me requires auth`() {
        val user = createUser("cert-public")

        mockMvc.perform(get("/certifications/${user.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("cert-public"))

        mockMvc.perform(get("/certifications/me"))
            .andExpect(status().isUnauthorized)
    }
}
