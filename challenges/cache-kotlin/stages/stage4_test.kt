@file:JvmName("RunTest")

// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
import kotlin.math.abs

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
    c.set("p1", "price=100", ttlSeconds = 60.0)
    expect(c.get("p1") == "price=100", "expected price=100") // hit
    c.invalidate("p1") // the price changed in the DB
    expect(c.get("p1") == null, "an invalidated key should miss") // miss
    expect(c.get("p2") == null, "p2 was never set and should miss") // miss
    c.set("p1", "price=120", ttlSeconds = 60.0)
    expect(c.get("p1") == "price=120", "after invalidation, the new value should be served") // hit
    c.invalidate("never-set") // must not throw

    val s = c.stats()
    expect(s.hits == 2L, "expected 2 hits, got ${s.hits}")
    expect(s.misses == 2L, "expected 2 misses, got ${s.misses}")
    expect(abs(s.hitRatio - 0.5) < 0.01, "expected hitRatio 0.5, got ${s.hitRatio}")
}
