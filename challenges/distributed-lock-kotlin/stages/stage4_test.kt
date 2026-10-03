@file:JvmName("RunTest")

// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
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

const val ROUNDS = 200
const val CONTENDERS = 8

// One round of 8 simultaneous acquires can easily come out right by luck — the
// check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
fun stage() {
    val store = LockStore()
    repeat(ROUNDS) { round ->
        val successes = AtomicInteger()
        val failure = AtomicReference<Throwable>()
        val startGate = CountDownLatch(1)
        val threads = (0 until CONTENDERS).map { i ->
            thread {
                try {
                    val lock = DistributedLock("resource-$round", store = store, leaseSeconds = 5.0)
                    startGate.await()
                    if (lock.acquire("owner-$i") != null) successes.incrementAndGet()
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        startGate.countDown()
        threads.forEach { it.join() }
        failure.get()?.let { throw it }
        expect(
            successes.get() == 1,
            "round $round: expected exactly 1 successful acquire among $CONTENDERS concurrent attempts, got ${successes.get()}",
        )
    }
}
