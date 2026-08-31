package com.sysdrill.backend.simulation.realinfra

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sweeps abandoned real-infra sessions across every real-infra domain
 * (coupon's Postgres schema/pool/Toxiproxy proxy since PLAN.md step 22/23;
 * notification's Kafka topic since step 27, via [RealInfraResourceCleaner]) —
 * none of these expire on their own the way
 * [SimulationStateStore][com.sysdrill.backend.simulation.SimulationStateStore]'s
 * Redis TTL does (PLAN.md step 21 notes flagged this as an explicit
 * fast-follow, not an oversight). Same single-background-thread shape as
 * `BuildRunnerWorker`/`EvaluationWorker`, but sleep-based instead of
 * blocking-poll since there's no job queue to drain — just a periodic idle
 * check (this codebase prefers a hand-rolled loop over `@Scheduled`, which
 * isn't enabled anywhere else in the app either).
 */
@Component
class RealInfraSessionSweepWorker(
    private val sessionTracker: RealInfraSessionTracker,
    private val measurementStore: RealInfraMeasurementStore,
    // PLAN.md step 27 — one bean per real-infra domain (coupon, notification),
    // Spring auto-collects every RealInfraResourceCleaner implementation into
    // this list. Cleaning up ALL of them for every expired session id (instead
    // of first figuring out which domain a session belongs to) is safe because
    // each domain's own cleanup calls are already idempotent no-ops when there
    // was nothing of theirs to clean up.
    private val cleaners: List<RealInfraResourceCleaner>,
    @Value("\${sysdrill.simulation.realinfra.sweep-interval-minutes}") sweepIntervalMinutes: Long,
    @Value("\${sysdrill.simulation.realinfra.session-idle-timeout-minutes}") idleTimeoutMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "realinfra-sweep-worker") }
    private val sweepInterval: Duration = Duration.ofMinutes(sweepIntervalMinutes)
    private val idleTimeout: Duration = Duration.ofMinutes(idleTimeoutMinutes)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.submit { runLoop() }
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    private fun runLoop() {
        while (running.get()) {
            try {
                sweepOnce()
            } catch (ex: Exception) {
                if (running.get()) {
                    log.error("Real-infra sweep loop error", ex)
                }
            }
            try {
                Thread.sleep(sweepInterval.toMillis())
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** internal, not private — RealInfraSessionSweepWorkerTest calls this directly to verify cleanup deterministically instead of waiting on the real sweep-interval timer. */
    internal fun sweepOnce() {
        val expired = sessionTracker.findExpired(idleTimeout)
        for (sessionId in expired) {
            runCatching {
                cleaners.forEach { it.cleanup(sessionId) }
                measurementStore.evict(sessionId)
                sessionTracker.forget(sessionId)
                log.info("Swept abandoned real-infra session {}", sessionId)
            }.onFailure { log.warn("Failed to sweep real-infra session {}: {}", sessionId, it.message) }
        }
    }
}
