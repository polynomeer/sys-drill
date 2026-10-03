"""Stage 2 — the relay.
학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
"""
from outbox import Broker, Database, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    for i, amount in enumerate((3, 1, 4), start=1):
        service.place_order(f"o-{i}", amount)

    published = relay.run_once()
    assert published == 3, f"run_once should report 3 published events, got {published}"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-2", "evt-3"], f"events should be published oldest first, got {ids}"
    assert [e["payload"]["order_id"] for e in broker.published] == ["o-1", "o-2", "o-3"]
    assert db.pending_events(100) == [], "published events should be marked, nothing left pending"

    assert relay.run_once() == 0, "a second run with nothing pending should publish nothing"
    assert len(broker.published) == 3, "already-published events must not be sent again"


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
