@file:JvmName("RunTest")

// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    val layer = IdempotencyLayer()
    val charges = Collections.synchronizedList(mutableListOf<String>())
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)

    val slowCharge = { // the payment gateway takes a while to answer
        charges.add("slow")
        started.countDown()
        release.await(5, TimeUnit.SECONDS)
        "ch_1"
    }
    val fastCharge = {
        charges.add("fast")
        "ch_dup"
    }

    val firstResult = Collections.synchronizedList(mutableListOf<Any>())
    val failure = AtomicReference<Throwable>()
    val first = thread {
        try {
            firstResult.add(layer.execute("order-1", mapOf("amount" to 1000), slowCharge))
        } catch (e: Throwable) {
            failure.compareAndSet(null, e)
            started.countDown() // don't make the main thread wait for a start that won't come
        }
    }
    expect(started.await(5, TimeUnit.SECONDS), "the first request never started running")
    failure.get()?.let { throw it }

    val outcome = Collections.synchronizedList(mutableListOf<Any>())
    val dup = thread {
        try {
            outcome.add(layer.execute("order-1", mapOf("amount" to 1000), fastCharge))
        } catch (e: IdempotencyInProgressException) {
            outcome.add("in-progress")
        } catch (e: Exception) {
            outcome.add(e)
        } catch (e: Throwable) {
            failure.compareAndSet(null, e)
        }
    }
    dup.join(2000)
    val blocked = dup.isAlive
    release.countDown()
    first.join(5000)
    dup.join(5000)
    failure.get()?.let { throw it }

    expect(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while the operation runs; reject it instead")
    expect(charges == listOf("slow"), "a duplicate arriving mid-flight must not charge again, charges were $charges")
    expect(outcome == listOf("in-progress"), "the duplicate should get IdempotencyInProgressException, got $outcome")
    expect(firstResult == listOf("ch_1"), "the first request should still finish normally, got $firstResult")
    expect(layer.execute("order-1", mapOf("amount" to 1000), fastCharge) == "ch_1", "once finished, the key should replay ch_1")
}
