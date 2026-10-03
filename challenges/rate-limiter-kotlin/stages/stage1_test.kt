@file:JvmName("RunTest")

// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).

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
    val rl = RateLimiter(capacity = 3, windowSeconds = 10.0)
    val allowed = (1..5).count { rl.allow("user-a") }
    expect(allowed == 3, "expected exactly 3 allowed within the window, got $allowed")
    expect(rl.allow("user-b"), "a different key should not be affected by user-a's budget")
}
