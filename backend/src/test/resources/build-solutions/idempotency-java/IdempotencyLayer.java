/*
 * SysDrill Build Mode — Build your own Idempotency Layer (Java)
 *
 * Implement the classes below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

class IdempotencyConflictException extends RuntimeException {
    IdempotencyConflictException(String message) {
        super(message);
    }
}

class IdempotencyInProgressException extends RuntimeException {
    IdempotencyInProgressException(String message) {
        super(message);
    }
}

public class IdempotencyLayer {
    /** done == false means the operation is still running (the key is claimed, no result yet). */
    private record Entry(Object request, Object result, boolean done, long expiresAtNanos) {}

    private final long ttlNanos;
    private final Object lock = new Object();
    private final Map<String, Entry> entries = new HashMap<>();

    public IdempotencyLayer() {
        this(86400.0);
    }

    public IdempotencyLayer(double ttlSeconds) {
        this.ttlNanos = (long) (ttlSeconds * 1_000_000_000L);
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Object request, Callable<T> operation) throws Exception {
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry != null && entry.done() && entry.expiresAtNanos() - System.nanoTime() <= 0) {
                entries.remove(key);
                entry = null;
            }
            if (entry != null) {
                if (!Objects.equals(entry.request(), request)) {
                    throw new IdempotencyConflictException("key " + key + " was first used with a different request");
                }
                if (!entry.done()) {
                    throw new IdempotencyInProgressException("a request with key " + key + " is still running");
                }
                return (T) entry.result();
            }
            // claim the key before running the operation, so a concurrent duplicate sees it in flight
            entries.put(key, new Entry(request, null, false, 0));
        }
        boolean succeeded = false;
        try {
            T result = operation.call(); // no lock held: a duplicate is rejected, not blocked
            synchronized (lock) {
                entries.put(key, new Entry(request, result, true, System.nanoTime() + ttlNanos));
            }
            succeeded = true;
            return result;
        } finally {
            if (!succeeded) {
                synchronized (lock) {
                    entries.remove(key); // nothing stored — a retry runs the operation again
                }
            }
        }
    }
}
