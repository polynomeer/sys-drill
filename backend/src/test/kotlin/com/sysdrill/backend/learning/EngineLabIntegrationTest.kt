package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md Round E11 (L5, ADR-0047) — engine labs call the same rule-based formula
 * as the Drill incident, with no session. The coupon numbers below are the ones
 * SimulationEngineTest already pins for the incident (6000 rps, Redis ×15 slower):
 * default TTL 10s → read util 0.8547, write util 1.8; TTL 600 → hit 0.95 → read util
 * 0.07; pool 500 → write capacity ×10 → write util 0.18, read util unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EngineLabIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private val user: UUID by lazy {
        userRepository.save(User(email = "engine-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "engine")).id!!
    }
    private val delta = Offset.offset(0.001)

    private fun run(slug: String, body: String) = mockMvc.perform(
        post("/learning/labs/$slug/engine/run")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user))
            .content(body)
    )

    private fun runOk(slug: String, body: String): String = run(slug, body).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `every official domain has a lab, each with the incident's defaults on its knobs`() {
        val list = mockMvc.perform(get("/learning/labs").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(list, "$[?(@.kind == 'ENGINE')].domain")).containsExactlyInAnyOrder(
            "coupon", "notification", "product-browsing", "payment", "reservation", "batch-settlement", "autoscaling", "deployment",
        )
        val view = mockMvc.perform(get("/learning/labs/engine-coupon-bottleneck/engine").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Any>>(view, "$.knobs[*].default")).containsExactly(10, 50, false)
    }

    @Test
    fun `the lab reproduces the incident and shows which bottleneck each knob actually moves`() {
        val base = runOk("engine-coupon-bottleneck", "{}")
        assertThat(JsonPath.read<Double>(base, "$.dbReadLoad")).isCloseTo(0.8547, delta)
        assertThat(JsonPath.read<Double>(base, "$.dbWriteLoad")).isCloseTo(1.8, delta)

        val longTtl = runOk("engine-coupon-bottleneck", """{"traits":{"cacheTtlSeconds":600}}""")
        assertThat(JsonPath.read<Double>(longTtl, "$.dbReadLoad")).isCloseTo(0.07, delta)

        val bigPool = runOk("engine-coupon-bottleneck", """{"traits":{"dbPoolSize":500}}""")
        assertThat(JsonPath.read<Double>(bigPool, "$.dbWriteLoad")).isCloseTo(0.18, delta)
        assertThat(JsonPath.read<Double>(bigPool, "$.dbReadLoad")).isCloseTo(0.8547, delta) // the read bottleneck stays

        val calm = runOk("engine-coupon-bottleneck", """{"incidentActive":false}""")
        assertThat(JsonPath.read<Double>(calm, "$.trafficRps")).isEqualTo(300.0)
    }

    @Test
    fun `only the lab's own knobs, within range and of the right type`() {
        run("engine-coupon-bottleneck", """{"traits":{"consumerCount":10}}""").andExpect(status().isBadRequest)
        run("engine-coupon-bottleneck", """{"traits":{"cacheTtlSeconds":9999}}""").andExpect(status().isBadRequest)
        run("engine-coupon-bottleneck", """{"traits":{"rateLimitEnabled":"yes"}}""").andExpect(status().isBadRequest)
        run("capacity-feed", "{}").andExpect(status().isNotFound) // not an engine lab
    }

    @Test
    fun `the canary lab doubles failures with each step and rollback returns to baseline`() {
        fun errorRate(body: String) = JsonPath.read<Double>(runOk("engine-canary-rollout", body), "$.errorRate")
        val start = errorRate("""{"traits":{"canaryStartPercent":10,"rolloutPromotions":0},"incidentActive":true}""")
        val twoSteps = errorRate("""{"traits":{"canaryStartPercent":10,"rolloutPromotions":2},"incidentActive":true}""")
        assertThat(start).isCloseTo(0.001 + 0.10 * 0.6, delta)
        assertThat(twoSteps).isCloseTo(0.001 + 0.40 * 0.6, delta)
        assertThat(errorRate("""{"traits":{"rolloutPromotions":2,"rolledBack":true},"incidentActive":true}""")).isCloseTo(0.001, delta)
        run("engine-canary-rollout", """{"traits":{"autoRollbackErrorPct":5},"incidentActive":true}""").andExpect(status().isBadRequest)
    }
}
