"""Stage 2 — LRU eviction.
학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
"""
from cache import Cache


def main():
    c = Cache(capacity=3)
    c.set("a", 1, ttl=60)
    c.set("b", 2, ttl=60)
    c.set("c", 3, ttl=60)
    assert c.get("a") == 1, "a should still be cached (capacity is 3)"  # a is now the most recently used
    c.set("d", 4, ttl=60)  # over capacity: b is the least recently used
    assert c.get("b") is None, "b was the least recently used key and should have been evicted"
    assert c.get("a") == 1, "a was read just before the insert, so it must survive"
    assert c.get("c") == 3, "c should survive — only one key needed to go"
    assert c.get("d") == 4, "the newly inserted key d should be cached"


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
