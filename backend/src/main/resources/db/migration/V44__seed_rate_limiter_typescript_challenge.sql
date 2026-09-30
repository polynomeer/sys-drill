-- Adds TypeScript as a second language for the "rate-limiter" Build Mode
-- challenge (ROADMAP-adjacent request: Build 챌린지 다중 언어 지원, 우선
-- rate-limiter 하나만). Stage content is authored in
-- challenges/rate-limiter-ts/ at the repo root — keep the two in sync if
-- either changes (same convention V9's rate-limiter seed already established).
--
-- The sandbox runs `.ts` files directly via `node --experimental-strip-types`
-- (type-stripping only, not a full transpile) — see
-- challenges/rate-limiter-ts/README.md for the syntax constraints that follow
-- from that. Stage 3 (concurrency) is deliberately NOT a literal port of the
-- Python version's OS-thread test — Node is single-threaded, so it uses
-- `Promise.all` over many concurrent `allow()` calls against a provided
-- `InMemoryStore` whose `incr()` is intentionally non-atomic (a genuine async
-- gap simulating a real store round trip), which does create a real,
-- verified race for a naive (unlocked) `allow()` implementation.

insert into build_challenges (id, slug, title, languages, source_file_name)
values (
    'b0000000-0000-0000-0000-000000000002',
    'rate-limiter-ts',
    'Build your own Rate Limiter (TypeScript)',
    'typescript',
    'rate_limiter.ts'
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'b0000000-0000-0000-0000-000000000002',
    1,
    '단일 프로세스 fixed window',
    '경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청)',
    $$// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(3, 10.0);
  const results: boolean[] = [];
  for (let i = 0; i < 5; i++) results.push(await rl.allow("user-a"));
  const allowed = results.filter((r) => r).length;
  if (allowed !== 3) throw new Error(`expected exactly 3 allowed within the window, got ${allowed}`);
  if ((await rl.allow("user-b")) !== true) {
    throw new Error("a different key should not be affected by user-a's budget");
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
),
(
    'b0000000-0000-0000-0000-000000000002',
    2,
    '윈도우 회복 (sliding/token bucket 감각)',
    '정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가)',
    $$// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
import { RateLimiter } from "./rate_limiter.ts";

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main(): Promise<void> {
  const rl = new RateLimiter(2, 0.5);
  if ((await rl.allow("k")) !== true) throw new Error("expected the first request to be allowed");
  if ((await rl.allow("k")) !== true) throw new Error("expected the second request to be allowed");
  if ((await rl.allow("k")) !== false) throw new Error("third request within the window should be rejected");
  await sleep(700);
  if ((await rl.allow("k")) !== true) throw new Error("after the window elapses, capacity should replenish");
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
),
(
    'b0000000-0000-0000-0000-000000000002',
    3,
    '동시성 안전성 (async 인터리빙)',
    'atomicity (Node는 싱글스레드라 Promise.all로 수백 개의 allow()를 동시에 날려 async 인터리빙 경쟁을 만든다)',
    $$// Stage 3 — concurrency safety.
// 학습 포인트: atomicity — Node는 싱글스레드라 Python의 OS 스레드 경쟁을 그대로
// 옮길 수 없다. 대신 async 호출을 대량으로 동시에 날려(Promise.all) 인터리빙
// 경쟁을 만든다 — rate_limiter.ts가 제공하는 InMemoryStore.incr()는 일부러
// 원자적이지 않으므로(내부에 실제 네트워크 I/O를 흉내낸 await 지점이 있음),
// allow()에 자체 동시성 제어가 없으면 capacity를 넘기게 된다.
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(50, 5.0);
  const calls: Promise<boolean>[] = [];
  for (let i = 0; i < 200; i++) calls.push(rl.allow("shared-key"));
  await Promise.all(calls);
  if (rl.metrics.allowed > 50) {
    throw new Error(`concurrent access let ${rl.metrics.allowed} requests through, expected <= 50`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
),
(
    'b0000000-0000-0000-0000-000000000002',
    4,
    '공유("분산") 스토어',
    '네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가 따로 놀아서 총 허용량이 커진다)',
    $$// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
import { InMemoryStore, RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const sharedStore = new InMemoryStore();
  const instanceA = new RateLimiter(5, 5.0, sharedStore);
  const instanceB = new RateLimiter(5, 5.0, sharedStore);
  let totalAllowed = 0;
  for (let i = 0; i < 10; i++) {
    const limiter = i % 2 === 0 ? instanceA : instanceB;
    if (await limiter.allow("shared-key")) totalAllowed++;
  }
  if (totalAllowed !== 5) {
    throw new Error(`two instances sharing a store should still cap at 5 total, got ${totalAllowed}`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
),
(
    'b0000000-0000-0000-0000-000000000002',
    5,
    'fail-open / fail-closed',
    '가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는 설계 선택이다)',
    $$// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
import { FaultyStore, RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const openLimiter = new RateLimiter(1, 1.0, new FaultyStore(), "open");
  if ((await openLimiter.allow("k")) !== true) {
    throw new Error("failMode=open should admit requests when the store is unavailable");
  }

  const closedLimiter = new RateLimiter(1, 1.0, new FaultyStore(), "closed");
  if ((await closedLimiter.allow("k")) !== false) {
    throw new Error("failMode=closed should reject requests when the store is unavailable");
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
),
(
    'b0000000-0000-0000-0000-000000000002',
    6,
    '운영 metric',
    'reject rate, latency, key skew 같은 운영 지표가 있어야 실제로 튜닝하고 대응할 수 있다',
    $$// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(2, 5.0);
  await rl.allow("k");
  await rl.allow("k");
  await rl.allow("k");
  const m = rl.metrics;
  if (m.allowed !== 2) throw new Error(`expected 2 allowed, got ${m.allowed}`);
  if (m.rejected !== 1) throw new Error(`expected 1 rejected, got ${m.rejected}`);
  if (Math.abs(m.rejectRate - 1 / 3) >= 0.01) {
    throw new Error(`expected rejectRate ~0.333, got ${m.rejectRate}`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
$$
);
