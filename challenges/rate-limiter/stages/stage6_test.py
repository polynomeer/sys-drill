"""Stage 6 — operational metrics.
학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
튜닝하고 대응할 수 있다.
"""
from rate_limiter import RateLimiter


def main():
    rl = RateLimiter(capacity=2, window_seconds=5.0)
    rl.allow("k")
    rl.allow("k")
    rl.allow("k")
    m = rl.metrics
    assert m["allowed"] == 2, f"expected 2 allowed, got {m['allowed']}"
    assert m["rejected"] == 1, f"expected 1 rejected, got {m['rejected']}"
    assert abs(m["reject_rate"] - (1 / 3)) < 0.01, f"expected reject_rate ~0.333, got {m['reject_rate']}"


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
