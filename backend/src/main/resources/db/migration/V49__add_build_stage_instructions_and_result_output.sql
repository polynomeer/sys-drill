-- docs/CODECRAFTERS_BENCHMARK.md §3.2·§3.3 (PLAN.md Round B6) — Build 단계별 진행과 테스트 로그.
--
-- build_stages.instructions: 스테이지 지시문(목표·테스트가 확인하는 것·힌트). 제출 전에
-- 현재 스테이지 지시문을 보여주기 위한 것 — 지금까지는 한 줄짜리 spec(학습 포인트)뿐이었다.
-- 콘텐츠는 마이그레이션으로 관리한다(ADR-0002). 지시문이 없는 챌린지는 화면이 spec으로 대신한다.
--
-- build_stage_results.output / duration_ms: 샌드박스 원본 출력과 소요 시간. 지금까지는 실패
-- 사유 한 줄만 feedback으로 뽑고 원본을 버려서 테스트 로그 패널에 보여줄 게 없었다.
-- 출력은 워커가 상한(BuildRunnerWorker.MAX_STORED_OUTPUT_CHARS)까지만 저장한다.

alter table build_stages add column instructions text;
alter table build_stage_results add column output text;
alter table build_stage_results add column duration_ms integer;


-- rate-limiter (Python)
update build_stages set instructions = '목표: 키마다 윈도우(window_seconds) 안에서 최대 capacity개 요청만 True를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: RateLimiter(capacity=3, window_seconds=10) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
힌트: InMemoryStore.incr / expire 부터 채우세요. count = store.incr(key) 가 1이면 그 키에 expire(key, window_seconds)를 걸고, count <= capacity 이면 허용합니다. store 인자가 None이면 InMemoryStore()를 기본값으로 씁니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 1;
update build_stages set instructions = '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, window_seconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: expire가 실제로 카운터를 0으로 되돌리는지 확인하세요 — 만료 시각을 저장해 두고 incr 때 지났으면 리셋하는 방식이면 충분합니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 2;
update build_stages set instructions = '목표: 여러 스레드가 동시에 allow()를 불러도 capacity를 넘기지 않게 합니다.
테스트가 확인하는 것: capacity=50 리미터에 10개 스레드가 20번씩(총 200번) 호출한 뒤 metrics["allowed"] <= 50 이어야 합니다.
주의: 이 테스트는 metrics["allowed"]를 읽습니다 — 6단계의 metrics 중 allowed 카운트는 여기서 미리 만들어 두세요.
힌트: "읽고-더하고-비교하기"가 한 번에 일어나도록 threading.Lock으로 감싸세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 3;
update build_stages set instructions = '목표: 여러 리미터 인스턴스가 같은 store를 공유하면 총 허용량도 공유되게 합니다(서버 여러 대 + Redis 상황).
테스트가 확인하는 것: InMemoryStore 하나를 공유하는 capacity=5 리미터 두 개에 번갈아 10번 요청하면 합쳐서 정확히 5번만 허용되어야 합니다.
힌트: 카운트를 인스턴스 필드가 아니라 store에 두어야 합니다. 락도 인스턴스마다 따로면 공유 store를 지키지 못합니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 4;
update build_stages set instructions = '목표: store(예: Redis)가 죽었을 때의 동작을 fail_mode로 고를 수 있게 합니다.
테스트가 확인하는 것: FaultyStore(항상 ConnectionError)를 쓸 때 fail_mode="open"이면 허용(True), fail_mode="closed"면 거절(False)해야 합니다.
힌트: store 호출을 try/except로 감싸세요. 어느 쪽이 맞는지는 서비스의 선택입니다 — 가용성(open)과 보호(closed)의 trade-off를 설계 단계에서도 다시 만나게 됩니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 5;
update build_stages set instructions = '목표: 운영자가 튜닝할 수 있도록 지표를 노출합니다.
테스트가 확인하는 것: capacity=2 리미터에 3번 요청한 뒤 metrics가 {"allowed": 2, "rejected": 1, "reject_rate": 약 0.333} 이어야 합니다.
힌트: allow()가 결정할 때마다 allowed/rejected를 세고, reject_rate = rejected / (allowed + rejected) (요청이 0개면 0.0)로 계산하세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000001' and stage_order = 6;

-- rate-limiter-ts (TypeScript)
update build_stages set instructions = '목표: 키마다 윈도우(windowSeconds) 안에서 최대 capacity개 요청만 true를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: new RateLimiter(3, 10) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
힌트: InMemoryStore.incr / expire 부터 채우세요. count = await this.store.incr(key) 가 1이면 await this.store.expire(key, this.windowSeconds)를 걸고, count <= capacity 이면 허용합니다. store 인자가 없으면 new InMemoryStore()를 기본값으로 씁니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 1;
update build_stages set instructions = '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, windowSeconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: expire가 실제로 카운터를 0으로 되돌리는지 확인하세요 — 만료 시각을 저장해 두고 incr 때 지났으면 리셋하는 방식이면 충분합니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 2;
update build_stages set instructions = '목표: allow()를 대량으로 동시에 호출해도 capacity를 넘기지 않게 합니다.
테스트가 확인하는 것: capacity=50 리미터에 allow()를 200번 동시에 날린 뒤(Promise.all) metrics.allowed <= 50 이어야 합니다.
주의: 이 테스트는 metrics.allowed를 읽습니다 — 6단계의 metrics 중 allowed 카운트는 여기서 미리 만들어 두세요.
힌트: Node는 싱글스레드지만 InMemoryStore.incr()는 내부에 await 지점이 있어 원자적이지 않습니다. 키별로 이전 호출의 Promise에 이어 붙이는 간단한 뮤텍스(Promise 체인)로 allow()를 직렬화하세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 3;
update build_stages set instructions = '목표: 여러 리미터 인스턴스가 같은 store를 공유하면 총 허용량도 공유되게 합니다(서버 여러 대 + Redis 상황).
테스트가 확인하는 것: InMemoryStore 하나를 공유하는 capacity=5 리미터 두 개에 번갈아 10번 요청하면 합쳐서 정확히 5번만 허용되어야 합니다.
힌트: 카운트를 인스턴스 필드가 아니라 store에 두어야 합니다. 락도 인스턴스마다 따로면 공유 store를 지키지 못합니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 4;
update build_stages set instructions = '목표: store(예: Redis)가 죽었을 때의 동작을 failMode로 고를 수 있게 합니다.
테스트가 확인하는 것: FaultyStore(항상 reject되는 Promise)를 쓸 때 failMode="open"이면 허용(true), failMode="closed"면 거절(false)해야 합니다.
힌트: store 호출을 try/catch로 감싸세요. 어느 쪽이 맞는지는 서비스의 선택입니다 — 가용성(open)과 보호(closed)의 trade-off를 설계 단계에서도 다시 만나게 됩니다.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 5;
update build_stages set instructions = '목표: 운영자가 튜닝할 수 있도록 지표를 노출합니다.
테스트가 확인하는 것: capacity=2 리미터에 3번 요청한 뒤 metrics가 {"allowed": 2, "rejected": 1, "rejectRate": 약 0.333} 이어야 합니다.
힌트: allow()가 결정할 때마다 allowed/rejected를 세고, rejectRate = rejected / (allowed + rejected) (요청이 0개면 0.0)로 계산하세요.'
where challenge_id = 'b0000000-0000-0000-0000-000000000002' and stage_order = 6;
