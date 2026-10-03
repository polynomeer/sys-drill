@file:JvmName("RunTest")

// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.

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
    var calls = 0
    val flaky: () -> String = {
        calls++
        throw IllegalArgumentException("boom")
    }

    val cb = CircuitBreaker(failureThreshold = 3, recoveryTimeout = 10.0)
    repeat(3) {
        try {
            cb.call(flaky)
        } catch (e: IllegalArgumentException) {
            // the wrapped function's own failure — expected
        }
    }
    expect(cb.state == State.OPEN, "expected OPEN after 3 failures, got ${cb.state}")
    expect(calls == 3, "expected the function to run 3 times, got $calls")

    try {
        cb.call(flaky)
        expect(false, "expected CircuitOpenException while OPEN")
    } catch (e: CircuitOpenException) {
        // fail fast — expected
    }
    expect(calls == 3, "the underlying function must not run while the circuit is OPEN (fail fast)")
}
