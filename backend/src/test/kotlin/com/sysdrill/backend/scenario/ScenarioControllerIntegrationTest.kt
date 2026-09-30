package com.sysdrill.backend.scenario

import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioControllerIntegrationTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `lists the seeded coupon scenario`() {
        mockMvc.perform(get("/scenarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$COUPON_SCENARIO_ID')].domain").value("coupon"))
            .andExpect(jsonPath("$[?(@.id == '$COUPON_SCENARIO_ID')].title").value("선착순 쿠폰"))
    }

    @Test
    fun `returns scenario detail with parsed base requirements`() {
        mockMvc.perform(get("/scenarios/$COUPON_SCENARIO_ID"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domain").value("coupon"))
            .andExpect(jsonPath("$.baseRequirements.nonFunctional.totalCoupons").value(10000))
    }

    @Test
    fun `detail lists the published version's steps in order and only the initial prompt`() {
        mockMvc.perform(get("/scenarios/$COUPON_SCENARIO_ID"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.steps.length()").value(3))
            .andExpect(jsonPath("$.steps[0].order").value(1))
            .andExpect(jsonPath("$.steps[0].type").value("INITIAL"))
            .andExpect(jsonPath("$.steps[1].type").value("FOLLOWUP"))
            .andExpect(jsonPath("$.steps[2].type").value("INCIDENT"))
            .andExpect(jsonPath("$.initialPrompt").value(org.hamcrest.Matchers.containsString("선착순")))
            // FOLLOWUP/INCIDENT prompts must not leak into the overview — they're the session's mid-drill twist.
            .andExpect(jsonPath("$.steps[1].prompt").doesNotExist())
            .andExpect(jsonPath("$.followupPrompt").doesNotExist())
    }
}
