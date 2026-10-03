/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Java)
 *
 * Reference solution — passes all 4 stages.
 */

import java.util.concurrent.Callable;

enum State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException extends RuntimeException {
    CircuitOpenException(String message) {
        super(message);
    }
}

public class CircuitBreaker {
    private final int failureThreshold;
    private final long recoveryTimeoutNanos;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0;

    public CircuitBreaker() {
        this(3, 5.0);
    }

    public CircuitBreaker(int failureThreshold, double recoveryTimeoutSeconds) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeoutNanos = (long) (recoveryTimeoutSeconds * 1_000_000_000L);
    }

    public synchronized State state() {
        if (state == State.OPEN && System.nanoTime() - openedAt >= recoveryTimeoutNanos) {
            state = State.HALF_OPEN;
        }
        return state;
    }

    public <T> T call(Callable<T> fn) throws Exception {
        if (state() == State.OPEN) {
            throw new CircuitOpenException("circuit is open");
        }
        T result;
        try {
            result = fn.call();
        } catch (Exception e) {
            onFailure();
            throw e;
        }
        onSuccess();
        return result;
    }

    private synchronized void onSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;
        // A failed HALF_OPEN trial re-trips immediately, regardless of the threshold.
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = System.nanoTime();
        }
    }
}
