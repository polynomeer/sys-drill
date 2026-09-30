package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * docs/COMMERCIALIZATION.md — reproduces the real bug (a token whose user
 * no longer exists in the DB reached a service layer and blew up as an
 * opaque 500 via a FK-constraint violation) at the [AuthInterceptor] layer
 * instead, asserting it now fails fast as a 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthInterceptorUserExistenceTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    @Test
    fun `a token for a user that does not exist in the DB is rejected with 401, not a downstream 500`() {
        val tokenForNobody = bearerHeader(UUID.randomUUID())

        mockMvc.perform(
            get("/build-submissions/${UUID.randomUUID()}").header("Authorization", tokenForNobody)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token whose user existed at issue time but was deleted since is rejected with 401`() {
        val user = userRepository.save(User(email = "exists-then-deleted-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "ghost-user"))
        val token = bearerHeader(user.id!!)
        userRepository.deleteById(user.id!!)

        mockMvc.perform(
            get("/build-submissions/${UUID.randomUUID()}").header("Authorization", token)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token for a user that does exist passes auth and reaches the controller`() {
        val user = userRepository.save(User(email = "still-here-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "real-user"))
        val token = bearerHeader(user.id!!)

        // Auth passes -> reaches BuildController.get -> 404 (no such submission),
        // not 401. Proves the existence check doesn't false-positive-reject a
        // real user.
        mockMvc.perform(
            get("/build-submissions/${UUID.randomUUID()}").header("Authorization", token)
        ).andExpect(status().isNotFound)
    }
}
