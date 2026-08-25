from event_bus import EventBus
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
