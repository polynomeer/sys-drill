@file:JvmName("RunTest")

// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).

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
    val cb = CircuitBreaker(failureThreshold = 3, recoveryTimeout = 1.0)
    val result = cb.call { 42 }
    expect(result == 42, "expected 42, got $result")
    expect(cb.state == State.CLOSED, "expected CLOSED, got ${cb.state}")
    repeat(5) {
        val ok = cb.call { "ok" }
        expect(ok == "ok", "expected \"ok\", got $ok")
    }
    expect(cb.state == State.CLOSED, "expected CLOSED after 5 successful calls, got ${cb.state}")
}
