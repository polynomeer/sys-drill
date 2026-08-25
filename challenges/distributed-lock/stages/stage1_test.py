from distributed_lock import DistributedLock, LockStore
try:
    store = LockStore()
    lock_a = DistributedLock("resource-1", store=store, lease_seconds=5.0)
    lock_b = DistributedLock("resource-1", store=store, lease_seconds=5.0)

    token_a = lock_a.acquire("owner-a")
    assert token_a is not None, "owner-a should acquire the free lock"
    assert lock_a.is_locked() is True

    token_b = lock_b.acquire("owner-b")
    assert token_b is None, "owner-b should not acquire while owner-a holds the lock"

    released = lock_a.release("owner-a", token_a)
    assert released is True, "owner-a should be able to release its own lock"

    token_b2 = lock_b.acquire("owner-b")
    assert token_b2 is not None, "owner-b should acquire after owner-a releases"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
