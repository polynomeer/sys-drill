from circuit_breaker import CircuitBreaker, CircuitOpenError
try:
    calls = {"count": 0}

    def flaky():
        calls["count"] += 1
        raise ValueError("boom")

    cb = CircuitBreaker(failure_threshold=3, recovery_timeout=10.0)
    for _ in range(3):
        try:
            cb.call(flaky)
        except ValueError:
            pass
    assert cb.state == "OPEN", f"expected OPEN after 3 failures, got {cb.state}"
    assert calls["count"] == 3

    try:
        cb.call(flaky)
        assert False, "expected CircuitOpenError while OPEN"
    except CircuitOpenError:
        pass
    assert calls["count"] == 3, "the underlying function must not run while the circuit is OPEN (fail fast)"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
