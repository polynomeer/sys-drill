from queue_impl import Queue
import threading
try:
    q = Queue(visibility_timeout=5.0, max_retries=3)
    for i in range(20):
        q.enqueue(i)

    received = []
    lock = threading.Lock()

    def worker():
        while True:
            msg = q.dequeue()
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
    assert sorted(received) == list(range(20)), "each message should be delivered exactly once across concurrent consumers"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
