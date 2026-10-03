from distributed_lock import DistributedLock, LockStore
import sys
import threading
# Switch threads every microsecond instead of every 5ms, so an unguarded
# check-then-claim actually gets interleaved instead of running to completion.
sys.setswitchinterval(1e-6)
ROUNDS = 200
CONTENDERS = 8
try:
    # One round of 8 simultaneous acquires can easily come out right by luck — the
    # check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
    store = LockStore()
    for round_no in range(ROUNDS):
        results = []
        errors = []
        results_lock = threading.Lock()
        start_gate = threading.Event()

        def try_acquire(i, key=f"resource-{round_no}"):
            try:
                lock = DistributedLock(key, store=store, lease_seconds=5.0)
                start_gate.wait()
                token = lock.acquire(f"owner-{i}")
                if token is not None:
                    with results_lock:
                        results.append(i)
            except Exception as e:  # a crash in a worker thread would otherwise vanish silently
                errors.append(e)

        threads = [threading.Thread(target=try_acquire, args=(i,)) for i in range(CONTENDERS)]
        for t in threads:
            t.start()
        start_gate.set()
        for t in threads:
            t.join()
        if errors:
            raise errors[0]

        assert len(results) == 1, (
            f"round {round_no}: expected exactly 1 successful acquire among {CONTENDERS} concurrent attempts, got {len(results)}"
        )
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
