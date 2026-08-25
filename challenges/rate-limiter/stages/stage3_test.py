"""Stage 3 — concurrency safety.
학습 포인트: atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다).
"""
import threading
from rate_limiter import RateLimiter


def main():
    rl = RateLimiter(capacity=50, window_seconds=5.0)

    def hammer():
        for _ in range(20):
            rl.allow("shared-key")

    threads = [threading.Thread(target=hammer) for _ in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert rl.metrics["allowed"] <= 50, (
        f"concurrent access let {rl.metrics['allowed']} requests through, expected <= 50"
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
