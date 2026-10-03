@file:JvmName("RunTest")

// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
import kotlin.math.min
import kotlin.math.pow

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

/** One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts. */
fun recordDelays(): List<Double> {
    val recordedDelays = mutableListOf<Double>()
    val policy = RetryPolicy(maxAttempts = 6, baseDelay = 0.01, maxDelay = 10.0, sleepFn = { d -> recordedDelays.add(d) })
    try {
        policy.execute<Unit> { throw IllegalArgumentException("boom") }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    return recordedDelays
}

fun stage() {
    val first = recordDelays()
    val second = recordDelays()
    for (recordedDelays in listOf(first, second)) {
        expect(recordedDelays.size == 5, "expected 5 delays between 6 attempts, got ${recordedDelays.size}")
        recordedDelays.forEachIndexed { i, d ->
            val cap = min(10.0, 0.01 * 2.0.pow(i))
            expect(d in 0.0..cap, "delay $i = $d should be within [0, $cap] (exponential backoff cap)")
        }
    }
    // Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
    // "they vary" proves nothing — jitter means two runs don't wait the same amounts.
    expect(first != second, "jitter should randomize the delays, but two runs waited exactly the same: $first")
}
