package com.sysdrill.backend.auth

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md step 30 — real signup/login, replacing UserControllerIntegrationTest's
 * old POST /users coverage. Exercises real BCrypt hashing (not mocked) and
 * the real JwtService bean, matching this project's established real-infra
 * test discipline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val jwtService: JwtService,
) {

    private fun uniqueEmail() = "auth-${UUID.randomUUID()}@example.com"

    @Test
    fun `signing up stores a real BCrypt hash, not the plaintext password, and issues a valid token`() {
        val email = uniqueEmail()
        val response = mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"correct horse battery","nickname":"drill-user"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.nickname").value("drill-user"))
            .andExpect(jsonPath("$.token").exists())
            .andReturn().response.contentAsString

        val userId = UUID.fromString(JsonPath.read(response, "$.user.id"))
        val storedHash = userRepository.findById(userId).orElseThrow().passwordHash
        assertThat(storedHash).isNotEqualTo("correct horse battery")
        assertThat(BCryptPasswordEncoder().matches("correct horse battery", storedHash)).isTrue()

        val token = JsonPath.read<String>(response, "$.token")
        assertThat(jwtService.verify(token)).isEqualTo(userId)
    }

    @Test
    fun `signing up with an already-registered email is rejected as a conflict`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123","nickname":"first"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"different123","nickname":"second"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `logging in with the correct password succeeds and the wrong password is rejected`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"correct-password","nickname":"drill-user"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"correct-password"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"wrong-password"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `logging in with an unknown email is rejected the same way as a wrong password`() {
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail()}","password":"whatever"}""")
        ).andExpect(status().isUnauthorized)
    }
}
