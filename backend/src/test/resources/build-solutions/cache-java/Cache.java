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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private record Entry(Object value, long expiresAtNanos) {}

    private final Object lock = new Object();
    private final Map<String, Entry> entries;
    // key -> the in-flight load other callers wait on
    private final Map<String, CompletableFuture<Object>> loading = new HashMap<>();
    private long hits;
    private long misses;

    public Cache() {
        this(100);
    }

    public Cache(int capacity) {
        // accessOrder = true: iteration order is recency, least recently used first.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > capacity;
            }
        };
    }

    public void set(String key, Object value, double ttlSeconds) {
        synchronized (lock) {
            put(key, value, ttlSeconds);
        }
    }

    public Object get(String key) {
        synchronized (lock) {
            Entry e = lookup(key);
            if (e != null) {
                hits++;
                return e.value();
            }
            misses++;
            return null;
        }
    }

    public Object getOrLoad(String key, Supplier<?> loader, double ttlSeconds) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> pending;
        synchronized (lock) {
            Entry e = lookup(key);
            if (e != null) return e.value();
            pending = loading.putIfAbsent(key, mine);
        }
        if (pending != null) return pending.join(); // someone else is loading — share its result

        try {
            Object value = loader.get();
            synchronized (lock) {
                put(key, value, ttlSeconds);
            }
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (lock) {
                loading.remove(key);
            }
        }
    }

    public void invalidate(String key) {
        synchronized (lock) {
            entries.remove(key);
        }
    }

    public Stats stats() {
        synchronized (lock) {
            long total = hits + misses;
            return new Stats(hits, misses, total == 0 ? 0.0 : (double) hits / total);
        }
    }

    /** The live entry for key (marking it most recently used), or null. Caller holds lock. */
    private Entry lookup(String key) {
        Entry e = entries.get(key);
        if (e == null) return null;
        if (System.nanoTime() >= e.expiresAtNanos()) {
            entries.remove(key);
            return null;
        }
        return e;
    }

    private void put(String key, Object value, double ttlSeconds) {
        entries.put(key, new Entry(value, System.nanoTime() + (long) (ttlSeconds * 1_000_000_000L)));
    }
}
