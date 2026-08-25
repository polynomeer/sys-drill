from circuit_breaker import CircuitBreaker
try:
    cb = CircuitBreaker(failure_threshold=3, recovery_timeout=1.0)
    result = cb.call(lambda: 42)
    assert result == 42, f"expected 42, got {result}"
    assert cb.state == "CLOSED", f"expected CLOSED, got {cb.state}"
    for _ in range(5):
        assert cb.call(lambda: "ok") == "ok"
    assert cb.state == "CLOSED"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
