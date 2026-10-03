@file:JvmName("RunTest")

// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.

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
    val c = Cache(capacity = 10)
    expect(c.get("missing") == null, "a key that was never set should be a miss (null)")
    c.set("p1", "price=100", ttlSeconds = 0.3)
    val p1 = c.get("p1")
    expect(p1 == "price=100", "expected price=100 before the TTL, got $p1")
    c.set("p2", "price=200", ttlSeconds = 5.0)
    Thread.sleep(400)
    expect(c.get("p1") == null, "p1 should have expired after its 0.3s TTL")
    expect(c.get("p2") == "price=200", "p2 has a 5s TTL and should still be cached")
}
