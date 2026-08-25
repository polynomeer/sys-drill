from circuit_breaker import CircuitBreaker, CircuitOpenError
import time
try:
    def fail():
        raise ValueError("boom")

    cb = CircuitBreaker(failure_threshold=1, recovery_timeout=0.3)
    try:
        cb.call(fail)
    except ValueError:
        pass
    time.sleep(0.4)
    assert cb.state == "HALF_OPEN"

    try:
        cb.call(fail)
    except ValueError:
        pass
    assert cb.state == "OPEN", f"a failed HALF_OPEN trial should return to OPEN, got {cb.state}"

    try:
        cb.call(lambda: "should not run")
        assert False, "expected CircuitOpenError immediately after a failed trial (timeout must reset)"
    except CircuitOpenError:
        pass
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
