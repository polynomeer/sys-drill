@file:JvmName("RunTest")

// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).

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
    val q = Queue(visibilityTimeoutSeconds = 0.2, maxRetries = 2)
    q.enqueue("y")
    repeat(2) {
        val msg = q.dequeue()
        expect(msg != null, "expected a message")
        Thread.sleep(300)
    }
    expect(q.dequeue() == null, "message should no longer be deliverable after exceeding maxRetries")
    val dlq = q.deadLetterQueue
    expect(dlq.size == 1, "expected 1 message in DLQ, got ${dlq.size}")
    expect(dlq[0].payload == "y", "expected y in DLQ, got ${dlq[0].payload}")
}
