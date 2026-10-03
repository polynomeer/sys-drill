@file:JvmName("RunTest")

// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.

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

    broker.failNextPublish() // the broker being down must not matter when placing an order
    try {
        service.placeOrder("o-1", 3)
    } catch (e: BrokerException) {
        throw AssertionError("placeOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed")
    }
    expect(db.orders == mapOf("o-1" to 3), "the order should be saved, orders are ${db.orders}")
    expect(db.outbox.size == 1, "expected exactly 1 outbox event, got ${db.outbox}")
    val event = db.outbox[0]
    expect(event.type == "OrderPlaced", "expected an OrderPlaced event, got ${event.type}")
    expect(event.payload == mapOf("order_id" to "o-1", "amount" to 3), "unexpected payload ${event.payload}")
    expect(db.commits == 1, "the order and its event must be written in ONE transaction, saw ${db.commits} commits")
    expect(broker.published.isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)")
    // With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
    val healthyBroker = Broker()
    OrderService(Database(), healthyBroker).placeOrder("o-9", 1)
    expect(healthyBroker.published.isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)")

    db.failNextCommit()
    try {
        service.placeOrder("o-2", 5)
        expect(false, "a failed commit should surface to the caller as DatabaseException")
    } catch (e: DatabaseException) {
        // expected
    }
    expect("o-2" !in db.orders, "after a failed commit the order must not exist")
    expect(db.outbox.all { it.payload["order_id"] != "o-2" }, "after a failed commit its event must not exist either")
}
