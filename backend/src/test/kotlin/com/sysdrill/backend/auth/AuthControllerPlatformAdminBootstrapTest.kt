package com.sysdrill.backend.auth

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.UserRepository
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * PLAN.md step 35 — signing up with an allowlisted email is the only way to
 * become PLATFORM_ADMIN (no promote/demote API). Separate class: the
 * allowlist is fixed via property, distinct Spring context from the rest of
 * AuthControllerIntegrationTest. Emails are randomized per test run (not
 * fixed literals) since this hits a real, not-reset-between-runs Postgres —
 * a fixed email would 409 on a second local run, same reason
 * AuthControllerIntegrationTest's uniqueEmail() exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerPlatformAdminBootstrapTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    companion object {
        private val allowlistedEmail = "bootstrap-admin-${UUID.randomUUID()}@example.com"
        private val allowlistedEmailMixedCase = "Other-Admin-${UUID.randomUUID()}@Example.com"

        @DynamicPropertySource
        @JvmStatic
        fun platformAdminEmails(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.auth.platform-admin-emails") { "$allowlistedEmail, $allowlistedEmailMixedCase " }
        }
    }

    @Test
    fun `signing up with an allowlisted email becomes a platform admin`() {
        val response = mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$allowlistedEmail","password":"password123","nickname":"bootstrap"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val userId = UUID.fromString(JsonPath.read(response, "$.user.id"))

        assertThat(userRepository.findById(userId).orElseThrow().platformRole).isEqualTo(PlatformRole.PLATFORM_ADMIN)
    }

    @Test
    fun `the allowlist match is case-insensitive`() {
        val response = mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${allowlistedEmailMixedCase.lowercase()}","password":"password123","nickname":"bootstrap2"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val userId = UUID.fromString(JsonPath.read(response, "$.user.id"))

        assertThat(userRepository.findById(userId).orElseThrow().platformRole).isEqualTo(PlatformRole.PLATFORM_ADMIN)
    }

    @Test
    fun `signing up with a non-allowlisted email stays a regular user`() {
        val response = mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nobody-${UUID.randomUUID()}@example.com","password":"password123","nickname":"regular"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val userId = UUID.fromString(JsonPath.read(response, "$.user.id"))

        assertThat(userRepository.findById(userId).orElseThrow().platformRole).isEqualTo(PlatformRole.USER)
    }
}
