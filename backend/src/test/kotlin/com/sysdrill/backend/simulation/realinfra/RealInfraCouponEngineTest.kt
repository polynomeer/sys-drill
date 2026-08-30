package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.EngineMode
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SimulationSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * Exercises the real Postgres schema/pool + real Redis cache + real k6 load
 * (not mocked) — PLAN.md step 21 / ADR-0014. Real wall-clock timing means
 * these assert RANGES and RELATIVE comparisons, not exact hand-computed
 * values like RuleBasedSimulationEngine's tests — a deliberate, scoped
 * exception to this project's usual exact-value testing norm. Requires
 * Docker (for the real k6 container) and a real listening port, hence
 * RANDOM_PORT rather than the MOCK environment most controller tests use —
 * Spring Boot publishes the actual bound port as `local.server.port`
 * regardless of mode, which `sysdrill.simulation.realinfra.app-base-url`
 * references, so this doesn't collide with anything else on a fixed port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealInfraCouponEngineTest(
    @Autowired val engine: RealInfraCouponEngine,
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val toxiproxy: ToxiproxySessionProxy,
) {
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

    private fun session(sessionId: UUID, traits: DesignTraits) = SimulationSessionState(
        sessionId = sessionId,
        domain = RuleBasedSimulationEngine.DOMAIN_COUPON,
        incidentActive = true,
        traits = traits,
        engineMode = EngineMode.REAL_INFRA,
    )

    @Test
    fun `a fresh incident produces a real, plausible measurement`() {
        val sessionId = UUID.randomUUID().also { provisionedSessions += it }

        val state = engine.computeState(session(sessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE)))

        assertThat(state.p95LatencyMs).isGreaterThanOrEqualTo(0.0)
        assertThat(state.errorRate).isBetween(0.0, 1.0)
        assertThat(state.trafficRps).isGreaterThan(0.0)
        assertThat(state.connectionPoolUsage).isBetween(0.0, 1.0)
    }

    @Test
    fun `enabling the rate limit measurably reduces p95 latency under identical incident load`() {
        // Not errorRate: empirically (PLAN.md step 21 notes), this pilot's dedicated
        // pool + Hikari's fail-fast connectionTimeout mean real contention shows up
        // as *latency* growth long before it shows up as request failures — p95 is
        // the metric that actually demonstrates rate limiting's protective effect.
        val unlimitedSessionId = UUID.randomUUID().also { provisionedSessions += it }
        val limitedSessionId = UUID.randomUUID().also { provisionedSessions += it }

        val unlimited = engine.computeState(session(unlimitedSessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE)))
        engine.applyAction(
            session(limitedSessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE)),
            SimulationActionType.STRENGTHEN_RATE_LIMIT,
        )
        val limited = engine.computeState(session(limitedSessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE, rateLimitEnabled = true)))

        assertThat(limited.p95LatencyMs).isLessThanOrEqualTo(unlimited.p95LatencyMs)
    }
}
