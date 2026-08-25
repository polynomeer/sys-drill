"""Stage 2 — window replenishment (sliding/token-bucket-style behavior).
학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
"""
import time
from rate_limiter import RateLimiter


def main():
    rl = RateLimiter(capacity=2, window_seconds=0.5)
    assert rl.allow("k") is True
    assert rl.allow("k") is True
    assert rl.allow("k") is False, "third request within the window should be rejected"
    time.sleep(0.7)
    assert rl.allow("k") is True, "after the window elapses, capacity should replenish"


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
