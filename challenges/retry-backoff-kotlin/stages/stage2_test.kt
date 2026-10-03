@file:JvmName("RunTest")

// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음.

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

fun stage() {
    var attempts = 0
    val policy = RetryPolicy(maxAttempts = 4, baseDelay = 0.001, sleepFn = { })
    try {
        policy.execute<Unit> {
            attempts++
            throw IllegalArgumentException("boom")
        }
        expect(false, "expected RetryExhaustedException once maxAttempts is exceeded")
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attempts == 4, "expected exactly 4 attempts (maxAttempts), got $attempts")
}
