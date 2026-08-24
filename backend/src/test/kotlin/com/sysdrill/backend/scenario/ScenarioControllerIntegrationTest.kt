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
}
