@file:JvmName("RunTest")

// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.

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
    val layer = IdempotencyLayer()
    val charges = mutableListOf<Int>()
    val charge = {
        charges.add(1)
        "ch_${charges.size}"
    }

    layer.execute("order-1", mapOf("amount" to 1000, "currency" to "KRW"), charge)
    // a new map with the same contents is the same request
    val replay = layer.execute("order-1", mapOf("amount" to 1000, "currency" to "KRW"), charge)
    expect(replay == "ch_1", "an equal request should be replayed, got $replay")

    try {
        layer.execute("order-1", mapOf("amount" to 2000, "currency" to "KRW"), charge)
        expect(false, "reusing a key with a different request should throw IdempotencyConflictException")
    } catch (e: IdempotencyConflictException) {
        // expected
    }
    expect(charges.size == 1, "a conflicting request must not charge, got ${charges.size} charges")
}
