package com.sysdrill.backend.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** PLAN.md step 37 — a deployment that hasn't configured Google OAuth (the default, per application.yml) gets a clear error instead of a redirect to a broken Google URL with an empty client_id. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.auth.google.client-id="])
class GoogleAuthNotConfiguredTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `login is rejected when Google OAuth is not configured`() {
        mockMvc.perform(get("/auth/google/login")).andExpect(status().isBadRequest)
    }
}
