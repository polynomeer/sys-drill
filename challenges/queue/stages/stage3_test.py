from queue_impl import Queue
import time
try:
    q = Queue(visibility_timeout=0.2, max_retries=2)
    q.enqueue("y")
    for _ in range(2):
        msg = q.dequeue()
        assert msg is not None, "expected a message"
        time.sleep(0.3)
    assert q.dequeue() is None, "message should no longer be deliverable after exceeding max_retries"
    dlq = q.dead_letter_queue
    assert len(dlq) == 1, f"expected 1 message in DLQ, got {len(dlq)}"
    assert dlq[0]["payload"] == "y"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
