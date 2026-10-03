/*
 * SysDrill Build Mode — Build your own Cache (Java)
 *
 * Implement `Cache` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.function.Supplier;

/** What stats() hands out. hitRatio is 0.0 when there were no gets yet. */
record Stats(long hits, long misses, double hitRatio) {}

/**
 * An in-process read-through cache in front of a slow loader (e.g. the
 * product DB): entries expire after a TTL, the least recently used entry
 * is evicted once `capacity` is reached, and concurrent misses for the
 * same key share a single load instead of all hitting the loader.
 */
public class Cache {
    public Cache() {
        this(100);
    }

    public Cache(int capacity) {
        // TODO(stage 1): store config and set up whatever storage you need.
        throw new UnsupportedOperationException("not implemented");
    }

    public void set(String key, Object value, double ttlSeconds) {
        // TODO(stage 1): store `value` under `key`; it expires `ttlSeconds` from now.
        // TODO(stage 2): once more than `capacity` keys are stored, evict the
        // least recently used one (a get() or set() counts as a use).
        throw new UnsupportedOperationException("not implemented");
    }

    public Object get(String key) {
        // TODO(stage 1): the value for `key`, or null if it's missing or expired.
        // TODO(stage 4): count every get() as a hit or a miss for stats().
        throw new UnsupportedOperationException("not implemented");
    }

    public Object getOrLoad(String key, Supplier<?> loader, double ttlSeconds) {
        // TODO(stage 3): return the cached value if present. Otherwise call
        // loader.get() — a slow call such as a DB query — store its result
        // with `ttlSeconds`, and return it. When many threads miss the same
        // key at the same time, the loader must run only ONCE; the others
        // wait for that one load and get its result (single-flight — this is
        // what stops a cache stampede from flattening the DB when a hot key
        // expires).
        throw new UnsupportedOperationException("not implemented");
    }

    public void invalidate(String key) {
        // TODO(stage 4): drop `key` so the next read misses (e.g. after the
        // product's price changed in the DB).
        throw new UnsupportedOperationException("not implemented");
    }

    public Stats stats() {
        // TODO(stage 4): new Stats(hits, misses, hitRatio)
        // (hitRatio is 0.0 when there were no gets yet).
        throw new UnsupportedOperationException("not implemented");
    }
}
