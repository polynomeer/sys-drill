package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.EngineMode
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationSessionState
import com.sysdrill.backend.simulation.SimulationStateStore
import com.sysdrill.backend.simulation.TraceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round E25 follow-up — the Traces tab's real-infra path against the real pipeline (not mocked):
 * a real claim request → Spring/OTLP → Jaeger, then [TraceService] finds it by the session tag and turns
 * it into the waterfall the UI draws, with the Toxiproxy-delayed DB span inside it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealInfraTraceServiceTest(
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val toxiproxy: ToxiproxySessionProxy,
    @Autowired val stateStore: SimulationStateStore,
    @Autowired val traceService: TraceService,
    @LocalServerPort val port: Int,
) {
    private val provisioned = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        provisioned.forEach {
            dataSourceRegistry.evict(it)
            toxiproxy.evict(it)
            schemaProvisioner.drop(it)
        }
    }

    @Test
    fun `a real claim shows up as a Jaeger trace with its delayed db span`() {
        val sessionId = UUID.randomUUID().also { provisioned += it }
        stateStore.save(
            sessionId,
            SimulationSessionState(
                sessionId = sessionId,
                domain = RuleBasedSimulationEngine.DOMAIN_COUPON,
                incidentActive = true,
                traits = DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE),
                engineMode = EngineMode.REAL_INFRA,
            ),
        )
        schemaProvisioner.provision(sessionId)
        RestClient.create().post().uri("http://localhost:$port/sessions/$sessionId/simulation/realinfra/coupon/claim").retrieve().toBodilessEntity()

        val deadline = Instant.now().plus(Duration.ofSeconds(20))
        var list = traceService.jaegerList(sessionId)
        while (list.traces.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(500)
            list = traceService.jaegerList(sessionId)
        }
        assertThat(list.source).isEqualTo(TraceService.JAEGER)
        assertThat(list.available).isTrue()
        assertThat(list.traces).isNotEmpty()

        val view = traceService.jaegerTrace(list.traces.first().traceId)
        val db = view.spans.single { it.name == "coupon.db.claim" }
        assertThat(db.parentSpanId).isNotNull() // under the HTTP server span
        assertThat(db.durationMs.toDouble()).isGreaterThan(toxiproxy.configuredLatencyMs / 2.0)
        assertThat(view.durationMs).isGreaterThanOrEqualTo(db.durationMs)
        assertThat(view.spans.map { it.service }.toSet()).containsExactly("backend")
    }
}
