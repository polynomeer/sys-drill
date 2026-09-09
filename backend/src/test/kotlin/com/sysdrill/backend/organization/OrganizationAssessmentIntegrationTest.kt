package com.sysdrill.backend.organization

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.reporting.Report
import com.sysdrill.backend.reporting.ReportRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.FakeEmailConfig
import com.sysdrill.backend.support.FakeEmailSender
import com.sysdrill.backend.support.bearerHeader
import java.time.Instant
import java.util.UUID
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Phase 5 — 채용/역량 평가 상품화 (docs/adr/0033): OrganizationInvitation's email-bound token, scoped to one scenario. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeEmailConfig::class)
class OrganizationAssessmentIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val reportRepository: ReportRepository,
    @Autowired val emailSender: FakeEmailSender,
) {
    private fun createUser(prefix: String, email: String? = null): User =
        userRepository.save(User(email = email ?: "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private fun createOrg(adminId: UUID): UUID {
        val response = mockMvc.perform(
            post("/organizations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"name":"평가 테스트 조직"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    /** Returns (assessmentId, token). */
    private fun createAssessment(orgId: UUID, adminId: UUID, candidateEmail: String, scenarioId: UUID = COUPON_SCENARIO_ID): Pair<String, String> {
        val response = mockMvc.perform(
            post("/organizations/$orgId/assessments").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"candidateEmail":"$candidateEmail","scenarioId":"$scenarioId"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read<String>(response, "$.id") to JsonPath.read<String>(response, "$.token")
    }

    @Test
    fun `creating an assessment emails the candidate their invite link`() {
        val admin = createUser("email-admin")
        val orgId = createOrg(admin.id!!)
        emailSender.sent.clear()

        val (_, token) = createAssessment(orgId, admin.id!!, "candidate-${UUID.randomUUID()}@example.com")

        assertThat(emailSender.sent).anyMatch { it.body.contains(token) }
    }

    @Test
    fun `only the candidate whose email matches the assessment can start it`() {
        val admin = createUser("assess-admin")
        val orgId = createOrg(admin.id!!)
        val candidateEmail = "candidate-${UUID.randomUUID()}@example.com"

        val createResponse = mockMvc.perform(
            post("/organizations/$orgId/assessments").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"candidateEmail":"$candidateEmail","scenarioId":"$COUPON_SCENARIO_ID"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("NOT_STARTED"))
            .andReturn().response.contentAsString
        // Same shape as OrganizationInvitationResponse: the token comes back so the admin
        // can copy it out-of-band (ADR-0023) — also re-exposed by the list endpoint below.
        val token = JsonPath.read<String>(createResponse, "$.token")

        // Preview is genuinely public (docs/adr/0033) — unlike GET /organizations/invitations/{token},
        // a candidate is typically a first-time visitor with no account and no token to send yet.
        mockMvc.perform(get("/organizations/assessments/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.candidateEmail").value(candidateEmail))
            .andExpect(jsonPath("$.alreadyStarted").value(false))

        val impostor = createUser("assess-impostor")
        mockMvc.perform(post("/organizations/assessments/$token/start").header("Authorization", bearerHeader(impostor.id!!)))
            .andExpect(status().isNotFound)

        val candidate = createUser("assess-candidate", candidateEmail)
        mockMvc.perform(post("/organizations/assessments/$token/start").header("Authorization", bearerHeader(candidate.id!!)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.domain").value("coupon"))

        // Starting again with the same (now-consumed) token is a conflict.
        mockMvc.perform(post("/organizations/assessments/$token/start").header("Authorization", bearerHeader(candidate.id!!)))
            .andExpect(status().isConflict)

        mockMvc.perform(get("/organizations/$orgId/assessments").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
    }

    @Test
    fun `admin can read the report only once the candidate has completed the session`() {
        val admin = createUser("assess-admin2")
        val orgId = createOrg(admin.id!!)
        val candidateEmail = "candidate2-${UUID.randomUUID()}@example.com"
        val (assessmentId, token) = createAssessment(orgId, admin.id!!, candidateEmail)
        val candidate = createUser("assess-candidate2", candidateEmail)

        mockMvc.perform(get("/organizations/$orgId/assessments/$assessmentId/report").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isNotFound)

        val startResponse = mockMvc.perform(post("/organizations/assessments/$token/start").header("Authorization", bearerHeader(candidate.id!!)))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = UUID.fromString(JsonPath.read(startResponse, "$.id"))

        mockMvc.perform(get("/organizations/$orgId/assessments/$assessmentId/report").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isNotFound)

        val session = sessionRepository.findById(sessionId).orElseThrow()
        session.status = SessionStatus.COMPLETED
        session.completedAt = Instant.now()
        sessionRepository.save(session)
        reportRepository.save(Report(sessionId = sessionId, version = 1, summary = "총 1개 단계를 완료했습니다. 평균 점수 88/100."))

        mockMvc.perform(get("/organizations/$orgId/assessments/$assessmentId/report").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary").value("총 1개 단계를 완료했습니다. 평균 점수 88/100."))

        mockMvc.perform(get("/organizations/$orgId/assessments").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
    }

    @Test
    fun `a non-admin or an admin of a different organization cannot manage or view assessments`() {
        val admin = createUser("assess-admin3")
        val member = createUser("assess-member")
        val otherAdmin = createUser("assess-other-admin")
        val orgId = createOrg(admin.id!!)
        val otherOrgId = createOrg(otherAdmin.id!!)
        val candidateEmail = "candidate3-${UUID.randomUUID()}@example.com"
        val (assessmentId, _) = createAssessment(orgId, admin.id!!, candidateEmail)

        mockMvc.perform(
            post("/organizations/$orgId/assessments").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(member.id!!))
                .content("""{"candidateEmail":"$candidateEmail","scenarioId":"$COUPON_SCENARIO_ID"}""")
        ).andExpect(status().isNotFound)

        mockMvc.perform(get("/organizations/$orgId/assessments").header("Authorization", bearerHeader(otherAdmin.id!!)))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/organizations/$orgId/assessments/$assessmentId/report").header("Authorization", bearerHeader(otherAdmin.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `an assessment cannot be created for another organization's private scenario`() {
        val admin = createUser("assess-admin4")
        val otherAdmin = createUser("assess-other-admin2")
        val orgId = createOrg(admin.id!!)
        val otherOrgId = createOrg(otherAdmin.id!!)
        val otherScenarioResponse = mockMvc.perform(
            post("/organizations/$otherOrgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(otherAdmin.id!!))
                .content(
                    """{"title":"타 조직 전용","difficulty":"EASY","domain":"other-org-only",
                        "initialPrompt":"초기","followupPrompt":"꼬리"}"""
                )
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val otherScenarioId = JsonPath.read<String>(otherScenarioResponse, "$.id")

        mockMvc.perform(
            post("/organizations/$orgId/assessments").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"candidateEmail":"whoever@example.com","scenarioId":"$otherScenarioId"}""")
        ).andExpect(status().isNotFound)
    }
}
