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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
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
    @Autowired @Qualifier("transactionTemplate") val transactionTemplate: TransactionTemplate,
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

    private fun session(
        sessionId: UUID,
        traits: DesignTraits,
        loadRpsOverride: Int? = null,
        loadDurationOverride: Int? = null,
    ) = SimulationSessionState(
        sessionId = sessionId,
        domain = RuleBasedSimulationEngine.DOMAIN_COUPON,
        incidentActive = true,
        traits = traits,
        engineMode = EngineMode.REAL_INFRA,
        loadRpsOverride = loadRpsOverride,
        loadDurationOverride = loadDurationOverride,
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

    /**
     * Phase 3-B — a user-chosen loadRpsOverride well below the pilot's natural
     * pool+latency-bound throughput ceiling (~13 req/s per the calibration
     * comment on sysdrill.simulation.realinfra.incident-rps) should actually
     * bind the achieved traffic, unlike the default incident-rps(30) session
     * which is capacity-bound regardless of its higher target. A relative
     * comparison, same reasoning as the rate-limit test above — real k6
     * wall-clock timing rules out an exact-value assertion.
     */
    @Test
    fun `a low custom target RPS measurably reduces achieved traffic versus the default incident load`() {
        val defaultSessionId = UUID.randomUUID().also { provisionedSessions += it }
        val overriddenSessionId = UUID.randomUUID().also { provisionedSessions += it }

        val default = engine.computeState(session(defaultSessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE)))
        val overridden = engine.computeState(
            session(overriddenSessionId, DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE), loadRpsOverride = 3)
        )

        assertThat(overridden.trafficRps).isLessThan(default.trafficRps)
    }

    /**
     * Regression test: [com.sysdrill.backend.simulation.SimulationService]'s
     * `startIncident`/`applyAction` are `@Transactional` and call straight
     * into [RealInfraCouponEngine.computeState]/`applyAction` — this test
     * wraps the same call the same way, via [transactionTemplate], instead of
     * calling `engine.computeState` bare like every other test in this file.
     * Before [CouponSchemaProvisioner.provision] was made `REQUIRES_NEW`, its
     * DDL (issued through a plain `JdbcTemplate` on the app's primary
     * DataSource) joined this ambient transaction and stayed uncommitted for
     * as long as the transaction stayed open — which, here, is for the
     * entire synchronous k6 run inside `probeAndCache`. k6's requests go
     * through a completely separate, non-transactional per-session
     * [SessionDataSourceRegistry] pool, so they saw a schema with no table
     * and failed nearly every request (observed in production: 13/13 with
     * `relation "coupon_inventory" does not exist`). This reproduced with a
     * single sequential call — no concurrency needed.
     *
     * Uses a deliberately low `loadRpsOverride` (well under this pilot's
     * ~13 req/s natural pool+latency capacity ceiling, per the calibration
     * comment on `incident-rps`) so the dominant plausible source of errors
     * here is the schema-visibility bug itself — the default incident-rps(30)
     * legitimately saturates a 4-connection pool and produces a genuinely
     * high error rate on its own (that's the pilot's point), which would
     * mask this regression rather than isolate it.
     *
     * The threshold is 0.5, not near-zero: a brand new session's pool still
     * has to grow its first few physical connections through Toxiproxy's
     * added latency, and a handful of the earliest requests can genuinely
     * hit Hikari's 3s connectionTimeout while it does (observed: ~0.1-0.2
     * under a correct fix). The schema-visibility bug this guards against is
     * qualitatively different and unmistakably distinct — it fails nearly
     * every single request in the run (observed in production: 13/13), not
     * a handful during warm-up.
     */
    @Test
    fun `computeState does not fail nearly every request when called from inside an ambient transaction`() {
        val sessionId = UUID.randomUUID().also { provisionedSessions += it }

        val state = transactionTemplate.execute {
            engine.computeState(
                session(
                    sessionId,
                    DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE),
                    loadRpsOverride = 3,
                    loadDurationOverride = 3,
                )
            )
        }!!

        assertThat(state.errorRate).isLessThan(0.5)
    }
}
