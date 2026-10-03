"""Model answer for Build your own Idempotency Layer (Python) — used by BuildLanguageVariantsIntegrationTest."""
import threading
import time


class IdempotencyConflictError(Exception):
    pass


class IdempotencyInProgressError(Exception):
    pass


_IN_PROGRESS = object()


class IdempotencyLayer:
    def __init__(self, ttl: float = 86400.0):
        self.ttl = ttl
        self._lock = threading.Lock()
        self._records = {}  # key -> {"request", "result" (or _IN_PROGRESS), "expires_at"}

    def execute(self, key, request, fn):
        with self._lock:
            record = self._records.get(key)
            if record is not None and record["expires_at"] <= time.monotonic():
                del self._records[key]
                record = None
            if record is not None:
                if record["request"] != request:
                    raise IdempotencyConflictError(f"key {key!r} was first used with a different request")
                if record["result"] is _IN_PROGRESS:
                    raise IdempotencyInProgressError(f"a request with key {key!r} is still running")
                return record["result"]
            # claim the key before running fn, so a concurrent duplicate sees it in flight
            self._records[key] = {"request": request, "result": _IN_PROGRESS, "expires_at": float("inf")}
        try:
            result = fn()  # no lock held: a duplicate is rejected, not blocked
        except BaseException:
            with self._lock:
                del self._records[key]  # nothing stored — a retry runs fn again
            raise
        with self._lock:
            self._records[key] = {"request": request, "result": result, "expires_at": time.monotonic() + self.ttl}
        return result
