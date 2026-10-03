"""Stage 4 — invalidation + hit ratio.
학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
"""
from cache import Cache


def main():
    c = Cache(capacity=10)
    c.set("p1", "price=100", ttl=60)
    assert c.get("p1") == "price=100"  # hit
    c.invalidate("p1")  # the price changed in the DB
    assert c.get("p1") is None, "an invalidated key should miss"  # miss
    assert c.get("p2") is None  # miss
    c.set("p1", "price=120", ttl=60)
    assert c.get("p1") == "price=120", "after invalidation, the new value should be served"  # hit
    c.invalidate("never-set")  # must not raise

    s = c.stats()
    assert s["hits"] == 2, f"expected 2 hits, got {s['hits']}"
    assert s["misses"] == 2, f"expected 2 misses, got {s['misses']}"
    assert abs(s["hit_ratio"] - 0.5) < 0.01, f"expected hit_ratio 0.5, got {s['hit_ratio']}"


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
