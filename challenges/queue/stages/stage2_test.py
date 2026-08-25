from queue_impl import Queue
import time
try:
    q = Queue(visibility_timeout=0.3, max_retries=3)
    q.enqueue("x")
    msg = q.dequeue()
    assert msg is not None, "expected a message"
    assert q.dequeue() is None, "in-flight message should not be immediately re-deliverable"
    time.sleep(0.4)
    redelivered = q.dequeue()
    assert redelivered is not None, "message should be redelivered after visibility timeout without ack"
    assert redelivered["payload"] == "x"
    q.ack(redelivered["id"])
    assert q.dequeue() is None, "acked message should not be redelivered"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
