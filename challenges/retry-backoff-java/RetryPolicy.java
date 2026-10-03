/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Java)
 *
 * Implement `RetryPolicy` and `RetryBudget` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;
import java.util.function.DoubleConsumer;

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException extends RuntimeException {
    RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * A shared token bucket that caps the *total* number of retries across
 * every RetryPolicy that shares it — protects a downstream dependency from
 * a retry storm even when many independent callers are each individually
 * retrying. Pass the same RetryBudget instance to multiple RetryPolicy
 * instances to share it (stage 4).
 */
class RetryBudget {
    RetryBudget() {
        this(10);
    }

    RetryBudget(int capacity) {
        // TODO(stage 4): store the starting capacity.
        throw new UnsupportedOperationException("not implemented");
    }

    boolean tryConsume() {
        // TODO(stage 4): if a token is available, consume it and return true.
        // If the budget is exhausted, return false (and consume nothing).
        throw new UnsupportedOperationException("not implemented");
    }
}

public class RetryPolicy {
    public RetryPolicy() {
        this(5, 0.01, 1.0, null, null);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, null, sleepFn);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, maxDelay, null, sleepFn);
    }

    /** Stage 4: callers may pass a *shared* budget. */
    public RetryPolicy(int maxAttempts, double baseDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, budget, sleepFn);
    }

    /**
     * Delays are in seconds. `budget` may be null (no shared budget). `sleepFn`
     * receives each delay in seconds; null means "really sleep".
     */
    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        // TODO(stage 1): store config. Default sleepFn to a real sleep if null
        // (tests pass their own sleepFn so they don't have to actually wait).
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T execute(Callable<T> fn) {
        // TODO(stage 1): call fn.call(). On success, return its result
        // immediately. On failure (an exception), retry up to maxAttempts total
        // calls, then throw RetryExhaustedException.
        // TODO(stage 3): between attempts, call sleepFn.accept(delay) where
        // delay grows exponentially with the attempt number (baseDelay *
        // 2^attempt, capped at maxDelay) *with jitter* — don't use the
        // exact exponential value, pick randomly within [0, cappedValue]
        // ("full jitter") so many simultaneous retriers don't all retry at
        // the exact same moment (thundering herd).
        // TODO(stage 4): if a budget was provided, call budget.tryConsume()
        // before each retry (not before the first attempt). If it returns
        // false, stop retrying immediately (throw RetryExhaustedException) even
        // if maxAttempts hasn't been reached yet.
        throw new UnsupportedOperationException("not implemented");
    }
}
