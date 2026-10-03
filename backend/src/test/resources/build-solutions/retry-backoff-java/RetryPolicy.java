/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Java)
 *
 * Reference solution: passes all 4 stages.
 */

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException extends RuntimeException {
    RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * A shared token bucket that caps the *total* number of retries across
 * every RetryPolicy that shares it.
 */
class RetryBudget {
    private final AtomicInteger tokens;

    RetryBudget() {
        this(10);
    }

    RetryBudget(int capacity) {
        this.tokens = new AtomicInteger(capacity);
    }

    boolean tryConsume() {
        // Shared across policies (and threads), so check-and-decrement must be atomic.
        while (true) {
            int current = tokens.get();
            if (current <= 0) return false;
            if (tokens.compareAndSet(current, current - 1)) return true;
        }
    }
}

public class RetryPolicy {
    private final int maxAttempts;
    private final double baseDelay;
    private final double maxDelay;
    private final RetryBudget budget;
    private final DoubleConsumer sleepFn;

    public RetryPolicy() {
        this(5, 0.01, 1.0, null, null);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, null, sleepFn);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, maxDelay, null, sleepFn);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, budget, sleepFn);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.budget = budget;
        this.sleepFn = sleepFn != null ? sleepFn : RetryPolicy::sleepSeconds;
    }

    public <T> T execute(Callable<T> fn) {
        Exception lastError = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                if (budget != null && !budget.tryConsume()) {
                    throw new RetryExhaustedException("retry budget exhausted after " + attempt + " attempts", lastError);
                }
                sleepFn.accept(backoff(attempt - 1));
            }
            try {
                return fn.call();
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new RetryExhaustedException("all " + maxAttempts + " attempts failed", lastError);
    }

    /** Full jitter: uniform in [0, min(maxDelay, baseDelay * 2^retry)]. */
    private double backoff(int retry) {
        double capped = Math.min(maxDelay, baseDelay * Math.pow(2, retry));
        return ThreadLocalRandom.current().nextDouble() * capped;
    }

    private static void sleepSeconds(double seconds) {
        try {
            TimeUnit.NANOSECONDS.sleep((long) (seconds * 1e9));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
