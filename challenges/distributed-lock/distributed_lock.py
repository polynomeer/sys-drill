"""
SysDrill Build Mode — Build your own Distributed Lock

Implement the classes below across 4 stages (see README.md). Keep the
class/method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class LockStore:
    """A minimal shared key -> (owner, expiry, fencing token) store, shared
    by every DistributedLock instance constructed with the same LockStore
    object. Passing the same store to two DistributedLock instances is how
    the stages simulate "multiple processes/instances talking to the same
    external lock service (e.g. Redis)" without needing a real one.
    """

    def __init__(self):
        # TODO(stage 1): set up whatever storage you need.
        raise NotImplementedError

    def try_acquire(self, key: str, owner_id: str, lease_seconds: float):
        # TODO(stage 1): if `key` is free, claim it for `owner_id` and return
        # a fencing token (an int). If it's already held by someone whose
        # lease hasn't expired, return None.
        # TODO(stage 2): a lease expires `lease_seconds` after it was
        # acquired — after that, the key is free again even without release().
        # TODO(stage 3): each successful acquisition must get a fencing token
        # strictly greater than every token issued before it, even across
        # different owners and even after the key was released/expired.
        raise NotImplementedError

    def try_release(self, key: str, owner_id: str, token: int) -> bool:
        # TODO(stage 1): release `key` only if `owner_id`+`token` match the
        # current holder; return whether it actually released anything.
        # TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        # after its lease already expired and someone else acquired the
        # lock) must NOT be able to release the current holder's lock.
        raise NotImplementedError

    def is_locked(self, key: str) -> bool:
        # TODO(stage 1): whether `key` is currently held by an unexpired lease.
        raise NotImplementedError


class DistributedLock:
    def __init__(self, key: str, store: LockStore | None = None, lease_seconds: float = 5.0):
        # TODO(stage 1): store config. Default to LockStore() if `store` is
        # None (stage 4: callers may pass a *shared* store).
        raise NotImplementedError

    def acquire(self, owner_id: str):
        # TODO(stage 1): delegate to self.store.try_acquire(...).
        # TODO(stage 4): make this safe when called concurrently — only one
        # of many simultaneous callers for the same key may succeed.
        raise NotImplementedError

    def release(self, owner_id: str, fencing_token: int) -> bool:
        # TODO(stage 1): delegate to self.store.try_release(...).
        raise NotImplementedError

    def is_locked(self) -> bool:
        # TODO(stage 1): delegate to self.store.is_locked(...).
        raise NotImplementedError
