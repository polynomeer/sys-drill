/*
 * SysDrill Build Mode — Build your own Rate Limiter (Kotlin)
 *
 * Implement `RateLimiter` below across 6 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.ConcurrentHashMap

/** A key -> counter store, standing in for something like Redis. */
interface Store {
    fun incr(key: String): Long

    fun expire(key: String, seconds: Double)
}

/** Thrown by a store that can't be reached — see [FaultyStore]. */
class StoreUnavailableException(message: String) : RuntimeException(message)

/**
 * Shared by every RateLimiter constructed with the same InMemoryStore object
 * — passing one store to two limiters is how stage 4 simulates "multiple
 * instances behind a shared rate-limit store".
 *
 * Each map operation is thread-safe on its own, but incr() is a read, a
 * round trip, then a write — like a GET and a SET against Redis — so two
 * concurrent incr() calls on the same key can race. That's intentional:
 * making allow() safe under concurrent calls is RateLimiter's job (stage 3).
 */
class InMemoryStore : Store {
    private val counts = ConcurrentHashMap<String, Long>()

    override fun incr(key: String): Long {
        val current = counts.getOrDefault(key, 0L)
        Thread.sleep(1) // simulated network latency between the read and the write
        val next = current + 1
        counts[key] = next
        return next
    }

    override fun expire(key: String, seconds: Double) {
        // TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        // Until you do, this does nothing — so a window never ends.
    }
}

/** Always throws — stage 5 uses this to simulate the store (e.g. Redis) being down, so you can test failMode. */
class FaultyStore : Store {
    override fun incr(key: String): Long = throw StoreUnavailableException("store unavailable")

    override fun expire(key: String, seconds: Double): Unit = throw StoreUnavailableException("store unavailable")
}

enum class FailMode { OPEN, CLOSED }

data class Metrics(val allowed: Long, val rejected: Long, val rejectRate: Double)

class RateLimiter(
    private val capacity: Int,
    private val windowSeconds: Double = 1.0,
    // Stage 4: callers may pass a *shared* store.
    private val store: Store = InMemoryStore(),
    private val failMode: FailMode = FailMode.OPEN,
) {
    fun allow(key: String): Boolean {
        // Stage 1 — uncomment the three lines below, delete the `TODO(...)` call, and submit.
        // val count = store.incr(key)
        // if (count == 1L) store.expire(key, windowSeconds)
        // return count <= capacity
        // TODO(stage 3): make this safe under concurrent calls.
        // TODO(stage 5): when the store throws StoreUnavailableException, admit if
        // failMode == OPEN, reject if failMode == CLOSED.
        // TODO(stage 6): track allowed/rejected counts for `metrics`.
        TODO("not implemented")
    }

    val metrics: Metrics
        // TODO(stage 6): return Metrics(allowed, rejected, rejectRate).
        get() = TODO("not implemented")
}
