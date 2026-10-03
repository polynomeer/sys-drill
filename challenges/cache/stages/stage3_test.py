"""Stage 3 — single-flight (cache stampede).
학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
"""
import threading
import time
from cache import Cache


def main():
    c = Cache(capacity=10)
    loads = []
    loads_lock = threading.Lock()

    def slow_loader():
        with loads_lock:
            loads.append(1)
        time.sleep(0.2)  # a slow DB query
        return "product-42"

    results = []
    errors = []
    start_gate = threading.Barrier(8)

    def reader():
        try:
            start_gate.wait()
            results.append(c.get_or_load("hot", slow_loader, ttl=5.0))
        except Exception as e:  # a crash in a worker thread would otherwise vanish silently
            errors.append(e)

    threads = [threading.Thread(target=reader) for _ in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    if errors:
        raise errors[0]

    assert len(loads) == 1, f"8 concurrent misses on the same key should trigger exactly 1 load, got {len(loads)}"
    assert results == ["product-42"] * 8, f"every caller should get the loaded value, got {results}"
    assert c.get_or_load("hot", slow_loader, ttl=5.0) == "product-42"
    assert len(loads) == 1, "once loaded, the value should come from the cache, not the loader"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
