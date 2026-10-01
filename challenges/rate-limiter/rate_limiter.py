"""
SysDrill Build Mode — Build your own Rate Limiter

Implement the classes below across 6 stages (see stages/README.md).
Keep the class and method names as-is — the stage tests import this
module directly. Submit by running ./submit.sh once you're ready (see
README.md at the repo root).
"""


class InMemoryStore:
    """A minimal key -> counter store, shared by every RateLimiter
    instance that's constructed with the same InMemoryStore object.
    Passing the same store to two RateLimiter instances is how stage 4
    simulates "multiple instances behind a shared rate-limit store"
    without needing a real network call.
    """

    def __init__(self):
        self._data: dict[str, int] = {}

    def incr(self, key: str) -> int:
        self._data[key] = self._data.get(key, 0) + 1
        return self._data[key]

    def expire(self, key: str, seconds: float) -> None:
        # TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        # Until you do, this does nothing — so a window never ends.
        pass


class FaultyStore:
    """Always raises — stage 5 uses this to simulate the backing store
    (e.g. Redis) being unavailable, so you can test fail_mode."""

    def incr(self, key: str) -> int:
        raise ConnectionError("store unavailable")

    def expire(self, key: str, seconds: float) -> None:
        raise ConnectionError("store unavailable")


class RateLimiter:
    def __init__(
        self,
        capacity: int,
        window_seconds: float = 1.0,
        store=None,
        fail_mode: str = "open",
    ):
        self.capacity = capacity
        self.window_seconds = window_seconds
        # Stage 4: callers may pass a *shared* store.
        self.store = store if store is not None else InMemoryStore()
        self.fail_mode = fail_mode

    def allow(self, key: str) -> bool:
        # Stage 1 — uncomment the four lines below and submit.
        # count = self.store.incr(key)
        # if count == 1:
        #     self.store.expire(key, self.window_seconds)
        # return count <= self.capacity
        # TODO(stage 3): make this safe under concurrent calls.
        # TODO(stage 5): when the store raises, admit if fail_mode == "open",
        # reject if fail_mode == "closed".
        # TODO(stage 6): track allowed/rejected counts for `metrics`.
        raise NotImplementedError

    @property
    def metrics(self) -> dict:
        # TODO(stage 6): return {"allowed": int, "rejected": int, "reject_rate": float}.
        raise NotImplementedError
