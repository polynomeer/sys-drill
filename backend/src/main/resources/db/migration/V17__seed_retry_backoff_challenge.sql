-- Seeds the "Build your own Retry/Backoff Middleware" challenge
-- (docs/ROADMAP.md Phase 2 Build 과제 확장, PLAN.md step 16). Stage content
-- is authored in challenges/retry-backoff/ at the repo root — keep the two
-- in sync if either changes (see PLAN.md step 9 notes for why they're
-- duplicated).

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'a2000000-0000-0000-0000-000000000001',
    'retry-backoff',
    'Build your own Retry/Backoff Middleware',
    'python',
    'retry_backoff.py'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a2000000-0000-0000-0000-000000000001',
    1,
    '기본 재시도 동작',
    '실패 시 재시도, 성공하면 즉시 반환',
    $$from retry_backoff import RetryPolicy
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
$$
),
(
    'a2000000-0000-0000-0000-000000000001',
    2,
    '재시도 소진',
    'max_attempts를 넘기면 RetryExhaustedError, 그 이상 시도하지 않음',
    $$from retry_backoff import RetryPolicy, RetryExhaustedError
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
$$
),
(
    'a2000000-0000-0000-0000-000000000001',
    3,
    'exponential backoff + jitter',
    '지수적으로 커지는 대기 시간과 thundering herd를 막는 지터',
    $$from retry_backoff import RetryPolicy, RetryExhaustedError
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
$$
),
(
    'a2000000-0000-0000-0000-000000000001',
    4,
    'retry budget',
    '여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제',
    $$from retry_backoff import RetryPolicy, RetryBudget, RetryExhaustedError
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
$$
);
