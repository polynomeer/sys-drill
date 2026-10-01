package com.sysdrill.backend.common.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * docs/COMMERCIALIZATION.md — a previous attempt at backend Sentry
 * integration broke application startup entirely (wrong artifact for this
 * project's Spring Boot version). Separate `@SpringBootTest` context from
 * [GlobalExceptionHandlerTest] (its own [DynamicPropertySource]) so this is
 * the one place that actually boots with sentry.dsn set, proving that
 * regression doesn't reappear -- and that [GlobalExceptionHandler]'s
 * explicit `Sentry.captureException` call doesn't alter the response shape
 * or block the request thread (the DSN host is an unopened local port, so
 * any accidental synchronous delivery attempt would show up as a hang).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerSentryTest(@Autowired val mockMvc: MockMvc) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun sentryDsn(registry: DynamicPropertyRegistry) {
            registry.add("sentry.dsn") { "http://examplePublicKey@localhost:9/1" }
        }
    }

    @TestConfiguration
    class ThrowingEndpointConfig {
        @RestController
        class ThrowingController {
            @GetMapping("/__test/boom")
            fun boom(): Nothing = throw RuntimeException("test-only unexpected failure")
        }
    }

    @Test
    fun `an unexpected exception still returns the structured 500 when a real Sentry DSN is configured`() {
        mockMvc.perform(get("/__test/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
    }
}
