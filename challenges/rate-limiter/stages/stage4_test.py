"""Stage 4 — shared ("distributed") store.
학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가
따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
"""
from rate_limiter import InMemoryStore, RateLimiter


def main():
    shared_store = InMemoryStore()
    instance_a = RateLimiter(capacity=5, window_seconds=5.0, store=shared_store)
    instance_b = RateLimiter(capacity=5, window_seconds=5.0, store=shared_store)
    total_allowed = 0
    for i in range(10):
        limiter = instance_a if i % 2 == 0 else instance_b
        if limiter.allow("shared-key"):
            total_allowed += 1
    assert total_allowed == 5, (
        f"two instances sharing a store should still cap at 5 total, got {total_allowed}"
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
