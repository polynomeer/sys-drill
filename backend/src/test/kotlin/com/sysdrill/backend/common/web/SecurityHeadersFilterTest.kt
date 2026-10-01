package com.sysdrill.backend.common.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** docs/COMMERCIALIZATION.md — confirms [SecurityHeadersFilter] actually runs, on a real (public, no auth needed) endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersFilterTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `every response carries the standard security headers`() {
        mockMvc.perform(get("/scenarios"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
    }
}
