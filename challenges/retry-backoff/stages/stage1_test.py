from retry_backoff import RetryPolicy
try:
    attempts = {"count": 0}

    def flaky():
        attempts["count"] += 1
        if attempts["count"] < 3:
            raise ValueError("boom")
        return "success"

    policy = RetryPolicy(max_attempts=5, base_delay=0.001, sleep_fn=lambda d: None)
    result = policy.execute(flaky)
    assert result == "success", f"expected success, got {result}"
    assert attempts["count"] == 3, f"expected exactly 3 attempts (2 failures + 1 success), got {attempts['count']}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
