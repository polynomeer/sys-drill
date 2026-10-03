/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Kotlin)
 *
 * Reference solution: passes all 4 stages.
 */

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** A shared token bucket that caps the *total* number of retries across every RetryPolicy that shares it. */
class RetryBudget(capacity: Int = 10) {
    private val tokens = AtomicInteger(capacity)

    // Shared across policies (and threads), so check-and-decrement must be atomic.
    fun tryConsume(): Boolean {
        while (true) {
            val current = tokens.get()
            if (current <= 0) return false
            if (tokens.compareAndSet(current, current - 1)) return true
        }
    }
}

class RetryPolicy(
    private val maxAttempts: Int = 5,
    private val baseDelay: Double = 0.01,
    private val maxDelay: Double = 1.0,
    private val budget: RetryBudget? = null,
    sleepFn: ((Double) -> Unit)? = null,
) {
    private val sleepFn: (Double) -> Unit = sleepFn ?: { seconds -> Thread.sleep((seconds * 1000).toLong()) }

    fun <T> execute(fn: () -> T): T {
        var lastError: Exception? = null
        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) {
                if (budget != null && !budget.tryConsume()) {
                    throw RetryExhaustedException("retry budget exhausted after $attempt attempts", lastError)
                }
                sleepFn(backoff(attempt - 1))
            }
            try {
                return fn()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw RetryExhaustedException("all $maxAttempts attempts failed", lastError)
    }

    /** Full jitter: uniform in [0, min(maxDelay, baseDelay * 2^retry)]. */
    private fun backoff(retry: Int): Double = Random.nextDouble() * min(maxDelay, baseDelay * 2.0.pow(retry))
}
