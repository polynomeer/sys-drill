-- Seeds the "선착순 쿠폰" scenario's Build Mode counterpart from docs/PRD.md
-- §7.1 ("Build your own Rate Limiter"). Stage content is authored in
-- challenges/rate-limiter/ at the repo root — keep the two in sync if either
-- changes (see PLAN.md step 9 notes for why they're duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'b0000000-0000-0000-0000-000000000001',
    'rate-limiter',
    'Build your own Rate Limiter',
    'python',
    'rate_limiter.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'b0000000-0000-0000-0000-000000000001',
    1,
    '단일 프로세스 fixed window',
    '경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청)',
    $$"""Stage 1 — single-process fixed window."""
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
$$
),
(
    'b0000000-0000-0000-0000-000000000001',
    2,
    '윈도우 회복 (sliding/token bucket 감각)',
    '정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가)',
    $$"""Stage 2 — window replenishment (sliding/token-bucket-style behavior)."""
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
$$
),
(
    'b0000000-0000-0000-0000-000000000001',
    3,
    '동시성 안전성',
    'atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다)',
    $$"""Stage 3 — concurrency safety."""
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
$$
),
(
    'b0000000-0000-0000-0000-000000000001',
    4,
    '공유("분산") 스토어',
    '네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가 따로 놀아서 총 허용량이 커진다)',
    $$"""Stage 4 — shared ("distributed") store."""
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
$$
),
(
    'b0000000-0000-0000-0000-000000000001',
    5,
    'fail-open / fail-closed',
    '가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는 설계 선택이다)',
    $$"""Stage 5 — fail-open vs fail-closed."""
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
$$
),
(
    'b0000000-0000-0000-0000-000000000001',
    6,
    '운영 metric',
    'reject rate, latency, key skew 같은 운영 지표가 있어야 실제로 튜닝하고 대응할 수 있다',
    $$"""Stage 6 — operational metrics."""
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
$$
);
