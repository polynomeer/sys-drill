/*
 * SysDrill Build Mode — Build your own Rate Limiter (Java)
 *
 * Implement `RateLimiter` below across 6 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A key -> counter store, standing in for something like Redis. */
interface Store {
    long incr(String key);

    void expire(String key, double seconds);
}

/** Thrown by a store that can't be reached — see {@link FaultyStore}. */
class StoreUnavailableException extends RuntimeException {
    StoreUnavailableException(String message) {
        super(message);
    }
}

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
class InMemoryStore implements Store {
    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    @Override
    public long incr(String key) {
        long current = counts.getOrDefault(key, 0L);
        roundTrip();
        long next = current + 1;
        counts.put(key, next);
        return next;
    }

    @Override
    public void expire(String key, double seconds) {
        // TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        // Until you do, this does nothing — so a window never ends.
    }

    /** Simulated network latency between the read and the write. */
    private static void roundTrip() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/** Always throws — stage 5 uses this to simulate the store (e.g. Redis) being down, so you can test failMode. */
class FaultyStore implements Store {
    @Override
    public long incr(String key) {
        throw new StoreUnavailableException("store unavailable");
    }

    @Override
    public void expire(String key, double seconds) {
        throw new StoreUnavailableException("store unavailable");
    }
}

enum FailMode { OPEN, CLOSED }

record Metrics(long allowed, long rejected, double rejectRate) {}

public class RateLimiter {
    private final int capacity;
    private final double windowSeconds;
    private final Store store;
    private final FailMode failMode;

    public RateLimiter(int capacity, double windowSeconds) {
        this(capacity, windowSeconds, new InMemoryStore(), FailMode.OPEN);
    }

    /** Stage 4: callers may pass a *shared* store. */
    public RateLimiter(int capacity, double windowSeconds, Store store) {
        this(capacity, windowSeconds, store, FailMode.OPEN);
    }

    public RateLimiter(int capacity, double windowSeconds, Store store, FailMode failMode) {
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
        this.store = store;
        this.failMode = failMode;
    }

    public boolean allow(String key) {
        // Stage 1 — uncomment the three lines below, delete the `throw` line, and submit.
        // long count = store.incr(key);
        // if (count == 1) store.expire(key, windowSeconds);
        // return count <= capacity;
        // TODO(stage 3): make this safe under concurrent calls.
        // TODO(stage 5): when the store throws StoreUnavailableException, admit if
        // failMode == OPEN, reject if failMode == CLOSED.
        // TODO(stage 6): track allowed/rejected counts for metrics().
        throw new UnsupportedOperationException("not implemented");
    }

    public Metrics metrics() {
        // TODO(stage 6): return new Metrics(allowed, rejected, rejectRate).
        throw new UnsupportedOperationException("not implemented");
    }
}
