@file:JvmName("RunTest")

// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.

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
    bus.publish("orders", "a")
    bus.publish("orders", "b")
    bus.publish("orders", "c")

    val msg1 = bus.poll(sub)
    val msg2 = bus.poll(sub)
    val msg3 = bus.poll(sub)

    val got = listOf(msg1!!.payload, msg2!!.payload, msg3!!.payload)
    expect(got == listOf("a", "b", "c"), "expected FIFO order [a, b, c], got $got")
}
