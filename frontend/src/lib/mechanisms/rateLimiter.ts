/**
 * docs/LEARNING_DEEPENING_PLAN.md L17 (PLAN.md Round E41, ADR-0055) — the Rate Limiter mechanism
 * widget's simulation core: pure, deterministic, on a virtual clock (seconds), no I/O.
 *
 * The fixed window here behaves exactly like the Build challenge's (challenges/rate-limiter): a key's
 * window opens at its first request and its counter expires `windowSeconds` later; every request
 * increments the counter and is allowed while the count is within capacity. The scenarios below use
 * the Build stages' own values, and `npm run check:mechanisms` checks both that this core produces
 * each scenario's expected outcome and that those values are in the Build stage tests.
 *
 * Kept free of imports and of TypeScript-only runtime syntax so Node can run it with
 * `--experimental-strip-types` (the check script) as well as Next.
 */

export type Algorithm = "fixed-window" | "sliding-log" | "token-bucket";

export interface RateRequest {
  /** Seconds on the scenario's clock. */
  t: number;
  key: string;
}

export interface RateDecision extends RateRequest {
  allowed: boolean;
  /** What the limiter held for this key right after deciding: the window count, the log size, or tokens left. */
  level: number;
  /** For the fixed window: when this key's current window opened. */
  windowStart?: number;
}

/** Decide every request in time order with one algorithm. Capacity is per key per `windowSeconds`. */
export function simulate(algorithm: Algorithm, capacity: number, windowSeconds: number, requests: RateRequest[]): RateDecision[] {
  const ordered = [...requests].sort((a, b) => a.t - b.t);
  if (algorithm === "fixed-window") {
    const windows = new Map<string, { start: number; count: number }>();
    return ordered.map((r) => {
      let w = windows.get(r.key);
      if (!w || r.t >= w.start + windowSeconds) {
        w = { start: r.t, count: 0 };
        windows.set(r.key, w);
      }
      w.count += 1;
      return { ...r, allowed: w.count <= capacity, level: w.count, windowStart: w.start };
    });
  }
  if (algorithm === "sliding-log") {
    const logs = new Map<string, number[]>();
    return ordered.map((r) => {
      const log = (logs.get(r.key) ?? []).filter((t) => t > r.t - windowSeconds);
      const allowed = log.length < capacity;
      if (allowed) log.push(r.t);
      logs.set(r.key, log);
      return { ...r, allowed, level: log.length };
    });
  }
  // Token bucket: starts full, refills capacity tokens per window, continuously.
  const buckets = new Map<string, { tokens: number; at: number }>();
  const rate = capacity / windowSeconds;
  return ordered.map((r) => {
    const b = buckets.get(r.key) ?? { tokens: capacity, at: r.t };
    const tokens = Math.min(capacity, b.tokens + (r.t - b.at) * rate);
    const allowed = tokens >= 1;
    const left = allowed ? tokens - 1 : tokens;
    buckets.set(r.key, { tokens: left, at: r.t });
    return { ...r, allowed, level: Math.round(left * 100) / 100 };
  });
}

/** The most requests allowed within any `span` seconds — the burst a downstream actually feels. */
export function maxAllowedWithin(decisions: RateDecision[], span: number): number {
  const allowed = decisions.filter((d) => d.allowed).map((d) => d.t);
  let best = 0;
  for (let i = 0; i < allowed.length; i++) {
    let j = i;
    while (j < allowed.length && allowed[j] - allowed[i] < span) j++;
    best = Math.max(best, j - i);
  }
  return best;
}

/**
 * Stage 3's race without real threads: `workers` callers each run read → (round trip) → write+1 on a
 * shared counter, interleaved round-robin — every worker reads before any writes back, the way
 * concurrent calls against a non-atomic store (or Promise.all against the Build's InMemoryStore)
 * interleave. `atomic` makes read+write one step (Redis INCR, a lock).
 */
export function raceAllowed(workers: number, callsPerWorker: number, capacity: number, atomic: boolean): { allowed: number; counter: number } {
  let counter = 0;
  let allowed = 0;
  for (let round = 0; round < callsPerWorker; round++) {
    if (atomic) {
      for (let w = 0; w < workers; w++) {
        counter += 1;
        if (counter <= capacity) allowed++;
      }
    } else {
      const seen = counter; // every worker read the same value…
      for (let w = 0; w < workers; w++) {
        if (seen + 1 <= capacity) allowed++;
      }
      counter = seen + 1; // …and each wrote seen + 1 over the others.
    }
  }
  return { allowed, counter };
}

