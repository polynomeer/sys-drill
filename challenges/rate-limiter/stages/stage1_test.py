"""Stage 1 — single-process fixed window.
학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
"""
from rate_limiter import RateLimiter


def main():
    rl = RateLimiter(capacity=3, window_seconds=10.0)
    results = [rl.allow("user-a") for _ in range(5)]
    allowed = sum(1 for r in results if r)
    assert allowed == 3, f"expected exactly 3 allowed within the window, got {allowed}"
    assert rl.allow("user-b") is True, "a different key should not be affected by user-a's budget"


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
