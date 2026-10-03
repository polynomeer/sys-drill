@file:JvmName("RunTest")

// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.

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
    val subA = bus.subscribe("orders")
    val subB = bus.subscribe("orders")
    val subC = bus.subscribe("payments")

    bus.publish("orders", "order-created")

    val msgA = bus.poll(subA)
    val msgB = bus.poll(subB)
    val msgC = bus.poll(subC)

    expect(msgA != null && msgA.payload == "order-created", "sub_a should receive the event")
    expect(msgB != null && msgB.payload == "order-created", "sub_b should receive the event (fan-out)")
    expect(msgC == null, "a subscriber to a different topic should not receive the event")
}
