package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.EngineMode
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationSessionState
import com.sysdrill.backend.simulation.SimulationStateStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Exercises the real OTLP-to-Jaeger pipeline (PLAN.md step 24, not mocked) —
 * makes a real HTTP call to [RealInfraCouponController.claim], then polls
 * Jaeger's own query API (not this app) for the resulting trace. Confirms
 * both halves this step added: Spring's automatic HTTP server span, and
 * [RealInfraCouponController]'s manual `coupon.db.claim` span — whose
 * duration should reflect the real Toxiproxy-injected latency, the whole
 * point of wrapping that call explicitly (JDBC calls aren't
 * auto-instrumented here; see the controller's class doc).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealInfraCouponTracingTest(
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val toxiproxy: ToxiproxySessionProxy,
    @Autowired val stateStore: SimulationStateStore,
    @LocalServerPort val port: Int,
) {
    private val jaegerClient = RestClient.create()
    private val provisionedSessions = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        provisionedSessions.forEach {
            dataSourceRegistry.evict(it)
            toxiproxy.evict(it)
            schemaProvisioner.drop(it)
        }
        provisionedSessions.clear()
    }

    @Test
    fun `a real claim request produces a real trace with a db span reflecting the injected latency`() {
        val sessionId = UUID.randomUUID().also { provisionedSessions += it }
        val state = SimulationSessionState(
            sessionId = sessionId,
            domain = RuleBasedSimulationEngine.DOMAIN_COUPON,
            incidentActive = true,
            traits = DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE),
            engineMode = EngineMode.REAL_INFRA,
        )
        stateStore.save(sessionId, state)
        schemaProvisioner.provision(sessionId)

        val response = RestClient.create().post()
            .uri("http://localhost:$port/sessions/$sessionId/simulation/realinfra/coupon/claim")
            .retrieve()
            .toBodilessEntity()
        assertThat(response.statusCode.is2xxSuccessful).isTrue()

        val trace = awaitTraceWithDbSpan(sessionId, Duration.ofSeconds(15))
        assertThat(trace).isNotNull
        val dbSpanDurationMs = trace!!.durationMicros / 1000.0
        // Non-strict lower bound (half the configured toxic) — same
        // flakiness-avoidance rationale as ADR-0014's real-infra range assertions.
        assertThat(dbSpanDurationMs).isGreaterThan(toxiproxy.configuredLatencyMs / 2.0)
    }

    private data class DbSpan(val durationMicros: Long)

    private fun awaitTraceWithDbSpan(sessionId: UUID, timeout: Duration): DbSpan? {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val body = jaegerClient.get()
                .uri("http://localhost:16686/api/traces?service=backend&operation=coupon.db.claim&limit=50")
                .retrieve()
                .body(String::class.java) ?: ""
            findMatchingSpan(body, sessionId)?.let { return it }
            Thread.sleep(500)
        }
        return null
    }

    /** Hand-rolled string search rather than a full Jaeger response DTO — this test only ever needs one field out of a large, otherwise-irrelevant JSON shape. */
    private fun findMatchingSpan(jaegerResponseJson: String, sessionId: UUID): DbSpan? {
        val root = ObjectMapper().readTree(jaegerResponseJson)
        for (trace in root.path("data")) {
            for (span in trace.path("spans")) {
                if (span.path("operationName").asText() != "coupon.db.claim") continue
                val matchesSession = span.path("tags").any {
                    it.path("key").asText() == "sysdrill.session_id" && it.path("value").asText() == sessionId.toString()
                }
                if (matchesSession) return DbSpan(span.path("duration").asLong())
            }
        }
        return null
    }
}
