@file:JvmName("RunTest")

// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).

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
    val rl = RateLimiter(capacity = 2, windowSeconds = 0.5)
    expect(rl.allow("k"), "expected the first request to be allowed")
    expect(rl.allow("k"), "expected the second request to be allowed")
    expect(!rl.allow("k"), "third request within the window should be rejected")
    Thread.sleep(700)
    expect(rl.allow("k"), "after the window elapses, capacity should replenish")
}
