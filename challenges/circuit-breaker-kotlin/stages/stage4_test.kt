@file:JvmName("RunTest")

// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).

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
    val fail: () -> String = { throw IllegalArgumentException("boom") }

    val cb = CircuitBreaker(failureThreshold = 1, recoveryTimeout = 0.3)
    try {
        cb.call(fail)
    } catch (e: IllegalArgumentException) {
        // expected
    }
    Thread.sleep(400)
    expect(cb.state == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got ${cb.state}")

    try {
        cb.call(fail)
    } catch (e: IllegalArgumentException) {
        // the trial call's own failure — expected
    }
    expect(cb.state == State.OPEN, "a failed HALF_OPEN trial should return to OPEN, got ${cb.state}")

    try {
        cb.call { "should not run" }
        expect(false, "expected CircuitOpenException immediately after a failed trial (timeout must reset)")
    } catch (e: CircuitOpenException) {
        // fail fast — expected
    }
}
