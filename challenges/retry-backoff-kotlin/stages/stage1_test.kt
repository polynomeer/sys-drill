@file:JvmName("RunTest")

// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.

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
    val policy = RetryPolicy(maxAttempts = 5, baseDelay = 0.001, sleepFn = { })
    val result = policy.execute {
        attempts++
        if (attempts < 3) throw IllegalArgumentException("boom")
        "success"
    }
    expect(result == "success", "expected success, got $result")
    expect(attempts == 3, "expected exactly 3 attempts (2 failures + 1 success), got $attempts")
}
