@file:JvmName("RunTest")

// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).

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
    expect(cb.state == State.OPEN, "expected OPEN after 1 failure, got ${cb.state}")

    Thread.sleep(400)
    expect(cb.state == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got ${cb.state}")

    val result = cb.call { "recovered" }
    expect(result == "recovered", "expected \"recovered\", got $result")
    expect(cb.state == State.CLOSED, "a successful HALF_OPEN trial should recover to CLOSED, got ${cb.state}")
}
