package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * docs/COMMERCIALIZATION.md — `POST /auth/logout` previously didn't exist;
 * "logging out" only ever cleared the frontend's localStorage, so a leaked
 * token stayed valid for the full 30-day TTL. Drives the real endpoint ->
 * `TokenRevocationService` -> `AuthInterceptor` path end to end rather than
 * unit-testing the service in isolation, since the whole point is that an
 * old token actually gets rejected by real request handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenRevocationIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    /** Any authenticated, path-parameter-only endpoint works as a probe -- 404 means auth passed, 401 means it didn't. */
    private fun probe(token: String) =
        mockMvc.perform(get("/build-submissions/${UUID.randomUUID()}").header("Authorization", token))

    @Test
    fun `logging out revokes the old token but not a freshly issued one`() {
        val userId = userRepository.save(
            User(email = "logout-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
        val oldToken = bearerHeader(userId)

        probe(oldToken).andExpect(status().isNotFound)

        mockMvc.perform(post("/auth/logout").header("Authorization", oldToken)).andExpect(status().isNoContent)

        probe(oldToken).andExpect(status().isUnauthorized)

        // JWT `iat` is second-granularity -- cross a full second so the next
        // token's issuedAt can't tie with the revocation timestamp just set.
        Thread.sleep(1100)

        val newToken = bearerHeader(userId)
        probe(newToken).andExpect(status().isNotFound)
    }

    @Test
    fun `logging out one user does not revoke another user's token`() {
        val userId = userRepository.save(
            User(email = "logout-a-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "a")
        ).id!!
        val otherUserId = userRepository.save(
            User(email = "logout-b-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "b")
        ).id!!
        val otherToken = bearerHeader(otherUserId)

        mockMvc.perform(post("/auth/logout").header("Authorization", bearerHeader(userId))).andExpect(status().isNoContent)

        probe(otherToken).andExpect(status().isNotFound)
    }
}
