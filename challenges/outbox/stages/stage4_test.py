"""Stage 4 — at-least-once, idempotent consumer.
학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
"""
from outbox import Broker, Database, DatabaseError, InventoryConsumer, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    service.place_order("o-1", 3)

    db.fail_next_mark_published()  # the relay crashes right after the broker took evt-1
    try:
        relay.run_once()
    except DatabaseError:
        pass
    relay.run_once()  # the restarted relay sends evt-1 again — it was never marked
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-1"], f"an event published but not marked should be sent again (at-least-once), broker has {ids}"

    consumer = InventoryConsumer(stock=10)
    for event in broker.published:
        consumer.handle(event)
    assert consumer.stock == 7, f"the duplicate evt-1 must reserve stock only once: expected 7 left, got {consumer.stock}"

    service.place_order("o-2", 2)
    relay.run_once()
    consumer.handle(broker.published[-1])
    assert consumer.stock == 5, f"a new event should still be applied: expected 5 left, got {consumer.stock}"


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
