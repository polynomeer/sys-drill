package com.sysdrill.backend.simulation.realinfra

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Runtime DDL for the real-infra coupon pilot's per-session isolation
 * (PLAN.md step 21). Flyway migrations are static/versioned once; session
 * schemas are ephemeral and unbounded, so they're issued directly via
 * [JdbcTemplate] instead of a migration.
 */
@Component
class CouponSchemaProvisioner(private val jdbcTemplate: JdbcTemplate) {

    /** Schema name is derived from [UUID.toString], never attacker-controlled free text — the regex check below is defense-in-depth, not an injection fix. */
    fun schemaName(sessionId: UUID): String {
        val hex = sessionId.toString().replace("-", "")
        require(HEX_32.matches(hex)) { "Unexpected UUID shape: $sessionId" }
        return "realinfra_$hex"
    }

    /**
     * Idempotent — drops any previous run's schema first, so restarting an
     * incident for the same session is safe.
     *
     * `REQUIRES_NEW`: this is always called from inside
     * [com.sysdrill.backend.simulation.SimulationService]'s `@Transactional`
     * `startIncident`/`applyAction`, which shares the app's primary DataSource
     * (and thus this bean's plain [jdbcTemplate]) with JPA. Without a fresh
     * transaction here, this DDL would join that outer, still-open
     * transaction and stay uncommitted while [RealInfraCouponEngine]
     * synchronously runs k6 right after this returns — k6's requests go
     * through a completely separate, non-transactional per-session
     * [SessionDataSourceRegistry] pool, so they'd see none of it and fail
     * every request with "relation does not exist" (observed empirically).
     * `REQUIRES_NEW` commits this schema/table before returning, regardless
     * of the caller's own transaction outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun provision(sessionId: UUID): String {
        val schema = schemaName(sessionId)
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
        jdbcTemplate.execute("CREATE SCHEMA $schema")
        jdbcTemplate.execute("CREATE TABLE $schema.coupon_inventory (id INT PRIMARY KEY, remaining INT NOT NULL)")
        jdbcTemplate.execute("INSERT INTO $schema.coupon_inventory VALUES (1, $SEED_INVENTORY)")
        return schema
    }

    fun drop(sessionId: UUID) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS ${schemaName(sessionId)} CASCADE")
    }

    private companion object {
        val HEX_32 = Regex("^[0-9a-f]{32}$")
        const val SEED_INVENTORY = 1000
    }
}
