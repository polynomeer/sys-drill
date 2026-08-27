package com.sysdrill.backend.simulation.realinfra

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.util.UUID

/**
 * Calls [RealInfraSessionSweepWorker.sweepOnce] directly rather than waiting on
 * the real sweep-interval timer (default 30 minutes) — deterministic, not
 * timing-dependent. Real Postgres schema drop + real HikariDataSource evict.
 */
@SpringBootTest
class RealInfraSessionSweepWorkerTest(
    @Autowired val sweepWorker: RealInfraSessionSweepWorker,
    @Autowired val sessionTracker: RealInfraSessionTracker,
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
    @Autowired val dataSourceRegistry: SessionDataSourceRegistry,
    @Autowired val redisTemplate: StringRedisTemplate,
    @Autowired val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `sweeps a long-abandoned session but leaves an actively-touched one alone`() {
        val abandonedSessionId = UUID.randomUUID()
        val activeSessionId = UUID.randomUUID()

        val abandonedSchema = schemaProvisioner.provision(abandonedSessionId)
        dataSourceRegistry.poolFor(abandonedSessionId, abandonedSchema, 4)
        val oldTimestamp = (System.currentTimeMillis() - Duration.ofHours(7).toMillis()).toDouble()
        redisTemplate.opsForZSet().add(TRACKER_KEY, abandonedSessionId.toString(), oldTimestamp)

        val activeSchema = schemaProvisioner.provision(activeSessionId)
        dataSourceRegistry.poolFor(activeSessionId, activeSchema, 4)
        sessionTracker.touch(activeSessionId)

        sweepWorker.sweepOnce()

        assertThatThrownBy {
            jdbcTemplate.queryForObject("SELECT remaining FROM $abandonedSchema.coupon_inventory WHERE id = 1", Int::class.java)
        }.isInstanceOf(BadSqlGrammarException::class.java)

        val stillThere = jdbcTemplate.queryForObject("SELECT remaining FROM $activeSchema.coupon_inventory WHERE id = 1", Int::class.java)
        assertThat(stillThere).isEqualTo(1000)

        schemaProvisioner.drop(activeSessionId)
        dataSourceRegistry.evict(activeSessionId)
        sessionTracker.forget(activeSessionId)
    }

    private companion object {
        const val TRACKER_KEY = "sysdrill:simulation:realinfra:sessions"
    }
}
