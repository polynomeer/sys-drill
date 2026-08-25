from distributed_lock import DistributedLock, LockStore
import threading
try:
    store = LockStore()
    results = []
    results_lock = threading.Lock()

    def try_acquire(i):
        lock = DistributedLock("resource-1", store=store, lease_seconds=5.0)
        token = lock.acquire(f"owner-{i}")
        if token is not None:
            with results_lock:
                results.append(i)

    threads = [threading.Thread(target=try_acquire, args=(i,)) for i in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert len(results) == 1, f"expected exactly 1 successful acquire among 20 concurrent attempts, got {len(results)}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
