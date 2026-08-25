-- Seeds the "Build your own Circuit Breaker" challenge (docs/PRD.md §7.1 /
-- docs/ROADMAP.md Phase 2 Build 과제 확장, PLAN.md step 14). Stage content is
-- authored in challenges/circuit-breaker/ at the repo root — keep the two in
-- sync if either changes (see PLAN.md step 9 notes for why they're duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'f0000000-0000-0000-0000-000000000001',
    'circuit-breaker',
    'Build your own Circuit Breaker',
    'python',
    'circuit_breaker.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'f0000000-0000-0000-0000-000000000001',
    1,
    '정상 동작 (CLOSED)',
    'pass-through 기본 동작 — 호출이 성공하는 동안 breaker는 CLOSED를 유지하고 결과를 그대로 반환한다',
    $$from circuit_breaker import CircuitBreaker
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
$$
),
(
    'f0000000-0000-0000-0000-000000000001',
    2,
    'failure threshold 도달 시 OPEN',
    'fail fast — OPEN 상태에서는 실제 함수를 호출하지 않고 즉시 CircuitOpenError를 던진다',
    $$from circuit_breaker import CircuitBreaker, CircuitOpenError
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
$$
),
(
    'f0000000-0000-0000-0000-000000000001',
    3,
    'recovery timeout 경과 후 HALF_OPEN 복구',
    '언제, 어떻게 재시도를 허용할지 — recovery_timeout이 지나면 HALF_OPEN으로 전이하고, 시도가 성공하면 CLOSED로 복구한다',
    $$from circuit_breaker import CircuitBreaker
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
$$
),
(
    'f0000000-0000-0000-0000-000000000001',
    4,
    'HALF_OPEN 시도 실패 시 재차단',
    '복구 판단이 틀렸을 때의 대응 — 시도 호출이 실패하면 다시 OPEN으로 돌아가고 recovery_timeout이 그 시점부터 재시작된다',
    $$from circuit_breaker import CircuitBreaker, CircuitOpenError
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
$$
);
