"""Stage 1 — TTL get/set.
학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
"""
import time
from cache import Cache


def main():
    c = Cache(capacity=10)
    assert c.get("missing") is None, "a key that was never set should be a miss (None)"
    c.set("p1", "price=100", ttl=0.3)
    assert c.get("p1") == "price=100", f"expected price=100 before the TTL, got {c.get('p1')}"
    c.set("p2", "price=200", ttl=5.0)
    time.sleep(0.4)
    assert c.get("p1") is None, "p1 should have expired after its 0.3s TTL"
    assert c.get("p2") == "price=200", "p2 has a 5s TTL and should still be cached"


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
