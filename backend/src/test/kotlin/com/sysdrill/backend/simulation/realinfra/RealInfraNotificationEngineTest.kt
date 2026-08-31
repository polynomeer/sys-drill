package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.EngineMode
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SimulationSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * Exercises the real Kafka topic/consumer group + real producer/consumer
 * threads (not mocked) — PLAN.md step 27 / ADR-0014. Real wall-clock timing
 * means these assert ranges and relative comparisons, not exact hand-computed
 * values, same discipline as [RealInfraCouponEngineTest]. Each probe runs for
 * `sysdrill.simulation.realinfra.kafka.probe-duration-seconds` plus a drain
 * buffer, so this test class is genuinely slow (multiple real probes) — that
 * is expected, not a bug.
 */
@SpringBootTest
class RealInfraNotificationEngineTest(
    @Autowired val engine: RealInfraNotificationEngine,
    @Autowired val topicProvisioner: NotificationTopicProvisioner,
) {
    private val provisionedSessions = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        provisionedSessions.forEach { topicProvisioner.drop(it) }
        provisionedSessions.clear()
    }

    private fun session(sessionId: UUID, traits: DesignTraits) = SimulationSessionState(
        sessionId = sessionId,
        domain = RuleBasedSimulationEngine.DOMAIN_NOTIFICATION,
        incidentActive = true,
        traits = traits,
        engineMode = EngineMode.REAL_INFRA,
    )

    @Test
    fun `a fresh incident produces a real, plausible measurement`() {
        val sessionId = UUID.randomUUID().also { provisionedSessions += it }

        val state = engine.computeState(
            session(sessionId, DesignTraits(consumerCount = RealInfraNotificationEngine.INITIAL_CONSUMER_COUNT))
        )

        assertThat(state.p95LatencyMs).isGreaterThanOrEqualTo(0.0)
        assertThat(state.errorRate).isBetween(0.0, 1.0)
        assertThat(state.trafficRps).isGreaterThan(0.0)
        assertThat(state.queueLag).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `enabling the circuit breaker measurably reduces p95 latency and error rate under identical incident load`() {
        val baselineId = UUID.randomUUID().also { provisionedSessions += it }
        val breakerId = UUID.randomUUID().also { provisionedSessions += it }
        topicProvisioner.provision(baselineId)
        topicProvisioner.provision(breakerId)
        val baseTraits = DesignTraits(consumerCount = RealInfraNotificationEngine.INITIAL_CONSUMER_COUNT)

        val baseline = engine.computeState(session(baselineId, baseTraits))
        engine.applyAction(session(breakerId, baseTraits), SimulationActionType.ENABLE_CIRCUIT_BREAKER)
        val withBreaker = engine.computeState(session(breakerId, baseTraits.copy(circuitBreakerEnabled = true)))

        assertThat(withBreaker.p95LatencyMs).isLessThan(baseline.p95LatencyMs)
        assertThat(withBreaker.errorRate).isLessThanOrEqualTo(baseline.errorRate)
    }
}
