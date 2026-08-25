from retry_backoff import RetryPolicy, RetryBudget, RetryExhaustedError
try:
    budget = RetryBudget(capacity=2)

    attempts_p1 = {"count": 0}

    def always_fail_p1():
        attempts_p1["count"] += 1
        raise ValueError("boom")

    policy1 = RetryPolicy(max_attempts=10, base_delay=0.001, budget=budget, sleep_fn=lambda d: None)
    try:
        policy1.execute(always_fail_p1)
    except RetryExhaustedError:
        pass
    assert attempts_p1["count"] < 10, "a shared retry budget should cut retries short before max_attempts is reached"

    attempts_p2 = {"count": 0}

    def always_fail_p2():
        attempts_p2["count"] += 1
        raise ValueError("boom")

    policy2 = RetryPolicy(max_attempts=10, base_delay=0.001, budget=budget, sleep_fn=lambda d: None)
    try:
        policy2.execute(always_fail_p2)
    except RetryExhaustedError:
        pass
    assert attempts_p2["count"] <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
