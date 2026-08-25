from event_bus import EventBus
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
