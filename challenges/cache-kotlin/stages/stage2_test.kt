@file:JvmName("RunTest")

// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.

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
    val c = Cache(capacity = 3)
    c.set("a", 1, ttlSeconds = 60.0)
    c.set("b", 2, ttlSeconds = 60.0)
    c.set("c", 3, ttlSeconds = 60.0)
    expect(c.get("a") == 1, "a should still be cached (capacity is 3)") // a is now the most recently used
    c.set("d", 4, ttlSeconds = 60.0) // over capacity: b is the least recently used
    expect(c.get("b") == null, "b was the least recently used key and should have been evicted")
    expect(c.get("a") == 1, "a was read just before the insert, so it must survive")
    expect(c.get("c") == 3, "c should survive — only one key needed to go")
    expect(c.get("d") == 4, "the newly inserted key d should be cached")
}
