package com.sysdrill.backend.simulation.realinfra

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.BadSqlGrammarException
import java.util.UUID

/** Exercises real DDL against the real Postgres container (not mocked) — see PLAN.md step 21 notes. */
@SpringBootTest
class CouponSchemaProvisionerTest(
    @Autowired val provisioner: CouponSchemaProvisioner,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `provisions a schema with a seeded coupon_inventory table`() {
        val sessionId = UUID.randomUUID()

        val schema = provisioner.provision(sessionId)

        val remaining = jdbcTemplate.queryForObject("SELECT remaining FROM $schema.coupon_inventory WHERE id = 1", Int::class.java)
        assertThat(remaining).isEqualTo(1000)

        provisioner.drop(sessionId)
    }

    @Test
    fun `provisioning twice is idempotent — the second call resets the schema`() {
        val sessionId = UUID.randomUUID()
        val schema = provisioner.provision(sessionId)
        jdbcTemplate.update("UPDATE $schema.coupon_inventory SET remaining = 1 WHERE id = 1")

        provisioner.provision(sessionId)

        val remaining = jdbcTemplate.queryForObject("SELECT remaining FROM $schema.coupon_inventory WHERE id = 1", Int::class.java)
        assertThat(remaining).isEqualTo(1000)

        provisioner.drop(sessionId)
    }

    @Test
    fun `drop removes the schema entirely`() {
        val sessionId = UUID.randomUUID()
        val schema = provisioner.provision(sessionId)

        provisioner.drop(sessionId)

        assertThatThrownBy { jdbcTemplate.queryForObject("SELECT remaining FROM $schema.coupon_inventory WHERE id = 1", Int::class.java) }
            .isInstanceOf(BadSqlGrammarException::class.java)
    }
}
