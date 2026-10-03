from retry_backoff import RetryPolicy, RetryExhaustedError


def record_delays():
    """One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts."""
    recorded_delays = []

    def always_fail():
        raise ValueError("boom")

    policy = RetryPolicy(max_attempts=6, base_delay=0.01, max_delay=10.0, sleep_fn=lambda d: recorded_delays.append(d))
    try:
        policy.execute(always_fail)
    except RetryExhaustedError:
        pass
    return recorded_delays


try:
    first = record_delays()
    second = record_delays()
    for recorded_delays in (first, second):
        assert len(recorded_delays) == 5, f"expected 5 delays between 6 attempts, got {len(recorded_delays)}"
        for i, d in enumerate(recorded_delays):
            cap = min(10.0, 0.01 * (2 ** i))
            assert 0 <= d <= cap, f"delay {i} = {d} should be within [0, {cap}] (exponential backoff cap)"
    # Plain exponential delays (10ms, 20ms, 40ms, ...) already all differ from each other, so
    # "they vary" proves nothing — jitter means two runs don't wait the same amounts.
    assert first != second, f"jitter should randomize the delays, but two runs waited exactly the same: {first}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
