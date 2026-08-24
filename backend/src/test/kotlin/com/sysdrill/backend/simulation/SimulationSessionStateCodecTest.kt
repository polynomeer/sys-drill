package com.sysdrill.backend.simulation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SimulationSessionStateCodecTest {

    @Test
    fun `round-trips through encode and decode`() {
        val original = SimulationSessionState(
            incidentActive = true,
            traits = DesignTraits(rateLimitEnabled = true, cacheTtlSeconds = 300, dbPoolSize = 150),
        )

        val decoded = SimulationSessionStateCodec.decode(SimulationSessionStateCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }
}
