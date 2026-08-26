package com.sysdrill.backend.simulation.realinfra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/** Real HikariDataSource instances against the real Postgres container — see PLAN.md step 21 notes. */
@SpringBootTest
class SessionDataSourceRegistryTest(
    @Autowired val registry: SessionDataSourceRegistry,
    @Autowired val schemaProvisioner: CouponSchemaProvisioner,
) {

    @Test
    fun `builds a pool sized to maxPoolSize`() {
        val sessionId = UUID.randomUUID()
        val schema = schemaProvisioner.provision(sessionId)

        val dataSource = registry.poolFor(sessionId, schema, 5)

        assertThat(dataSource.maximumPoolSize).isEqualTo(5)
        assertThat(dataSource.isClosed).isFalse()

        registry.evict(sessionId)
        schemaProvisioner.drop(sessionId)
    }

    @Test
    fun `resizing closes the old pool and opens a new one`() {
        val sessionId = UUID.randomUUID()
        val schema = schemaProvisioner.provision(sessionId)

        val original = registry.poolFor(sessionId, schema, 4)
        val resized = registry.poolFor(sessionId, schema, 8)

        assertThat(original.isClosed).isTrue()
        assertThat(resized.isClosed).isFalse()
        assertThat(resized.maximumPoolSize).isEqualTo(8)
        assertThat(resized).isNotSameAs(original)

        registry.evict(sessionId)
        schemaProvisioner.drop(sessionId)
    }

    @Test
    fun `requesting the same size returns the same pool instance`() {
        val sessionId = UUID.randomUUID()
        val schema = schemaProvisioner.provision(sessionId)

        val first = registry.poolFor(sessionId, schema, 6)
        val second = registry.poolFor(sessionId, schema, 6)

        assertThat(second).isSameAs(first)
        assertThat(first.isClosed).isFalse()

        registry.evict(sessionId)
        schemaProvisioner.drop(sessionId)
    }

    @Test
    fun `evict closes the pool`() {
        val sessionId = UUID.randomUUID()
        val schema = schemaProvisioner.provision(sessionId)
        val dataSource = registry.poolFor(sessionId, schema, 4)

        registry.evict(sessionId)

        assertThat(dataSource.isClosed).isTrue()
        schemaProvisioner.drop(sessionId)
    }
}
