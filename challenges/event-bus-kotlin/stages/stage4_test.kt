@file:JvmName("RunTest")

// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음.
import java.util.Collections
import java.util.concurrent.CountDownLatch
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
    val bus = EventBus()
    val sub = bus.subscribe("orders")
    for (i in 0 until 20) bus.publish("orders", i)

    val received = Collections.synchronizedList(mutableListOf<Any?>())
    val failure = AtomicReference<Throwable>()
    val start = CountDownLatch(1)
    val threads = (1..5).map {
        thread {
            try {
                start.await()
                while (true) {
                    val msg = bus.poll(sub) ?: break
                    received.add(msg.payload)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    start.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(received.size == 20, "expected 20 deliveries, got ${received.size}")
    expect(received.map { it as Int }.sorted() == (0 until 20).toList(), "each event should be delivered exactly once across concurrent pollers")
}
