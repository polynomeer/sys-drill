/*
 * SysDrill Build Mode — Build your own Distributed Lock (Java)
 *
 * Implement `LockStore` and `DistributedLock` below across 4 stages (see
 * README.md). Keep the class and method names as-is — the stage tests call
 * them directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/**
 * A minimal shared key -> (owner, expiry, fencing token) store, shared by
 * every DistributedLock constructed with the same LockStore object. Passing
 * the same store to two DistributedLock instances is how the stages simulate
 * "multiple processes/instances talking to the same external lock service
 * (e.g. Redis)" without needing a real one.
 */
class LockStore {
    public LockStore() {
        // TODO(stage 1): set up whatever storage you need.
    }

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public Long tryAcquire(String key, String ownerId, double leaseSeconds) {
        // TODO(stage 1): if `key` is free, claim it for `ownerId` and return
        // a fencing token. If it's already held by someone whose lease hasn't
        // expired, return null.
        // TODO(stage 2): a lease expires `leaseSeconds` after it was
        // acquired — after that, the key is free again even without tryRelease().
        // TODO(stage 3): each successful acquisition must get a fencing token
        // strictly greater than every token issued before it, even across
        // different owners and even after the key was released/expired.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean tryRelease(String key, String ownerId, long token) {
        // TODO(stage 1): release `key` only if `ownerId`+`token` match the
        // current holder; return whether it actually released anything.
        // TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        // after its lease already expired and someone else acquired the
        // lock) must NOT be able to release the current holder's lock.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isLocked(String key) {
        // TODO(stage 1): whether `key` is currently held by an unexpired lease.
        throw new UnsupportedOperationException("not implemented");
    }
}

public class DistributedLock {
    private final String key;
    private final LockStore store;
    private final double leaseSeconds;

    public DistributedLock(String key) {
        this(key, new LockStore(), 5.0);
    }

    /** Stage 4: callers may pass a *shared* store. */
    public DistributedLock(String key, LockStore store) {
        this(key, store, 5.0);
    }

    public DistributedLock(String key, LockStore store, double leaseSeconds) {
        this.key = key;
        this.store = store;
        this.leaseSeconds = leaseSeconds;
    }

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public Long acquire(String ownerId) {
        // TODO(stage 1): delegate to store.tryAcquire(...).
        // TODO(stage 4): make this safe when called concurrently — only one
        // of many simultaneous callers for the same key may succeed.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean release(String ownerId, long fencingToken) {
        // TODO(stage 1): delegate to store.tryRelease(...).
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isLocked() {
        // TODO(stage 1): delegate to store.isLocked(...).
        throw new UnsupportedOperationException("not implemented");
    }
}
