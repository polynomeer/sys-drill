@file:JvmName("RunTest")

// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).

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
    val openLimiter = RateLimiter(capacity = 1, store = FaultyStore(), failMode = FailMode.OPEN)
    expect(openLimiter.allow("k"), "failMode=OPEN should admit requests when the store is unavailable")

    val closedLimiter = RateLimiter(capacity = 1, store = FaultyStore(), failMode = FailMode.CLOSED)
    expect(!closedLimiter.allow("k"), "failMode=CLOSED should reject requests when the store is unavailable")
}
