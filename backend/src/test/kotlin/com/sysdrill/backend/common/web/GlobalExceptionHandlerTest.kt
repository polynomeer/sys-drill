package com.sysdrill.backend.common.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * docs/COMMERCIALIZATION.md — a malformed request body used to fall through
 * to Spring Boot's default /error handler (still a 400, but not this app's
 * ApiError shape). This confirms GlobalExceptionHandler now shapes it
 * consistently with every other error response, via the overridden
 * `handleExceptionInternal` -- and, just as importantly, that it's still a
 * 400 and not the 500 a naive `@ExceptionHandler(Exception::class)` alone
 * would produce (this exact regression was caught mid-round by
 * PasswordResetAndVerificationIntegrationTest's `@Valid` failure case going
 * from 400 to 500). The genuinely-unexpected catch-all
 * (@ExceptionHandler(Exception::class)) isn't separately integration-tested
 * here -- it's four lines with no branching, the same coverage depth this
 * file's other six handlers already have (none of them have a dedicated
 * test either; AuthControllerIntegrationTest exercises them indirectly
 * through real 401/409 responses).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `a malformed JSON body is rejected as a structured 400, not the framework default error page`() {
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not valid json")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").exists())
    }
}
