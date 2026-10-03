"""
SysDrill Build Mode — Build your own Cache

Implement the Cache class below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class Cache:
    """An in-process read-through cache in front of a slow loader (e.g. the
    product DB): entries expire after a TTL, the least recently used entry
    is evicted once `capacity` is reached, and concurrent misses for the
    same key share a single load instead of all hitting the loader.
    """

    def __init__(self, capacity: int = 100):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def set(self, key: str, value, ttl: float) -> None:
        # TODO(stage 1): store `value` under `key`; it expires `ttl` seconds from now.
        # TODO(stage 2): once more than `capacity` keys are stored, evict the
        # least recently used one (a get() or set() counts as a use).
        raise NotImplementedError

    def get(self, key: str):
        # TODO(stage 1): the value for `key`, or None if it's missing or expired.
        # TODO(stage 4): count every get() as a hit or a miss for stats().
        raise NotImplementedError

    def get_or_load(self, key: str, loader, ttl: float):
        # TODO(stage 3): return the cached value if present. Otherwise call
        # loader() — a slow call such as a DB query — store its result with
        # `ttl`, and return it. When many threads miss the same key at the
        # same time, loader() must run only ONCE; the others wait for that
        # one load and get its result (single-flight — this is what stops a
        # cache stampede from flattening the DB when a hot key expires).
        raise NotImplementedError

    def invalidate(self, key: str) -> None:
        # TODO(stage 4): drop `key` so the next read misses (e.g. after the
        # product's price changed in the DB).
        raise NotImplementedError

    def stats(self) -> dict:
        # TODO(stage 4): {"hits": int, "misses": int, "hit_ratio": float}
        # (hit_ratio is 0.0 when there were no gets yet).
        raise NotImplementedError
