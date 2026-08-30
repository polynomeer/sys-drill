package com.sysdrill.backend.simulation.realinfra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One dedicated [HikariDataSource] per real-infra coupon session, scoped to
 * that session's Postgres schema via Hikari's `schema` option (sets
 * `search_path` per connection — no query changes needed). Resizing "DB pool
 * size" (PLAN.md step 21) rebuilds the pool rather than mutating a live one —
 * infrequent/interactive, not a hot path — and always closes the pool it
 * replaces so the housekeeper thread doesn't leak.
 *
 * [jdbcUrl] defaults to the app's own direct connection, but PLAN.md step 23
 * has callers pass [ToxiproxySessionProxy.jdbcUrlFor] instead, so every query
 * this session's pool makes actually flows through that session's Toxiproxy
 * proxy (and its injected latency) rather than bypassing it.
 */
@Component
class SessionDataSourceRegistry(
    @Value("\${spring.datasource.url}") private val defaultJdbcUrl: String,
    @Value("\${spring.datasource.username}") private val username: String,
    @Value("\${spring.datasource.password}") private val password: String,
) {
    private val pools = ConcurrentHashMap<UUID, Pool>()

    private data class Pool(val dataSource: HikariDataSource, val maxPoolSize: Int, val jdbcUrl: String)

    @Synchronized
    fun poolFor(sessionId: UUID, schemaName: String, maxPoolSize: Int, jdbcUrl: String = defaultJdbcUrl): HikariDataSource {
        val existing = pools[sessionId]
        if (existing != null && existing.maxPoolSize == maxPoolSize && existing.jdbcUrl == jdbcUrl) return existing.dataSource

        existing?.dataSource?.close()

        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.schema = schemaName
        config.maximumPoolSize = maxPoolSize
        config.poolName = "realinfra-$sessionId"
        // Hikari's 30s default would let requests pile up quietly under real
        // contention; failing fast here makes pool exhaustion show up as a real,
        // prompt error k6 actually observes within its own request timeout.
        config.connectionTimeout = CONNECTION_TIMEOUT_MILLIS
        val dataSource = HikariDataSource(config)

        pools[sessionId] = Pool(dataSource, maxPoolSize, jdbcUrl)
        return dataSource
    }

    @Synchronized
    fun evict(sessionId: UUID) {
        pools.remove(sessionId)?.dataSource?.close()
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 3000L
    }
}
