@file:JvmName("RunTest")

// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).

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
    val sharedStore = InMemoryStore()
    val instanceA = RateLimiter(capacity = 5, windowSeconds = 5.0, store = sharedStore)
    val instanceB = RateLimiter(capacity = 5, windowSeconds = 5.0, store = sharedStore)
    val totalAllowed = (0 until 10).count { i -> (if (i % 2 == 0) instanceA else instanceB).allow("shared-key") }
    expect(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got $totalAllowed")
}
