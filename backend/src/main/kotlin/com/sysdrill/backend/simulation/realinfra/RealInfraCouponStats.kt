package com.sysdrill.backend.simulation.realinfra

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-session, in-process counters for the real-infra coupon pilot's cache
 * and rate-limit behavior (PLAN.md step 21) — single-JVM-instance
 * assumption, same as everywhere else real-infra state lives outside
 * Redis/Postgres (see [RealInfraCouponEngine]'s per-session probe lock).
 */
@Component
class RealInfraCouponStats {
    private val limiters = ConcurrentHashMap<UUID, FixedWindowLimiter>()
    private val cacheHits = ConcurrentHashMap<UUID, AtomicInteger>()
    private val cacheMisses = ConcurrentHashMap<UUID, AtomicInteger>()

    /** Resets this session's rate-limit window — called at the start of each probe so results are comparable. */
    fun resetLimiter(sessionId: UUID, maxPerWindow: Int, windowMillis: Long) {
        limiters[sessionId] = FixedWindowLimiter(maxPerWindow, windowMillis)
    }

    fun tryAcquire(sessionId: UUID): Boolean = limiters[sessionId]?.tryAcquire() ?: true

    fun resetCacheCounters(sessionId: UUID) {
        cacheHits[sessionId] = AtomicInteger(0)
        cacheMisses[sessionId] = AtomicInteger(0)
    }

    fun recordCacheHit(sessionId: UUID) {
        cacheHits.getOrPut(sessionId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordCacheMiss(sessionId: UUID) {
        cacheMisses.getOrPut(sessionId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun cacheHitRatio(sessionId: UUID): Double {
        val hits = cacheHits[sessionId]?.get() ?: 0
        val misses = cacheMisses[sessionId]?.get() ?: 0
        val total = hits + misses
        return if (total == 0) 0.0 else hits.toDouble() / total
    }

    fun evict(sessionId: UUID) {
        limiters.remove(sessionId)
        cacheHits.remove(sessionId)
        cacheMisses.remove(sessionId)
    }

    private class FixedWindowLimiter(private val maxPerWindow: Int, private val windowMillis: Long) {
        private val windowStart = AtomicLong(System.currentTimeMillis())
        private val count = AtomicInteger(0)

        fun tryAcquire(): Boolean {
            val now = System.currentTimeMillis()
            val start = windowStart.get()
            if (now - start >= windowMillis && windowStart.compareAndSet(start, now)) {
                count.set(0)
            }
            return count.incrementAndGet() <= maxPerWindow
        }
    }
}
