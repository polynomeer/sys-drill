@file:JvmName("RunTest")

// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.

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
        charges.add(1000)
        mapOf("charge_id" to "ch_${charges.size}", "amount" to 1000)
    }

    val first = layer.execute("order-1", mapOf("amount" to 1000), charge)
    val retry = layer.execute("order-1", mapOf("amount" to 1000), charge)
    expect(charges.size == 1, "a retry with the same key must not charge again, got ${charges.size} charges")
    expect(retry == first, "the retry should get the original result $first, got $retry")

    val other = layer.execute("order-2", mapOf("amount" to 1000), charge)
    expect(charges.size == 2, "a different key is a different request and should charge")
    expect(other["charge_id"] == "ch_2", "expected ch_2 for the new key, got $other")
}
