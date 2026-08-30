package com.sysdrill.backend.simulation.realinfra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.system.measureTimeMillis

private val PROXY_MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}

/** Exercises the real Toxiproxy container (not mocked) — see PLAN.md step 23 / ADR-0015. */
@SpringBootTest
class ToxiproxySessionProxyTest(
    @Autowired val toxiproxy: ToxiproxySessionProxy,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.admin-url}") val adminUrl: String,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.latency-ms}") val configuredLatencyMs: Int,
) {
    private val adminClient = RestClient.create()

    @Test
    fun `provisioning creates a real proxy visible via the admin API, evict removes it`() {
        val sessionId = UUID.randomUUID()

        val session = toxiproxy.provision(sessionId)

        val proxies = adminClient.get().uri("$adminUrl/proxies").retrieve().body(PROXY_MAP_TYPE)
        assertThat(proxies).containsKey(session.proxyName)

        toxiproxy.evict(sessionId)

        val proxiesAfter = adminClient.get().uri("$adminUrl/proxies").retrieve().body(PROXY_MAP_TYPE)
        assertThat(proxiesAfter).doesNotContainKey(session.proxyName)
    }

    @Test
    fun `provision is idempotent — repeated calls return the same port`() {
        val sessionId = UUID.randomUUID()

        val first = toxiproxy.provision(sessionId)
        val second = toxiproxy.provision(sessionId)

        assertThat(second).isEqualTo(first)
        toxiproxy.evict(sessionId)
    }

    @Test
    fun `a query routed through the proxy is measurably slower than a direct connection`() {
        val sessionId = UUID.randomUUID()
        val schema = schemaProvisioner.provision(sessionId)

        val directPool = dataSourceRegistry.poolFor(sessionId, schema, 2)
        val directElapsedMs = measureTimeMillis {
            JdbcTemplate(directPool).queryForObject("SELECT 1", Int::class.java)
        }

        val toxiproxyUrl = toxiproxy.jdbcUrlFor(sessionId)
        val proxiedPool = dataSourceRegistry.poolFor(sessionId, schema, 2, toxiproxyUrl)
        val proxiedElapsedMs = measureTimeMillis {
            JdbcTemplate(proxiedPool).queryForObject("SELECT 1", Int::class.java)
        }

        // Non-strict margin (half the configured latency, no jitter budget needed since
        // jitter only adds on top) — avoids flakiness while still proving a real effect.
        assertThat(proxiedElapsedMs - directElapsedMs).isGreaterThan(configuredLatencyMs / 2L)

        toxiproxy.evict(sessionId)
        dataSourceRegistry.evict(sessionId)
        schemaProvisioner.drop(sessionId)
    }
}
