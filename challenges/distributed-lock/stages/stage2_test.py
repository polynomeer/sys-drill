from distributed_lock import DistributedLock, LockStore
import time
try:
    store = LockStore()
    lock_a = DistributedLock("resource-1", store=store, lease_seconds=0.3)
    lock_b = DistributedLock("resource-1", store=store, lease_seconds=0.3)

    token_a = lock_a.acquire("owner-a")
    assert token_a is not None
    assert lock_b.acquire("owner-b") is None, "owner-b should not acquire before the lease expires"

    time.sleep(0.4)
    assert lock_a.is_locked() is False, "the lock should report unlocked once the lease has expired"

    token_b = lock_b.acquire("owner-b")
    assert token_b is not None, "owner-b should acquire once owner-a's lease has expired without release"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
