from event_bus import EventBus
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
