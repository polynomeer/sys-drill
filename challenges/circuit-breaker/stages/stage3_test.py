from circuit_breaker import CircuitBreaker
import time
try:
    def fail():
        raise ValueError("boom")

    cb = CircuitBreaker(failure_threshold=1, recovery_timeout=0.3)
    try:
        cb.call(fail)
    except ValueError:
        pass
    assert cb.state == "OPEN"

    time.sleep(0.4)
    assert cb.state == "HALF_OPEN", f"expected HALF_OPEN after recovery_timeout elapsed, got {cb.state}"

    result = cb.call(lambda: "recovered")
    assert result == "recovered"
    assert cb.state == "CLOSED", f"a successful HALF_OPEN trial should recover to CLOSED, got {cb.state}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
