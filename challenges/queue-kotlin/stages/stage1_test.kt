@file:JvmName("RunTest")

// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).

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
    q.enqueue("a")
    q.enqueue("b")
    q.enqueue("c")
    val msg1 = q.dequeue()!!
    val msg2 = q.dequeue()!!
    val msg3 = q.dequeue()!!
    expect(msg1.payload == "a", "expected a, got ${msg1.payload}")
    expect(msg2.payload == "b", "expected b, got ${msg2.payload}")
    expect(msg3.payload == "c", "expected c, got ${msg3.payload}")
    expect(q.dequeue() == null, "queue should be empty")
}
