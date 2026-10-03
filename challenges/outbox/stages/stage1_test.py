"""Stage 1 — one transaction, no dual write.
학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
"""
from outbox import Broker, BrokerError, Database, DatabaseError, OrderService


def main():
    db, broker = Database(), Broker()
    service = OrderService(db, broker)

    broker.fail_next_publish()  # the broker being down must not matter when placing an order
    try:
        service.place_order("o-1", 3)
    except BrokerError:
        assert False, "place_order must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed"
    assert db.orders == {"o-1": 3}, f"the order should be saved, orders are {db.orders}"
    assert len(db.outbox) == 1, f"expected exactly 1 outbox event, got {db.outbox}"
    event = db.outbox[0]
    assert event["type"] == "OrderPlaced", f"expected an OrderPlaced event, got {event['type']}"
    assert event["payload"] == {"order_id": "o-1", "amount": 3}, f"unexpected payload {event['payload']}"
    assert db.commits == 1, f"the order and its event must be written in ONE transaction, saw {db.commits} commits"
    assert broker.published == [], "place_order must not publish to the broker itself (that's the dual write)"
    # With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
    healthy_db, healthy_broker = Database(), Broker()
    OrderService(healthy_db, healthy_broker).place_order("o-9", 1)
    assert healthy_broker.published == [], "place_order must not publish to the broker itself (that's the dual write)"

    db.fail_next_commit()
    try:
        service.place_order("o-2", 5)
        assert False, "a failed commit should surface to the caller as DatabaseError"
    except DatabaseError:
        pass
    assert "o-2" not in db.orders, "after a failed commit the order must not exist"
    assert all(e["payload"]["order_id"] != "o-2" for e in db.outbox), "after a failed commit its event must not exist either"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
