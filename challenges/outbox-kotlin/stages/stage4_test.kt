@file:JvmName("RunTest")

// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.

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
    val db = Database()
    val broker = Broker()
    val service = OrderService(db, broker)
    val relay = OutboxRelay(db, broker)
    service.placeOrder("o-1", 3)

    db.failNextMarkPublished() // the relay crashes right after the broker took evt-1
    try {
        relay.runOnce()
    } catch (e: DatabaseException) {
        // the crash
    }
    relay.runOnce() // the restarted relay sends evt-1 again — it was never marked
    val ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-1"), "an event published but not marked should be sent again (at-least-once), broker has $ids")

    val consumer = InventoryConsumer(stock = 10)
    for (event in broker.published) consumer.handle(event)
    expect(consumer.stock == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got ${consumer.stock}")

    service.placeOrder("o-2", 2)
    relay.runOnce()
    consumer.handle(broker.published.last())
    expect(consumer.stock == 5, "a new event should still be applied: expected 5 left, got ${consumer.stock}")
}
