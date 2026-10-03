@file:JvmName("RunTest")

// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.

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
    listOf(3, 1, 4).forEachIndexed { i, amount -> service.placeOrder("o-${i + 1}", amount) }

    val published = relay.runOnce()
    expect(published == 3, "runOnce should report 3 published events, got $published")
    val ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-2", "evt-3"), "events should be published oldest first, got $ids")
    val orderIds = broker.published.map { it.payload["order_id"] }
    expect(orderIds == listOf("o-1", "o-2", "o-3"), "events should carry their orders oldest first, got $orderIds")
    expect(db.pendingEvents(100).isEmpty(), "published events should be marked, nothing left pending")

    expect(relay.runOnce() == 0, "a second run with nothing pending should publish nothing")
    expect(broker.published.size == 3, "already-published events must not be sent again")
}
