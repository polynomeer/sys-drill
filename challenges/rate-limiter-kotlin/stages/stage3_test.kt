@file:JvmName("RunTest")

// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.incr()는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — allow()에 동시성 제어가 없으면 capacity를 넘긴다.
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
    val rl = RateLimiter(capacity = 50, windowSeconds = 5.0)
    val failure = AtomicReference<Throwable>()
    val threads = (1..10).map {
        thread {
            try {
                repeat(20) { rl.allow("shared-key") }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    threads.forEach { it.join() }
    failure.get()?.let { throw it }
    val allowed = rl.metrics.allowed
    expect(allowed <= 50, "concurrent access let $allowed requests through, expected <= 50")
}
