package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SimulationSessionStateCodecTest {

    private val sessionId: UUID = UUID.randomUUID()

    @Test
    fun `round-trips through encode and decode`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "coupon",
            incidentActive = true,
            traits = DesignTraits(rateLimitEnabled = true, cacheTtlSeconds = 300, dbPoolSize = 150),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips notification and product-browsing traits`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "product-browsing",
            incidentActive = true,
            traits = DesignTraits(
                consumerCount = 12,
                circuitBreakerEnabled = true,
                retryBackoffMultiplier = 5,
                cachePolicySplit = true,
                singleFlightEnabled = true,
                readReplicaCount = 2,
            ),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips payment traits`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "payment",
            incidentActive = true,
            traits = DesignTraits(
                dispatcherWorkers = 12,
                idempotentPgRetryEnabled = true,
                paymentPoolIsolated = true,
            ),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips reservation traits`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "reservation",
            incidentActive = true,
            traits = DesignTraits(
                fineGrainedLockingEnabled = true,
                holdTimeoutSeconds = 30,
                atomicInventoryCheckEnabled = true,
            ),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips batch-settlement traits`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "batch-settlement",
            incidentActive = true,
            traits = DesignTraits(
                checkpointingEnabled = true,
                chunkSize = 1000,
                idempotentReconciliationEnabled = true,
            ),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips autoscaling traits`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "autoscaling",
            incidentActive = true,
            traits = DesignTraits(
                podReplicas = 40,
                resourceLimitsTuned = true,
                rolloutSafeguardEnabled = true,
            ),
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips engineMode`() {
        val original = SimulationSessionState(
            sessionId = sessionId,
            domain = "coupon",
            incidentActive = true,
            traits = DesignTraits(),
            engineMode = EngineMode.REAL_INFRA,
        )

        val decoded = SimulationSessionStateCodec.decode(sessionId, SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }
}
