package com.sysdrill.backend.simulation.realinfra

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ToxiproxySession(val proxyName: String, val port: Int)

/**
 * A per-session Toxiproxy proxy in front of the real Postgres upstream
 * (PLAN.md step 23, ADR-0015) — lets the coupon incident inject a genuine,
 * controllable network fault (added latency) rather than relying purely on
 * k6 load volume to make contention visible. Adding a real latency floor to
 * every query is what forced step 21's incident-rps down from ~3000 to ~30
 * (application.yml notes) — once each query takes 300ms+, a 4-connection
 * pool caps out around 13 req/s and the old value just meant every request
 * hit k6's own client timeout. This adds a second, independent, always-on
 * failure mode none of the coupon domain's three actions can fix (ADR-0015)
 * — the fault is at the network layer, not the application layer those
 * actions tune.
 *
 * Ports are pre-published in docker-compose's fixed range and handed out
 * from an in-memory pool per JVM instance (same single-instance assumption
 * as everywhere else in this pilot — see [RealInfraCouponEngine]'s
 * per-session lock).
 */
@Component
class ToxiproxySessionProxy(
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.admin-url}") adminUrl: String,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.postgres-upstream}") private val postgresUpstream: String,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.db-name}") private val dbName: String,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.port-range-start}") private val portRangeStart: Int,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.port-range-size}") private val portRangeSize: Int,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.latency-ms}") val configuredLatencyMs: Int,
    @Value("\${sysdrill.simulation.realinfra.toxiproxy.jitter-ms}") private val jitterMs: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = RestClient.builder().baseUrl(adminUrl).build()
    private val sessions = ConcurrentHashMap<UUID, ToxiproxySession>()
    private val usedPorts = ConcurrentHashMap.newKeySet<Int>()

    private fun proxyName(sessionId: UUID) = "coupon_pg_${sessionId.toString().replace("-", "")}"

    /** Idempotent — returns the existing session's proxy if already provisioned, without re-creating it or re-adding the toxic. */
    @Synchronized
    fun provision(sessionId: UUID): ToxiproxySession {
        sessions[sessionId]?.let { return it }

        val port = allocatePort()
        val name = proxyName(sessionId)
        try {
            restClient.post().uri("/proxies")
                .body(mapOf("name" to name, "listen" to "0.0.0.0:$port", "upstream" to postgresUpstream))
                .retrieve().toBodilessEntity()
            restClient.post().uri("/proxies/$name/toxics")
                .body(
                    mapOf(
                        "name" to "latency_down",
                        "type" to "latency",
                        "stream" to "downstream",
                        "attributes" to mapOf("latency" to configuredLatencyMs, "jitter" to jitterMs),
                    )
                )
                .retrieve().toBodilessEntity()
        } catch (ex: Exception) {
            // Release the port back to the pool on any failure (e.g. a stale proxy
            // from a prior run/crash still bound to it) — otherwise a failed
            // provision leaks that port for the rest of this JVM's lifetime.
            usedPorts.remove(port)
            throw ex
        }

        val session = ToxiproxySession(name, port)
        sessions[sessionId] = session
        return session
    }

    /** The JDBC URL a session's dedicated pool should use to route through this proxy — provisions first if not already done. */
    fun jdbcUrlFor(sessionId: UUID): String {
        val session = provision(sessionId)
        return "jdbc:postgresql://localhost:${session.port}/$dbName"
    }

    @Synchronized
    fun evict(sessionId: UUID) {
        val session = sessions.remove(sessionId) ?: return
        usedPorts.remove(session.port)
        runCatching {
            restClient.delete().uri("/proxies/${session.proxyName}").retrieve().toBodilessEntity()
        }.onFailure { log.warn("Failed to delete toxiproxy proxy {}: {}", session.proxyName, it.message) }
    }

    private fun allocatePort(): Int {
        for (offset in 0 until portRangeSize) {
            val candidate = portRangeStart + offset
            if (usedPorts.add(candidate)) return candidate
        }
        error("No free Toxiproxy ports available (range size $portRangeSize exhausted)")
    }
}
