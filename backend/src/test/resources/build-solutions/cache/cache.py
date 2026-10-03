"""Model answer for Build your own Cache (Python) — used by BuildLanguageVariantsIntegrationTest."""
import threading
import time
from collections import OrderedDict


class Cache:
    def __init__(self, capacity: int = 100):
        self.capacity = capacity
        self._entries = OrderedDict()  # key -> (value, expires_at); order = recency, oldest first
        self._lock = threading.Lock()
        self._loading = {}  # key -> Event set when that key's in-flight load finishes
        self._hits = 0
        self._misses = 0

    def set(self, key, value, ttl):
        with self._lock:
            self._put(key, value, ttl)

    def get(self, key):
        with self._lock:
            found, value = self._lookup(key)
            if found:
                self._hits += 1
                return value
            self._misses += 1
            return None

    def get_or_load(self, key, loader, ttl):
        while True:
            with self._lock:
                found, value = self._lookup(key)
                if found:
                    return value
                pending = self._loading.get(key)
                if pending is None:
                    pending = self._loading[key] = threading.Event()
                    break  # this caller does the load
            pending.wait()  # someone else is loading — wait, then re-read
        try:
            value = loader()
            with self._lock:
                self._put(key, value, ttl)
            return value
        finally:
            with self._lock:
                del self._loading[key]
            pending.set()

    def invalidate(self, key):
        with self._lock:
            self._entries.pop(key, None)

    def stats(self):
        with self._lock:
            total = self._hits + self._misses
            return {"hits": self._hits, "misses": self._misses, "hit_ratio": self._hits / total if total else 0.0}

    def _lookup(self, key):
        entry = self._entries.get(key)
        if entry is None:
            return False, None
        value, expires_at = entry
        if time.monotonic() >= expires_at:
            del self._entries[key]
            return False, None
        self._entries.move_to_end(key)
        return True, value

    def _put(self, key, value, ttl):
        self._entries[key] = (value, time.monotonic() + ttl)
        self._entries.move_to_end(key)
        while len(self._entries) > self.capacity:
            self._entries.popitem(last=False)