/** Stage 4: instances behind one shared store count together; each with its own store counts separately. */
export function sharedStoreAllowed(instances: number, requests: number, capacity: number, shared: boolean): number {
  const counts = new Array<number>(shared ? 1 : instances).fill(0);
  let allowed = 0;
  for (let i = 0; i < requests; i++) {
    const slot = shared ? 0 : i % instances;
    counts[slot] += 1;
    if (counts[slot] <= capacity) allowed++;
  }
  return allowed;
}

/** Where a scenario's values come from: a Build stage test and strings that pin them there. */
export interface BuildSource {
  stage: number;
  /** Repo-relative test files and, for each, strings that must appear in it. */
  files: { path: string; contains: string[] }[];
}

export interface RateScenario {
  id: string;
  title: string;
  lesson: string;
  build: BuildSource | null;
}

/** Stage 1 — 5 requests from one key against capacity 3 / 10 s, then another key. */
export const STAGE1 = {
  capacity: 3,
  windowSeconds: 10,
  requests: [
    ...[0, 0.1, 0.2, 0.3, 0.4].map((t) => ({ t, key: "user-a" })),
    { t: 0.5, key: "user-b" },
  ],
  expected: { allowedA: 3, userBAllowed: true },
};

/** Stage 2 — capacity 2 / 0.5 s: two in, the third out, and after 0.7 s it replenishes. */
export const STAGE2 = {
  capacity: 2,
  windowSeconds: 0.5,
  requests: [0, 0.01, 0.02, 0.72].map((t) => ({ t, key: "k" })),
  expected: [true, true, false, true],
};

/** Stage 3 — 200 concurrent calls (Python: 10 threads × 20; TypeScript: Promise.all of 200) against capacity 50. */
export const STAGE3 = { workers: 10, callsPerWorker: 20, capacity: 50, expected: { atomicAllowed: 50 } };

/** Stage 4 — two instances, one shared store, capacity 5, 10 alternating requests → 5 in total. */
export const STAGE4 = { instances: 2, requests: 10, capacity: 5, expected: { sharedAllowed: 5, separateAllowed: 10 } };

/** Stage 5 — the store is down: fail-open admits, fail-closed rejects. */
export const STAGE5 = { expected: { open: true, closed: false } };

/** Stage 6 — capacity 2, three requests: 2 allowed, 1 rejected, reject rate 1/3. */
export const STAGE6 = { capacity: 2, windowSeconds: 5, requests: [0, 0.1, 0.2].map((t) => ({ t, key: "k" })), expected: { allowed: 2, rejected: 1, rejectRate: 1 / 3 } };

/**
 * Stage 1's learning point, the boundary burst — not a Build assertion, the widget's own lesson:
 * one request opens the window at 0 s, two more slip in just before it closes, and three more land
 * just after a new one opens. Same capacity and window as stage 1.
 */
export const BOUNDARY_BURST = {
  capacity: 3,
  windowSeconds: 10,
  requests: [0, 9.7, 9.8, 10.1, 10.2, 10.3].map((t) => ({ t, key: "user-a" })),
  /** Most allowed within any 1 s, per algorithm. */
  expected: { "fixed-window": 5, "sliding-log": 3, "token-bucket": 3 } as Record<Algorithm, number>,
};

/** How the stage tests write a seconds value: `10.0`, `0.5`. */
function sec(n: number): string {
  return Number.isInteger(n) ? n.toFixed(1) : String(n);
}

