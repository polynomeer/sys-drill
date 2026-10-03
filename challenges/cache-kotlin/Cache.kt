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

/** What stats() hands out. hitRatio is 0.0 when there were no gets yet. */
data class Stats(val hits: Long, val misses: Long, val hitRatio: Double)

/**
 * An in-process read-through cache in front of a slow loader (e.g. the
 * product DB): entries expire after a TTL, the least recently used entry
 * is evicted once `capacity` is reached, and concurrent misses for the
 * same key share a single load instead of all hitting the loader.
 */
class Cache(private val capacity: Int = 100) {
    // TODO(stage 1): set up whatever storage you need.

    fun set(key: String, value: Any?, ttlSeconds: Double) {
        // TODO(stage 1): store `value` under `key`; it expires `ttlSeconds` from now.
        // TODO(stage 2): once more than `capacity` keys are stored, evict the
        // least recently used one (a get() or set() counts as a use).
        TODO("not implemented")
    }

    fun get(key: String): Any? {
        // TODO(stage 1): the value for `key`, or null if it's missing or expired.
        // TODO(stage 4): count every get() as a hit or a miss for stats().
        TODO("not implemented")
    }

    fun getOrLoad(key: String, ttlSeconds: Double, loader: () -> Any?): Any? {
        // TODO(stage 3): return the cached value if present. Otherwise call
        // loader() — a slow call such as a DB query — store its result with
        // `ttlSeconds`, and return it. When many threads miss the same key at
        // the same time, loader() must run only ONCE; the others wait for
        // that one load and get its result (single-flight — this is what
        // stops a cache stampede from flattening the DB when a hot key expires).
        TODO("not implemented")
    }

    fun invalidate(key: String) {
        // TODO(stage 4): drop `key` so the next read misses (e.g. after the
        // product's price changed in the DB).
        TODO("not implemented")
    }

    fun stats(): Stats {
        // TODO(stage 4): Stats(hits, misses, hitRatio)
        // (hitRatio is 0.0 when there were no gets yet).
        TODO("not implemented")
    }
}
