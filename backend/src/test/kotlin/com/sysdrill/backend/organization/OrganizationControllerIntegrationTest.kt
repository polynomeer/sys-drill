package com.sysdrill.backend.organization

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.SkillProfile
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** PLAN.md step 32 — organization creation, email-bound invitations, and ADMIN/MEMBER membership. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val invitationRepository: OrganizationInvitationRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val skillProfileRepository: SkillProfileRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    /** Fixed scenario_version id seeded by V2__seed_coupon_scenario.sql — any published version works, the dashboard test only cares about Session.status/completedAt. */
    private val couponScenarioVersionId = UUID.fromString("a0000000-0000-0000-0000-000000000003")

    private fun createUser(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private fun createOrg(adminId: UUID, name: String = "테스트 조직"): UUID {
        val response = mockMvc.perform(
            post("/organizations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"name":"$name"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun invite(orgId: UUID, adminId: UUID, email: String, role: String = "MEMBER"): String {
        val response = mockMvc.perform(
            post("/organizations/$orgId/invitations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"email":"$email","role":"$role"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.token")
    }

    @Test
    fun `creating an organization makes the creator an ADMIN member`() {
        val admin = createUser("creator")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(get("/organizations/$orgId").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myRole").value("ADMIN"))
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
    }

    @Test
    fun `a non-member gets 404 on the organization`() {
        val admin = createUser("owner")
        val outsider = createUser("outsider")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(get("/organizations/$orgId").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `an admin can invite, creating a pending invitation with a token`() {
        val admin = createUser("admin")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(
            post("/organizations/$orgId/invitations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"email":"invitee@example.com","role":"MEMBER"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.inviteeEmail").value("invitee@example.com"))
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.expired").value(false))
    }

    @Test
    fun `a non-admin member inviting is rejected as not found, not forbidden`() {
        val admin = createUser("admin")
        val member = createUser("member")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/organizations/$orgId/invitations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(member.id!!))
                .content("""{"email":"someone@example.com","role":"MEMBER"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `accepting with the matching email succeeds and a second accept is rejected`() {
        val admin = createUser("admin")
        val invitee = createUser("invitee")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, invitee.email, role = "ADMIN")

        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myRole").value("ADMIN"))

        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `accepting while logged in as a different email is rejected as not found`() {
        val admin = createUser("admin")
        val invitee = createUser("invitee")
        val impostor = createUser("impostor")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, invitee.email)

        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(impostor.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a duplicate pending invite for the same org and email is rejected`() {
        val admin = createUser("admin")
        val orgId = createOrg(admin.id!!)
        invite(orgId, admin.id!!, "dup@example.com")

        mockMvc.perform(
            post("/organizations/$orgId/invitations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"email":"dup@example.com","role":"MEMBER"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `accepting an invitation for an email that is already a member is rejected`() {
        val admin = createUser("admin")
        val invitee = createUser("invitee")
        val orgId = createOrg(admin.id!!)
        val firstToken = invite(orgId, admin.id!!, invitee.email)
        mockMvc.perform(post("/organizations/invitations/$firstToken/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isOk)

        // Manually bypass the invite-creation guard's "already a member" check to isolate accept-time enforcement.
        val secondInvitation = invitationRepository.save(
            OrganizationInvitation(
                organizationId = orgId,
                inviteeEmail = invitee.email,
                token = UUID.randomUUID().toString(),
                invitedBy = admin.id!!,
                expiresAt = Instant.now().plusSeconds(3600),
            )
        )

        mockMvc.perform(post("/organizations/invitations/${secondInvitation.token}/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `removing the last admin is blocked, both via admin-remove and self-leave`() {
        val admin = createUser("solo-admin")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(delete("/organizations/$orgId/members/${admin.id}").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isConflict)
        mockMvc.perform(post("/organizations/$orgId/leave").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `an admin can remove a non-last-admin member, who then loses access`() {
        val admin = createUser("admin")
        val member = createUser("member")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))

        mockMvc.perform(delete("/organizations/$orgId/members/${member.id}").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/organizations/$orgId").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a member who is not the last admin can leave voluntarily`() {
        val admin = createUser("admin")
        val member = createUser("member")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))

        mockMvc.perform(post("/organizations/$orgId/leave").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `an expired invitation is rejected at accept and its status stays pending`() {
        val admin = createUser("admin")
        val invitee = createUser("invitee")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, invitee.email)
        val invitation = invitationRepository.findByToken(token)!!
        invitation.expiresAt = Instant.now().minusSeconds(60)
        invitationRepository.save(invitation)

        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isConflict)
        assert(invitationRepository.findByToken(token)!!.status == OrganizationInvitationStatus.PENDING)
    }

    @Test
    fun `revoking a pending invitation blocks acceptance, and only an admin may revoke`() {
        val admin = createUser("admin")
        val member = createUser("member")
        val invitee = createUser("invitee")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, invitee.email)
        val memberToken = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$memberToken/accept").header("Authorization", bearerHeader(member.id!!)))

        val invitationId = invitationRepository.findByToken(token)!!.id
        mockMvc.perform(delete("/organizations/$orgId/invitations/$invitationId").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/organizations/$orgId/invitations/$invitationId").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isNoContent)

        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(invitee.id!!)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `unauthenticated calls to organizations endpoints are rejected`() {
        mockMvc.perform(get("/organizations")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/organizations").contentType(MediaType.APPLICATION_JSON).content("""{"name":"x"}"""))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the dashboard reports each member's completed session count, last activity, and trend`() {
        val admin = createUser("dash-admin")
        val member = createUser("dash-member")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isOk)

        sessionRepository.save(
            Session(userId = member.id!!, scenarioVersionId = couponScenarioVersionId, status = SessionStatus.COMPLETED, completedAt = Instant.now())
        )
        // Recent 3 scores (50s) well above the prior 3 (10s) — IMPROVING per SkillProfileService.trendDirection's threshold.
        skillProfileRepository.save(
            SkillProfile(userId = member.id!!, trend = objectMapper.writeValueAsString(listOf(10, 10, 10, 50, 50, 50)))
        )

        val response = mockMvc.perform(get("/organizations/$orgId/dashboard").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val members = JsonPath.read<List<Map<String, Any?>>>(response, "$.members")
        val adminEntry = members.single { it["userId"] == admin.id.toString() }
        val memberEntry = members.single { it["userId"] == member.id.toString() }

        assertThat(adminEntry["completedSessionCount"]).isEqualTo(0)
        assertThat(adminEntry["lastActiveAt"]).isNull()
        assertThat(adminEntry["trendDirection"]).isEqualTo("INSUFFICIENT_DATA")

        assertThat(memberEntry["completedSessionCount"]).isEqualTo(1)
        assertThat(memberEntry["lastActiveAt"]).isNotNull()
        assertThat(memberEntry["trendDirection"]).isEqualTo("IMPROVING")
    }

    @Test
    fun `a non-admin member gets 404 on the dashboard`() {
        val admin = createUser("dash-admin2")
        val member = createUser("dash-member2")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))

        mockMvc.perform(get("/organizations/$orgId/dashboard").header("Authorization", bearerHeader(member.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a non-member gets 404 on the dashboard`() {
        val admin = createUser("dash-admin3")
        val outsider = createUser("dash-outsider")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(get("/organizations/$orgId/dashboard").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }

    private val customScenarioBody = """
        {"title":"사내 결제 장애","difficulty":"HARD","domain":"internal-payment",
         "initialPrompt":"초기 설계 프롬프트","followupPrompt":"꼬리설계 프롬프트"}
    """.trimIndent()

    @Test
    fun `an admin can create a custom scenario, scoped to the organization`() {
        val admin = createUser("scenario-admin")
        val orgId = createOrg(admin.id!!)

        val response = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content(customScenarioBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("사내 결제 장애"))
            .andExpect(jsonPath("$.organizationId").value(orgId.toString()))
            .andReturn().response.contentAsString
        val scenarioId = UUID.fromString(JsonPath.read(response, "$.id"))

        mockMvc.perform(get("/organizations/$orgId/scenarios").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(scenarioId.toString()))
    }

    @Test
    fun `a non-admin member creating a custom scenario is rejected as not found`() {
        val admin = createUser("scenario-admin2")
        val member = createUser("scenario-member")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))

        mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(member.id!!))
                .content(customScenarioBody)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `a non-member cannot list or read the organization's custom scenarios`() {
        val admin = createUser("scenario-admin3")
        val outsider = createUser("scenario-outsider")
        val orgId = createOrg(admin.id!!)
        val response = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content(customScenarioBody)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(response, "$.id")

        mockMvc.perform(get("/organizations/$orgId/scenarios").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/organizations/$orgId/scenarios/$scenarioId").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a custom scenario is invisible on the public scenario endpoints`() {
        val admin = createUser("scenario-admin4")
        val orgId = createOrg(admin.id!!)
        val response = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content(customScenarioBody)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(response, "$.id")

        mockMvc.perform(get("/scenarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')]").isEmpty)
        mockMvc.perform(get("/scenarios/$scenarioId")).andExpect(status().isNotFound)
    }

    @Test
    fun `starting a session against a custom scenario requires organization membership`() {
        val admin = createUser("scenario-admin6")
        val member = createUser("scenario-member2")
        val outsider = createUser("scenario-outsider2")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, member.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(member.id!!)))
        val response = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content(customScenarioBody)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(response, "$.id")

        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(outsider.id!!))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(member.id!!))
                .content("""{"scenarioId":"$scenarioId"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.currentPhase").value("INITIAL"))
    }

    @Test
    fun `creating a custom scenario with a blank title is rejected`() {
        val admin = createUser("scenario-admin5")
        val orgId = createOrg(admin.id!!)

        mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"title":"","domain":"x","initialPrompt":"a","followupPrompt":"b"}""")
        ).andExpect(status().isBadRequest)
    }

    private fun startCustomScenarioSession(orgId: UUID, admin: UUID, owner: UUID): UUID {
        val response = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin))
                .content(customScenarioBody)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(response, "$.id")

        val sessionResponse = mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(owner))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(sessionResponse, "$.id"))
    }

    @Test
    fun `a fellow org member can spectate a teammate's session but not submit or advance it`() {
        val admin = createUser("gd-admin")
        val owner = createUser("gd-owner")
        val spectator = createUser("gd-spectator")
        val outsider = createUser("gd-outsider")
        val orgId = createOrg(admin.id!!)
        for (u in listOf(owner, spectator)) {
            val token = invite(orgId, admin.id!!, u.email)
            mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(u.id!!)))
        }
        val sessionId = startCustomScenarioSession(orgId, admin.id!!, owner.id!!)

        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(spectator.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isOwner").value(false))
        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(owner.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isOwner").value(true))

        mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(spectator.id!!))
                .content("""{"rawText":"x"}""")
        ).andExpect(status().isNotFound)
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(spectator.id!!)))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/sessions/$sessionId").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }

    /** Fixed scenario_version id seeded by V2__seed_coupon_scenario.sql — has a real INCIDENT step, unlike a custom scenario (step 34/ADR-0024's INITIAL+FOLLOWUP-only cut). */
    private val couponScenarioVersionIdForSpectating = UUID.fromString("a0000000-0000-0000-0000-000000000003")

    @Test
    fun `spectating is not scoped to the organization's own custom scenarios -- a public-scenario session works too`() {
        val admin = createUser("gd3-admin")
        val owner = createUser("gd3-owner")
        val outsider = createUser("gd3-outsider")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, owner.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(owner.id!!)))

        val session = sessionRepository.save(
            Session(
                userId = owner.id!!,
                scenarioVersionId = couponScenarioVersionIdForSpectating,
                status = SessionStatus.IN_PROGRESS,
                currentPhase = "INCIDENT",
            )
        )

        mockMvc.perform(get("/sessions/${session.id}").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isOwner").value(false))
        mockMvc.perform(get("/sessions/${session.id}").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `game-day active session listing shows in-progress org sessions to members and excludes completed ones, 404s for non-members`() {
        val admin = createUser("gd2-admin")
        val owner = createUser("gd2-owner")
        val outsider = createUser("gd2-outsider")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, owner.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(owner.id!!)))
        val sessionId = startCustomScenarioSession(orgId, admin.id!!, owner.id!!)

        mockMvc.perform(get("/organizations/$orgId/game-day-sessions").header("Authorization", bearerHeader(admin.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.sessionId == '$sessionId')]").isNotEmpty)
            .andExpect(jsonPath("$[?(@.sessionId == '$sessionId')].ownerNickname").value("gd2-owner"))

        mockMvc.perform(get("/organizations/$orgId/game-day-sessions").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
    }
}
