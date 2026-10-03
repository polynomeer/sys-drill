@file:JvmName("RunTest")

// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 스레드가
// 동시에 dequeue()해도 각 메시지는 정확히 한 번만 전달돼야 한다).
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
    val q = Queue(visibilityTimeoutSeconds = 5.0, maxRetries = 3)
    for (i in 0 until 20) q.enqueue(i)

    val received = Collections.synchronizedList(mutableListOf<Int>())
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (1..5).map {
        thread {
            try {
                startGate.await()
                while (true) {
                    val msg = q.dequeue() ?: break
                    received.add(msg.payload as Int)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    startGate.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(received.size == 20, "expected 20 deliveries, got ${received.size}")
    expect(received.sorted() == (0 until 20).toList(), "each message should be delivered exactly once across concurrent consumers")
}
