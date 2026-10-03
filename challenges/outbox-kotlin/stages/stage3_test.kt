@file:JvmName("RunTest")

// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.

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
    expect(relay.runOnce() == 1, "the first run should publish 1 event")

    service.placeOrder("o-2", 1)
    service.placeOrder("o-3", 4)
    broker.failNextPublish() // evt-2 hits a broker hiccup
    val published = try {
        relay.runOnce()
    } catch (e: BrokerException) {
        throw AssertionError("runOnce should stop and return when the broker fails, not throw")
    }
    var ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1"), "the relay must stop at the failed event, not skip ahead — broker has $ids")
    expect(published == 0, "nothing was published in that run, got $published")
    val pending = db.pendingEvents(100).map { it.id }
    expect(pending == listOf("evt-2", "evt-3"), "the failed event must stay pending (never mark before publishing), pending is $pending")

    expect(relay.runOnce() == 2, "once the broker recovers, the next run should publish the rest")
    ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-2", "evt-3"), "order must survive the failure, broker has $ids")
}
