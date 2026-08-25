from retry_backoff import RetryPolicy, RetryExhaustedError
try:
    recorded_delays = []

    def always_fail():
        raise ValueError("boom")

    policy = RetryPolicy(max_attempts=6, base_delay=0.01, max_delay=10.0, sleep_fn=lambda d: recorded_delays.append(d))
    try:
        policy.execute(always_fail)
    except RetryExhaustedError:
        pass

    assert len(recorded_delays) == 5, f"expected 5 delays between 6 attempts, got {len(recorded_delays)}"
    for i, d in enumerate(recorded_delays):
        cap = min(10.0, 0.01 * (2 ** i))
        assert 0 <= d <= cap, f"delay {i} = {d} should be within [0, {cap}] (exponential backoff cap)"
    assert len(set(recorded_delays)) > 1, "jitter should make delays vary, not all be identical"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
