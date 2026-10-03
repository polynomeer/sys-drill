"""Stage 3 — broker failure.
학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
"""
from outbox import Broker, BrokerError, Database, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    service.place_order("o-1", 3)
    assert relay.run_once() == 1

    service.place_order("o-2", 1)
    service.place_order("o-3", 4)
    broker.fail_next_publish()  # evt-2 hits a broker hiccup
    try:
        published = relay.run_once()
    except BrokerError:
        assert False, "run_once should stop and return when the broker fails, not raise"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1"], f"the relay must stop at the failed event, not skip ahead — broker has {ids}"
    assert published == 0, f"nothing was published in that run, got {published}"
    pending = [e["id"] for e in db.pending_events(100)]
    assert pending == ["evt-2", "evt-3"], f"the failed event must stay pending (never mark before publishing), pending is {pending}"

    assert relay.run_once() == 2, "once the broker recovers, the next run should publish the rest"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-2", "evt-3"], f"order must survive the failure, broker has {ids}"


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
