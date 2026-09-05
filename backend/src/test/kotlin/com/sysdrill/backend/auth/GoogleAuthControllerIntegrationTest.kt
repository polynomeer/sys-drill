package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.FakeGoogleOAuthClient
import com.sysdrill.backend.support.FakeGoogleOAuthConfig
import java.net.URI
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder

/** PLAN.md step 37 — "Google로 계속하기". Uses [FakeGoogleOAuthClient] (Spring DI substitution, not a mock) since real Google can't be exercised in a test. client-id is set to a dummy non-blank value — it's never sent to real Google (exchangeCodeForAccessToken/fetchUserInfo are faked), just needed to pass buildAuthorizationUrl()'s not-configured guard (see GoogleAuthNotConfiguredTest for the blank-config case). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeGoogleOAuthConfig::class)
@TestPropertySource(properties = ["sysdrill.auth.google.client-id=test-client-id"])
class GoogleAuthControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val googleOAuthClient: GoogleOAuthClient,
) {
    private val fake get() = googleOAuthClient as FakeGoogleOAuthClient

    private fun startLoginAndGetState(): String {
        val response = mockMvc.perform(get("/auth/google/login")).andExpect(status().isFound).andReturn().response
        val location = URI.create(response.getHeader("Location")!!)
        assertThat(location.toString()).startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        return UriComponentsBuilder.fromUri(location).build().queryParams.getFirst("state")!!
    }

    @Test
    fun `a new verified Google email creates an account and redirects with a token`() {
        val email = "new-google-user-${UUID.randomUUID()}@example.com"
        fake.nextUserInfo = GoogleUserInfo(email = email, emailVerified = true, name = "New Googler")
        val state = startLoginAndGetState()

        val response = mockMvc.perform(get("/auth/google/callback").param("code", "auth-code").param("state", state))
            .andExpect(status().isFound)
            .andReturn().response
        val location = response.getHeader("Location")!!
        assertThat(location).startsWith("http://localhost:3000/auth/google/complete#")
        assertThat(location).contains("token=")
        assertThat(location).contains("nickname=New+Googler")

        val user = userRepository.findByEmail(email)
        assertThat(user).isNotNull
        assertThat(user!!.nickname).isEqualTo("New Googler")
        // A real BCrypt hash of an unknown random value, not the email or anything predictable.
        assertThat(user.passwordHash).matches("^\\$2[aby]\\$.*")
        assertThat(user.passwordHash).isNotEqualTo(email)
    }

    @Test
    fun `an existing email logs into the same account instead of creating a duplicate`() {
        val email = "existing-google-user-${UUID.randomUUID()}@example.com"
        fake.nextUserInfo = GoogleUserInfo(email = email, emailVerified = true, name = "Existing User")

        val firstState = startLoginAndGetState()
        mockMvc.perform(get("/auth/google/callback").param("code", "c1").param("state", firstState)).andExpect(status().isFound)
        val firstUserId = userRepository.findByEmail(email)!!.id

        val secondState = startLoginAndGetState()
        mockMvc.perform(get("/auth/google/callback").param("code", "c2").param("state", secondState)).andExpect(status().isFound)

        assertThat(userRepository.findAllById(listOf(firstUserId!!))).hasSize(1)
        assertThat(userRepository.findByEmail(email)!!.id).isEqualTo(firstUserId)
    }

    @Test
    fun `reusing a state is rejected as unauthorized`() {
        fake.nextUserInfo = GoogleUserInfo(email = "reuse-${UUID.randomUUID()}@example.com", emailVerified = true, name = "X")
        val state = startLoginAndGetState()

        mockMvc.perform(get("/auth/google/callback").param("code", "c1").param("state", state)).andExpect(status().isFound)
        mockMvc.perform(get("/auth/google/callback").param("code", "c2").param("state", state)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an unknown state is rejected as unauthorized`() {
        mockMvc.perform(get("/auth/google/callback").param("code", "c1").param("state", "not-a-real-state"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `an unverified Google email is rejected and no account is created`() {
        val email = "unverified-${UUID.randomUUID()}@example.com"
        fake.nextUserInfo = GoogleUserInfo(email = email, emailVerified = false, name = "Unverified")
        val state = startLoginAndGetState()

        mockMvc.perform(get("/auth/google/callback").param("code", "c1").param("state", state))
            .andExpect(status().isUnauthorized)

        assertThat(userRepository.findByEmail(email)).isNull()
    }
}
