-- Seeds the "Build your own Event Bus" challenge (docs/ROADMAP.md Phase 2
-- Build 과제 확장, PLAN.md step 17). Stage content is authored in
-- challenges/event-bus/ at the repo root — keep the two in sync if either
-- changes (see PLAN.md step 9 notes for why they're duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'a3000000-0000-0000-0000-000000000001',
    'event-bus',
    'Build your own Event Bus',
    'python',
    'event_bus.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a3000000-0000-0000-0000-000000000001',
    1,
    'pub/sub fan-out',
    '하나의 publish가 해당 topic의 모든 구독자에게 전달됨',
    $$from event_bus import EventBus
try:
    bus = EventBus()
    sub_a = bus.subscribe("orders")
    sub_b = bus.subscribe("orders")
    sub_c = bus.subscribe("payments")

    bus.publish("orders", "order-created")

    msg_a = bus.poll(sub_a)
    msg_b = bus.poll(sub_b)
    msg_c = bus.poll(sub_c)

    assert msg_a is not None and msg_a["payload"] == "order-created", "sub_a should receive the event"
    assert msg_b is not None and msg_b["payload"] == "order-created", "sub_b should receive the event (fan-out)"
    assert msg_c is None, "a subscriber to a different topic should not receive the event"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$$
),
(
    'a3000000-0000-0000-0000-000000000001',
    2,
    'at-least-once delivery',
    'ack 없이 visibility timeout이 지나면 재전달',
    $$from event_bus import EventBus
import time
try:
    bus = EventBus(visibility_timeout=0.3, max_retries=3)
    sub = bus.subscribe("orders")
    bus.publish("orders", "x")

    msg = bus.poll(sub)
    assert msg is not None, "expected an event"
    assert bus.poll(sub) is None, "in-flight event should not be immediately re-deliverable"

    time.sleep(0.4)
    redelivered = bus.poll(sub)
    assert redelivered is not None, "event should be redelivered after visibility timeout without ack"
    assert redelivered["payload"] == "x"
    bus.ack(sub, redelivered["id"])
    assert bus.poll(sub) is None, "acked event should not be redelivered"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$$
),
(
    'a3000000-0000-0000-0000-000000000001',
    3,
    'ordering',
    '같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달',
    $$from event_bus import EventBus
try:
    bus = EventBus()
    sub = bus.subscribe("orders")
    bus.publish("orders", "a")
    bus.publish("orders", "b")
    bus.publish("orders", "c")

    msg1 = bus.poll(sub)
    msg2 = bus.poll(sub)
    msg3 = bus.poll(sub)

    assert [msg1["payload"], msg2["payload"], msg3["payload"]] == ["a", "b", "c"], \
        f"expected FIFO order [a, b, c], got {[msg1['payload'], msg2['payload'], msg3['payload']]}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$$
),
(
    'a3000000-0000-0000-0000-000000000001',
    4,
    '동시성',
    '한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음',
    $$from event_bus import EventBus
import threading
try:
    bus = EventBus()
    sub = bus.subscribe("orders")
    for i in range(20):
        bus.publish("orders", i)

    received = []
    lock = threading.Lock()

    def worker():
        while True:
            msg = bus.poll(sub)
            if msg is None:
                break
            with lock:
                received.append(msg["payload"])

    threads = [threading.Thread(target=worker) for _ in range(5)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert len(received) == 20, f"expected 20 deliveries, got {len(received)}"
    assert sorted(received) == list(range(20)), "each event should be delivered exactly once across concurrent pollers"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$$
);
