from queue_impl import Queue
try:
    q = Queue(visibility_timeout=5.0, max_retries=3)
    q.enqueue("a")
    q.enqueue("b")
    q.enqueue("c")
    msg1 = q.dequeue()
    msg2 = q.dequeue()
    msg3 = q.dequeue()
    assert msg1["payload"] == "a", f"expected a, got {msg1['payload']}"
    assert msg2["payload"] == "b", f"expected b, got {msg2['payload']}"
    assert msg3["payload"] == "c", f"expected c, got {msg3['payload']}"
    assert q.dequeue() is None, "queue should be empty"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
