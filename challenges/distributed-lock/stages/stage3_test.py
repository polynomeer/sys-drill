from distributed_lock import DistributedLock, LockStore
import time
try:
    store = LockStore()
    lock_a = DistributedLock("resource-1", store=store, lease_seconds=0.3)
    lock_b = DistributedLock("resource-1", store=store, lease_seconds=5.0)

    token_a = lock_a.acquire("owner-a")
    assert token_a is not None
    time.sleep(0.4)  # owner-a stalls (e.g. GC pause) past its own lease

    token_b = lock_b.acquire("owner-b")
    assert token_b is not None, "owner-b should acquire after owner-a's lease expired"
    assert token_b > token_a, "fencing tokens must increase monotonically across acquisitions"

    # owner-a wakes up late and tries to release with its now-stale token
    released = lock_a.release("owner-a", token_a)
    assert released is False, "a stale owner/token pair must not be able to release the current holder's lock"
    assert lock_b.is_locked() is True, "owner-b's lock must remain held despite owner-a's stale release attempt"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
