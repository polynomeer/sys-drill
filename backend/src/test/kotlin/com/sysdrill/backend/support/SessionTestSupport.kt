package com.sysdrill.backend.support

import com.jayway.jsonpath.JsonPath
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Fixed id seeded by V2__seed_coupon_scenario.sql ("선착순 쿠폰"). */
val COUPON_SCENARIO_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000002")

/** Fixed id seeded by V11__seed_notification_scenario.sql ("알림 이벤트 처리"). */
val NOTIFICATION_SCENARIO_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000002")

/** Fixed id seeded by V12__seed_product_browsing_scenario.sql ("대규모 상품 조회"). */
val PRODUCT_BROWSING_SCENARIO_ID: UUID = UUID.fromString("d0000000-0000-0000-0000-000000000002")

/** Fixed id seeded by V19__seed_payment_scenario.sql ("주문/결제"). */
val PAYMENT_SCENARIO_ID: UUID = UUID.fromString("b1000000-0000-0000-0000-000000000002")

fun MockMvc.startSession(
    userId: UUID,
    scenarioId: UUID = COUPON_SCENARIO_ID,
    buildSubmissionId: UUID? = null,
    seed: String? = null,
): UUID {
    val body = buildString {
        append("""{"userId":"$userId","scenarioId":"$scenarioId"""")
        if (buildSubmissionId != null) append(""","buildSubmissionId":"$buildSubmissionId"""")
        if (seed != null) append(""","seed":"$seed"""")
        append("}")
    }
    val response = perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated)
        .andReturn().response.contentAsString
    return UUID.fromString(JsonPath.read(response, "$.id"))
}
