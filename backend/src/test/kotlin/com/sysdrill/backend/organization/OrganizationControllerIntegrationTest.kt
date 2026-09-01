package com.sysdrill.backend.organization

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import java.time.Instant
import java.util.UUID
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

/** PLAN.md step 32 — organization creation, email-bound invitations, and ADMIN/MEMBER membership. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val invitationRepository: OrganizationInvitationRepository,
) {

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
}
