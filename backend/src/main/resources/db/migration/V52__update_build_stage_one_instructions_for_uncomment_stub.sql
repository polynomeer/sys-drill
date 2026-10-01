-- docs/CODECRAFTERS_BENCHMARK.md §3.2 (PLAN.md Round B11) — 1단계를 "주석을 풀고 제출"로 바꾼
-- 새 스텁(challenges/rate-limiter*/rate_limiter.*, frontend/src/app/bridge/page.tsx)에 맞춰
-- 1·2단계 지시문을 갱신한다. 테스트 스크립트는 그대로다 — 바뀐 것은 스텁이 미리 채워 둔 범위뿐.
-- 이미 적용된 V49는 고치지 않는다.

update build_stages set instructions = '목표: 키마다 윈도우(window_seconds) 안에서 최대 capacity개 요청만 True를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: RateLimiter(capacity=3, window_seconds=10) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
할 일: 생성자와 InMemoryStore.incr는 이미 채워져 있습니다. allow() 안의 주석 처리된 네 줄(count = … 부터 return … 까지)의 주석을 풀고 제출하세요. 그 세 줄이 무엇을 하는지 읽어 두면 다음 단계가 쉬워집니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 1;
update build_stages set instructions = '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, window_seconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: 지금 InMemoryStore.expire는 아무것도 하지 않아서 윈도우가 끝나지 않습니다. 키별 만료 시각을 저장해 두고, incr 때 그 시각이 지났으면 카운터를 0부터 다시 세세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 2;
update build_stages set instructions = '목표: 키마다 윈도우(windowSeconds) 안에서 최대 capacity개 요청만 true를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: new RateLimiter(3, 10) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
할 일: 생성자와 InMemoryStore는 이미 채워져 있습니다. allow() 안의 주석 처리된 세 줄의 주석을 풀고 제출하세요. 그 세 줄이 무엇을 하는지 읽어 두면 다음 단계가 쉬워집니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 1;
update build_stages set instructions = '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, windowSeconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
참고: TypeScript판은 제공된 InMemoryStore.expire가 이미 타이머로 키를 지워 주므로, 1단계를 통과하면 이 단계도 함께 통과합니다. Python판에서는 이 만료를 직접 구현하는 것이 2단계의 과제입니다 — 위 구현이 왜 윈도우를 끝내는지 확인하고 넘어가세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 2;
