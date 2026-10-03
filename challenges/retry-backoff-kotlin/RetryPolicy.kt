/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Kotlin)
 *
 * Implement `RetryPolicy` and `RetryBudget` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * A shared token bucket that caps the *total* number of retries across
 * every RetryPolicy that shares it — protects a downstream dependency from
 * a retry storm even when many independent callers are each individually
 * retrying. Pass the same RetryBudget instance to multiple RetryPolicy
 * instances to share it (stage 4).
 */
class RetryBudget(capacity: Int = 10) {
    init {
        // TODO(stage 4): store the starting capacity.
        TODO("not implemented")
    }

    fun tryConsume(): Boolean {
        // TODO(stage 4): if a token is available, consume it and return true.
        // If the budget is exhausted, return false (and consume nothing).
        TODO("not implemented")
    }
}

/**
 * Delays are in seconds. `budget` may be null (no shared budget) — stage 4
 * passes a *shared* one. `sleepFn` receives each delay in seconds; null
 * means "really sleep".
 */
class RetryPolicy(
    maxAttempts: Int = 5,
    baseDelay: Double = 0.01,
    maxDelay: Double = 1.0,
    budget: RetryBudget? = null,
    sleepFn: ((Double) -> Unit)? = null,
) {
    init {
        // TODO(stage 1): store config. Default sleepFn to a real sleep if null
        // (tests pass their own sleepFn so they don't have to actually wait).
        TODO("not implemented")
    }

    fun <T> execute(fn: () -> T): T {
        // TODO(stage 1): call fn(). On success, return its result immediately.
        // On failure (an exception), retry up to maxAttempts total calls, then
        // throw RetryExhaustedException.
        // TODO(stage 3): between attempts, call sleepFn(delay) where delay
        // grows exponentially with the attempt number (baseDelay * 2^attempt,
        // capped at maxDelay) *with jitter* — don't use the exact exponential
        // value, pick randomly within [0, cappedValue] ("full jitter") so many
        // simultaneous retriers don't all retry at the exact same moment
        // (thundering herd).
        // TODO(stage 4): if a budget was provided, call budget.tryConsume()
        // before each retry (not before the first attempt). If it returns
        // false, stop retrying immediately (throw RetryExhaustedException) even
        // if maxAttempts hasn't been reached yet.
        TODO("not implemented")
    }
}
