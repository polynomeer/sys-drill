from event_bus import EventBus
import sys
import threading
# Switch threads every microsecond instead of every 5ms, so an unguarded
# check-then-update actually gets interleaved instead of running to completion.
sys.setswitchinterval(1e-6)
try:
    bus = EventBus()
    sub = bus.subscribe("orders")
    for i in range(1000):
        bus.publish("orders", i)

    received = []
    errors = []
    lock = threading.Lock()
    start_gate = threading.Event()

    def worker():
        start_gate.wait()
        try:
            while True:
                msg = bus.poll(sub)
                if msg is None:
                    break
                with lock:
                    received.append(msg["payload"])
        except Exception as e:  # a crash in a worker thread would otherwise vanish silently
            errors.append(e)

    threads = [threading.Thread(target=worker) for _ in range(8)]
    for t in threads:
        t.start()
    start_gate.set()
    for t in threads:
        t.join()
    if errors:
        raise errors[0]

    assert len(received) == 1000, f"expected 1000 deliveries, got {len(received)}"
    assert sorted(received) == list(range(1000)), "each event should be delivered exactly once across concurrent pollers"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
