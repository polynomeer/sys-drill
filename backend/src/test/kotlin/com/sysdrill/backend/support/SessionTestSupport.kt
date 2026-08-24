package com.sysdrill.backend.support

import com.jayway.jsonpath.JsonPath
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Fixed id seeded by V2__seed_coupon_scenario.sql ("선착순 쿠폰"). */
val COUPON_SCENARIO_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000002")

fun MockMvc.startSession(userId: UUID, scenarioId: UUID = COUPON_SCENARIO_ID): UUID {
    val body = """{"userId":"$userId","scenarioId":"$scenarioId"}"""
    val response = perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated)
        .andReturn().response.contentAsString
    return UUID.fromString(JsonPath.read(response, "$.id"))
}