// The needles below are built from the widget's own values, so changing a value here without the
// Build stage (or the other way round) fails `npm run check:mechanisms`.
export const RATE_LIMITER_SCENARIOS: RateScenario[] = [
  {
    id: "boundary-burst",
    title: "경계 구간 burst",
    lesson: "고정 윈도우는 윈도우가 바뀌는 순간 앞뒤로 용량의 거의 두 배를 통과시킨다.",
    build: null,
  },
  {
    id: "stage1",
    title: "1단계 — 키마다 고정 윈도우",
    lesson: "한 키의 예산이 다른 키에 영향을 주지 않는다.",
    build: {
      stage: 1,
      files: [
        {
          path: "challenges/rate-limiter/stages/stage1_test.py",
          contains: [
            `RateLimiter(capacity=${STAGE1.capacity}, window_seconds=${sec(STAGE1.windowSeconds)})`,
            `range(${STAGE1.requests.filter((r) => r.key === "user-a").length})`,
            `allowed == ${STAGE1.expected.allowedA}`,
            'allow("user-b") is True',
          ],
        },
        {
          path: "challenges/rate-limiter-ts/stages/stage1_test.ts",
          contains: [
            `new RateLimiter(${STAGE1.capacity}, ${sec(STAGE1.windowSeconds)})`,
            `i < ${STAGE1.requests.filter((r) => r.key === "user-a").length}`,
            `allowed !== ${STAGE1.expected.allowedA}`,
            'allow("user-b")) !== true',
          ],
        },
      ],
    },
  },
  {
    id: "stage2",
    title: "2단계 — 윈도우가 지나면 회복",
    lesson: "윈도우가 끝나면 카운터가 사라지고 예산이 돌아온다.",
    build: {
      stage: 2,
      files: [
        { path: "challenges/rate-limiter/stages/stage2_test.py", contains: [`RateLimiter(capacity=${STAGE2.capacity}, window_seconds=${sec(STAGE2.windowSeconds)})`, "time.sleep(0.7)"] },
        { path: "challenges/rate-limiter-ts/stages/stage2_test.ts", contains: [`new RateLimiter(${STAGE2.capacity}, ${sec(STAGE2.windowSeconds)})`, "sleep(700)"] },
      ],
    },
  },
  {
    id: "stage3",
    title: "3단계 — 동시에 몰리면",
    lesson: "읽고-더하고-쓰기가 원자적이지 않으면 모두가 같은 값을 읽고 모두 통과한다.",
    build: {
      stage: 3,
      files: [
        {
          path: "challenges/rate-limiter/stages/stage3_test.py",
          contains: [`RateLimiter(capacity=${STAGE3.capacity}, window_seconds=5.0)`, `range(${STAGE3.callsPerWorker})`, `range(${STAGE3.workers})`, `["allowed"] <= ${STAGE3.capacity}`],
        },
        {
          path: "challenges/rate-limiter-ts/stages/stage3_test.ts",
          contains: [`new RateLimiter(${STAGE3.capacity}, 5.0)`, `i < ${STAGE3.workers * STAGE3.callsPerWorker}`, `allowed > ${STAGE3.capacity}`],
        },
      ],
    },
  },
  {
    id: "stage4",
    title: "4단계 — 인스턴스가 여러 대",
    lesson: "저장소를 공유해야 인스턴스 수와 상관없이 전체 한도가 지켜진다.",
    build: {
      stage: 4,
      files: [
        {
          path: "challenges/rate-limiter/stages/stage4_test.py",
          contains: [`RateLimiter(capacity=${STAGE4.capacity}, window_seconds=5.0, store=shared_store)`, `range(${STAGE4.requests})`, `total_allowed == ${STAGE4.expected.sharedAllowed}`],
        },
        {
          path: "challenges/rate-limiter-ts/stages/stage4_test.ts",
          contains: [`new RateLimiter(${STAGE4.capacity}, 5.0, sharedStore)`, `i < ${STAGE4.requests}`, `totalAllowed !== ${STAGE4.expected.sharedAllowed}`],
        },
      ],
    },
  },
  {
    id: "stage5",
    title: "5단계 — 저장소가 죽으면",
    lesson: "fail-open은 가용성을, fail-closed는 보호를 고른다.",
    build: {
      stage: 5,
      files: [
        { path: "challenges/rate-limiter/stages/stage5_test.py", contains: ['fail_mode="open"', 'fail_mode="closed"', 'allow("k") is True', 'allow("k") is False'] },
        { path: "challenges/rate-limiter-ts/stages/stage5_test.ts", contains: ['new FaultyStore(), "open"', 'new FaultyStore(), "closed"'] },
      ],
    },
  },
  {
    id: "stage6",
    title: "6단계 — 운영 지표",
    lesson: "거절률은 한도가 맞는지 보는 첫 지표다.",
    build: {
      stage: 6,
      files: [
        {
          path: "challenges/rate-limiter/stages/stage6_test.py",
          contains: [`RateLimiter(capacity=${STAGE6.capacity}, window_seconds=${sec(STAGE6.windowSeconds)})`, `m["allowed"] == ${STAGE6.expected.allowed}`, `m["rejected"] == ${STAGE6.expected.rejected}`, "(1 / 3)"],
        },
        {
          path: "challenges/rate-limiter-ts/stages/stage6_test.ts",
          contains: [`new RateLimiter(${STAGE6.capacity}, ${sec(STAGE6.windowSeconds)})`, `m.allowed !== ${STAGE6.expected.allowed}`, `m.rejected !== ${STAGE6.expected.rejected}`, "1 / 3"],
        },
      ],
    },
  },
];
