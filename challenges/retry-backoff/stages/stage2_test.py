from retry_backoff import RetryPolicy, RetryExhaustedError
try:
    attempts = {"count": 0}

    def always_fail():
        attempts["count"] += 1
        raise ValueError("boom")

    policy = RetryPolicy(max_attempts=4, base_delay=0.001, sleep_fn=lambda d: None)
    try:
        policy.execute(always_fail)
        assert False, "expected RetryExhaustedError once max_attempts is exceeded"
    except RetryExhaustedError:
        pass
    assert attempts["count"] == 4, f"expected exactly 4 attempts (max_attempts), got {attempts['count']}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
