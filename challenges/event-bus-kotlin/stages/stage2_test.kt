@file:JvmName("RunTest")

// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.

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
    val bus = EventBus(visibilityTimeout = 0.3, maxRetries = 3)
    val sub = bus.subscribe("orders")
    bus.publish("orders", "x")

    val msg = bus.poll(sub)
    expect(msg != null, "expected an event")
    expect(bus.poll(sub) == null, "in-flight event should not be immediately re-deliverable")

    Thread.sleep(400)
    val redelivered = bus.poll(sub)
    expect(redelivered != null, "event should be redelivered after visibility timeout without ack")
    expect(redelivered!!.payload == "x", "redelivered event should carry the original payload")
    bus.ack(sub, redelivered.id)
    expect(bus.poll(sub) == null, "acked event should not be redelivered")
}
