@file:JvmName("RunTest")

// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
import java.net.ConnectException

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
    val layer = IdempotencyLayer(ttlSeconds = 0.3)
    val attempts = mutableListOf<Int>()
    val flakyCharge = {
        attempts.add(1)
        if (attempts.size == 1) throw ConnectException("gateway timeout")
        "ch_1"
    }

    try {
        layer.execute("order-1", mapOf("amount" to 1000), flakyCharge)
        expect(false, "the operation's own error should propagate to the caller")
    } catch (e: ConnectException) {
        // expected
    }
    val result = layer.execute("order-1", mapOf("amount" to 1000), flakyCharge)
    expect(result == "ch_1", "a retry after a failure should run the operation again, got $result")
    expect(attempts.size == 2, "expected 2 attempts (the failure is not stored), got ${attempts.size}")

    Thread.sleep(400)
    val again = layer.execute("order-1", mapOf("amount" to 5000)) { "ch_new" }
    expect(again == "ch_new", "after the ttl the key is forgotten and may be reused, got $again")
}
