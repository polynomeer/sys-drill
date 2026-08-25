"""Stage 5 — fail-open vs fail-closed.
학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
"""
from rate_limiter import FaultyStore, RateLimiter


def main():
    open_limiter = RateLimiter(capacity=1, store=FaultyStore(), fail_mode="open")
    assert open_limiter.allow("k") is True, (
        "fail_mode=open should admit requests when the store is unavailable"
    )

    closed_limiter = RateLimiter(capacity=1, store=FaultyStore(), fail_mode="closed")
    assert closed_limiter.allow("k") is False, (
        "fail_mode=closed should reject requests when the store is unavailable"
    )


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
