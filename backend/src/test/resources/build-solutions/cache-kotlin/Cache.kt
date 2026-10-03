/*
 * SysDrill Build Mode — Build your own Cache (Kotlin)
 *
 * Implement `Cache` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.CompletableFuture

/** What stats() hands out. hitRatio is 0.0 when there were no gets yet. */
data class Stats(val hits: Long, val misses: Long, val hitRatio: Double)

/**
 * An in-process read-through cache in front of a slow loader (e.g. the
 * product DB): entries expire after a TTL, the least recently used entry
 * is evicted once `capacity` is reached, and concurrent misses for the
 * same key share a single load instead of all hitting the loader.
 */
class Cache(private val capacity: Int = 100) {
    private class Entry(val value: Any?, val expiresAtNanos: Long)

    private val lock = Any()

    // accessOrder = true: iteration order is recency, least recently used first.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > capacity
    }

    // key -> the in-flight load other callers wait on
    private val loading = HashMap<String, CompletableFuture<Any?>>()
    private var hits = 0L
    private var misses = 0L

    fun set(key: String, value: Any?, ttlSeconds: Double) {
        synchronized(lock) { put(key, value, ttlSeconds) }
    }

    fun get(key: String): Any? = synchronized(lock) {
        val e = lookup(key)
        if (e != null) {
            hits++
            e.value
        } else {
            misses++
            null
        }
    }

    fun getOrLoad(key: String, ttlSeconds: Double, loader: () -> Any?): Any? {
        val mine = CompletableFuture<Any?>()
        val pending = synchronized(lock) {
            lookup(key)?.let { return it.value }
            loading.putIfAbsent(key, mine)
        }
        if (pending != null) return pending.join() // someone else is loading — share its result

        try {
            val value = loader()
            synchronized(lock) { put(key, value, ttlSeconds) }
            mine.complete(value)
            return value
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            synchronized(lock) { loading.remove(key) }
        }
    }

    fun invalidate(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    fun stats(): Stats = synchronized(lock) {
        val total = hits + misses
        Stats(hits, misses, if (total == 0L) 0.0 else hits.toDouble() / total)
    }

    /** The live entry for key (marking it most recently used), or null. Caller holds lock. */
    private fun lookup(key: String): Entry? {
        val e = entries[key] ?: return null
        if (System.nanoTime() >= e.expiresAtNanos) {
            entries.remove(key)
            return null
        }
        return e
    }

    private fun put(key: String, value: Any?, ttlSeconds: Double) {
        entries[key] = Entry(value, System.nanoTime() + (ttlSeconds * 1_000_000_000).toLong())
    }
}
