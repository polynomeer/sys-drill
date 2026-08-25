-- Seeds the "알림 이벤트 처리" scenario's Build Mode counterpart from docs/PRD.md
-- §7.1 ("Build your own Queue"). Stage content is authored in
-- challenges/queue/ at the repo root — keep the two in sync if either
-- changes (see PLAN.md step 11 notes for why they're duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'e0000000-0000-0000-0000-000000000001',
    'queue',
    'Build your own Queue',
    'python',
    'queue_impl.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'e0000000-0000-0000-0000-000000000001',
    1,
    '기본 FIFO enqueue/dequeue',
    '큐의 기본 순서 보장 — 넣은 순서대로 나와야 한다',
    $$from queue_impl import Queue
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
$$
),
(
    'e0000000-0000-0000-0000-000000000001',
    2,
    'ack / visibility timeout',
    'at-least-once — ack 없이 visibility timeout이 지나면 재전달되고, ack하면 재전달되지 않는다',
    $$from queue_impl import Queue
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
$$
),
(
    'e0000000-0000-0000-0000-000000000001',
    3,
    '최대 재시도 + DLQ',
    'poison message 격리 — max_retries를 넘기면 재전달을 멈추고 dead_letter_queue로 옮긴다',
    $$from queue_impl import Queue
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
$$
),
(
    'e0000000-0000-0000-0000-000000000001',
    4,
    '동시성 안전성',
    '두 컨슈머가 같은 메시지를 동시에 받으면 안 된다 — dequeue()는 여러 스레드에서 안전해야 한다',
    $$from queue_impl import Queue
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
$$
);
