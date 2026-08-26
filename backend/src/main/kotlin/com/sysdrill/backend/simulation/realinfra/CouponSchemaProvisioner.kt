package com.sysdrill.backend.simulation.realinfra

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
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

    /** Idempotent — drops any previous run's schema first, so restarting an incident for the same session is safe. */
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
