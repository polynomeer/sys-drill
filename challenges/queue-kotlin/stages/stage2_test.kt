@file:JvmName("RunTest")

// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).

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
    val q = Queue(visibilityTimeoutSeconds = 0.3, maxRetries = 3)
    q.enqueue("x")
    val msg = q.dequeue()
    expect(msg != null, "expected a message")
    expect(q.dequeue() == null, "in-flight message should not be immediately re-deliverable")
    Thread.sleep(400)
    val redelivered = q.dequeue()
    expect(redelivered != null, "message should be redelivered after visibility timeout without ack")
    expect(redelivered!!.payload == "x", "expected x, got ${redelivered.payload}")
    q.ack(redelivered.id)
    expect(q.dequeue() == null, "acked message should not be redelivered")
}
