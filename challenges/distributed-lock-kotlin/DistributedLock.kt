/*
 * SysDrill Build Mode — Build your own Distributed Lock (Kotlin)
 *
 * Implement `LockStore` and `DistributedLock` below across 4 stages (see
 * README.md). Keep the class and member names as-is — the stage tests call
 * them directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/**
 * A minimal shared key -> (owner, expiry, fencing token) store, shared by
 * every DistributedLock constructed with the same LockStore object. Passing
 * the same store to two DistributedLock instances is how the stages simulate
 * "multiple processes/instances talking to the same external lock service
 * (e.g. Redis)" without needing a real one.
 */
class LockStore {
    // TODO(stage 1): set up whatever storage you need.

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    fun tryAcquire(key: String, ownerId: String, leaseSeconds: Double): Long? {
        // TODO(stage 1): if `key` is free, claim it for `ownerId` and return
        // a fencing token. If it's already held by someone whose lease hasn't
        // expired, return null.
        // TODO(stage 2): a lease expires `leaseSeconds` after it was
        // acquired — after that, the key is free again even without tryRelease().
        // TODO(stage 3): each successful acquisition must get a fencing token
        // strictly greater than every token issued before it, even across
        // different owners and even after the key was released/expired.
        TODO("not implemented")
    }

    fun tryRelease(key: String, ownerId: String, token: Long): Boolean {
        // TODO(stage 1): release `key` only if `ownerId`+`token` match the
        // current holder; return whether it actually released anything.
        // TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        // after its lease already expired and someone else acquired the
        // lock) must NOT be able to release the current holder's lock.
        TODO("not implemented")
    }

    fun isLocked(key: String): Boolean {
        // TODO(stage 1): whether `key` is currently held by an unexpired lease.
        TODO("not implemented")
    }
}

class DistributedLock(
    private val key: String,
    // Stage 4: callers may pass a *shared* store.
    private val store: LockStore = LockStore(),
    private val leaseSeconds: Double = 5.0,
) {
    /** Returns a fencing token, or null if the lock wasn't acquired. */
    fun acquire(ownerId: String): Long? {
        // TODO(stage 1): delegate to store.tryAcquire(...).
        // TODO(stage 4): make this safe when called concurrently — only one
        // of many simultaneous callers for the same key may succeed.
        TODO("not implemented")
    }

    fun release(ownerId: String, fencingToken: Long): Boolean {
        // TODO(stage 1): delegate to store.tryRelease(...).
        TODO("not implemented")
    }

    fun isLocked(): Boolean {
        // TODO(stage 1): delegate to store.isLocked(...).
        TODO("not implemented")
    }
}
