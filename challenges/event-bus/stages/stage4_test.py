from event_bus import EventBus
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
