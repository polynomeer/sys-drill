-- Seeds the "Build your own Distributed Lock" challenge (docs/ROADMAP.md
-- Phase 2 Build 과제 확장, PLAN.md step 15). Stage content is authored in
-- challenges/distributed-lock/ at the repo root — keep the two in sync if
-- either changes (see PLAN.md step 9 notes for why they're duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'a1000000-0000-0000-0000-000000000001',
    'distributed-lock',
    'Build your own Distributed Lock',
    'python',
    'distributed_lock.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a1000000-0000-0000-0000-000000000001',
    1,
    'mutual exclusion',
    '기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다',
    $$from distributed_lock import DistributedLock, LockStore
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
$$
),
(
    'a1000000-0000-0000-0000-000000000001',
    2,
    'lease/TTL 만료',
    'release 없이도 lease가 지나면 락이 풀려야 하는 이유',
    $$from distributed_lock import DistributedLock, LockStore
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
$$
),
(
    'a1000000-0000-0000-0000-000000000001',
    3,
    'fencing token',
    '오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유',
    $$from distributed_lock import DistributedLock, LockStore
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
$$
),
(
    'a1000000-0000-0000-0000-000000000001',
    4,
    '동시성',
    '여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공',
    $$from distributed_lock import DistributedLock, LockStore
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
$$
);
