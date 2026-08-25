package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SimulationSessionStateCodecTest {

    @Test
    fun `round-trips through encode and decode`() {
        val original = SimulationSessionState(
            domain = "coupon",
            incidentActive = true,
            traits = DesignTraits(rateLimitEnabled = true, cacheTtlSeconds = 300, dbPoolSize = 150),
        )

        val decoded = SimulationSessionStateCodec.decode(SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips notification and product-browsing traits`() {
        val original = SimulationSessionState(
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

        val decoded = SimulationSessionStateCodec.decode(SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }
}
