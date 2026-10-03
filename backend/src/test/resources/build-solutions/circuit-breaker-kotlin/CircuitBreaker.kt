/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Kotlin)
 *
 * Reference solution — passes all 4 stages.
 */

enum class State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException(message: String) : RuntimeException(message)

class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val recoveryTimeout: Double = 5.0, // seconds
) {
    private val recoveryTimeoutNanos = (recoveryTimeout * 1_000_000_000).toLong()
    private var current = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAt = 0L

    val state: State
        @Synchronized get() {
            if (current == State.OPEN && System.nanoTime() - openedAt >= recoveryTimeoutNanos) {
                current = State.HALF_OPEN
            }
            return current
        }

    fun <T> call(fn: () -> T): T {
        if (state == State.OPEN) throw CircuitOpenException("circuit is open")
        val result = try {
            fn()
        } catch (e: Exception) {
            onFailure()
            throw e
        }
        onSuccess()
        return result
    }

    @Synchronized
    private fun onSuccess() {
        consecutiveFailures = 0
        current = State.CLOSED
    }

    @Synchronized
    private fun onFailure() {
        consecutiveFailures++
        // A failed HALF_OPEN trial re-trips immediately, regardless of the threshold.
        if (current == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            current = State.OPEN
            openedAt = System.nanoTime()
        }
    }
}
