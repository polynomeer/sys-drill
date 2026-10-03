@file:JvmName("RunTest")

// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

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
    val loads = AtomicInteger()
    val slowLoader = {
        loads.incrementAndGet()
        Thread.sleep(200) // a slow DB query
        "product-42"
    }

    val results = Collections.synchronizedList(mutableListOf<Any?>())
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (1..8).map {
        thread {
            try {
                startGate.await()
                results.add(c.getOrLoad("hot", ttlSeconds = 5.0, loader = slowLoader))
            } catch (e: Throwable) { // a crash in a worker thread would otherwise vanish silently
                failure.compareAndSet(null, e)
            }
        }
    }
    startGate.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(loads.get() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got ${loads.get()}")
    expect(results.toList() == List(8) { "product-42" }, "every caller should get the loaded value, got $results")
    expect(c.getOrLoad("hot", ttlSeconds = 5.0, loader = slowLoader) == "product-42", "expected product-42 from the cache")
    expect(loads.get() == 1, "once loaded, the value should come from the cache, not the loader")
}
