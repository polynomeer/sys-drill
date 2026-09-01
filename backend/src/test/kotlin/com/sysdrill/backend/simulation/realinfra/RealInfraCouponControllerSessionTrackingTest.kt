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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.UUID

private val PROXY_MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}

/**
 * Regression test for a real leak found in production: a session that only
 * ever hits [RealInfraCouponController] directly (a one-off manual curl
 * check, or k6 targeting this controller without going through
 * [RealInfraCouponEngine] first) used to provision a Toxiproxy proxy via
 * `toxiproxy.jdbcUrlFor` without ever registering with
 * [RealInfraSessionTracker] — so [RealInfraSessionSweepWorker] could never
 * find it, and the proxy (and its port) squatted forever. The controller now
 * touches the tracker itself on every request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealInfraCouponControllerSessionTrackingTest(
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val toxiproxy: ToxiproxySessionProxy,
    @Autowired val sessionTracker: RealInfraSessionTracker,
    @Autowired val sweepWorker: RealInfraSessionSweepWorker,
    @Autowired val stateStore: SimulationStateStore,
    @Autowired val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.admin-url}") val adminUrl: String,
    @LocalServerPort val port: Int,
) {
    private val adminClient = RestClient.create()
    private val provisionedSessions = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        provisionedSessions.forEach {
            dataSourceRegistry.evict(it)
            toxiproxy.evict(it)
            schemaProvisioner.drop(it)
            sessionTracker.forget(it)
        }
        provisionedSessions.clear()
    }

    @Test
    fun `hitting the controller directly registers the session so the sweep can find and evict its proxy`() {
        val sessionId = UUID.randomUUID().also { provisionedSessions += it }
        stateStore.save(
            sessionId,
            SimulationSessionState(
                sessionId = sessionId,
                domain = RuleBasedSimulationEngine.DOMAIN_COUPON,
                incidentActive = true,
                traits = DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE),
                engineMode = EngineMode.REAL_INFRA,
            )
        )
        schemaProvisioner.provision(sessionId)

        // No RealInfraCouponEngine / sessionTracker.touch anywhere in this test —
        // this call to the controller is the ONLY thing that should register the
        // session, mirroring a manual curl check or k6 pointed straight at it.
        val response = RestClient.create().post()
            .uri("http://localhost:$port/sessions/$sessionId/simulation/realinfra/coupon/claim")
            .retrieve()
            .toBodilessEntity()
        assertThat(response.statusCode.is2xxSuccessful).isTrue()

        val proxyName = "coupon_pg_${sessionId.toString().replace("-", "")}"
        val proxiesBeforeSweep = adminClient.get().uri("$adminUrl/proxies").retrieve().body(PROXY_MAP_TYPE)
        assertThat(proxiesBeforeSweep).containsKey(proxyName)

        assertThat(sessionTracker.findExpired(Duration.ZERO)).contains(sessionId)

        // Simulate abandonment the same way RealInfraSessionSweepWorkerTest does —
        // age the tracked timestamp past the idle timeout instead of waiting on it.
        val oldTimestamp = (System.currentTimeMillis() - Duration.ofHours(7).toMillis()).toDouble()
        redisTemplate.opsForZSet().add(TRACKER_KEY, sessionId.toString(), oldTimestamp)

        sweepWorker.sweepOnce()

        val proxiesAfterSweep = adminClient.get().uri("$adminUrl/proxies").retrieve().body(PROXY_MAP_TYPE)
        assertThat(proxiesAfterSweep).doesNotContainKey(proxyName)
    }

    private companion object {
        const val TRACKER_KEY = "sysdrill:simulation:realinfra:sessions"
    }
}
