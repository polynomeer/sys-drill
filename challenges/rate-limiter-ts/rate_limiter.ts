/**
 * SysDrill Build Mode — Build your own Rate Limiter (TypeScript)
 *
 * Implement `RateLimiter` below across 6 stages (see stages/README.md).
 * Keep the class and method names as-is — the stage tests import this
 * module directly. Submit by running ./submit.sh once you're ready (see
 * README.md at the repo root).
 *
 * 이 샌드박스는 `node --experimental-strip-types`로 실행됩니다 — 타입만
 * 벗겨낼 뿐 완전한 트랜스파일이 아니라서, 생성자 파라미터 프로퍼티
 * (`constructor(private x: number)`) 같은 일부 TS 문법은 지원하지 않습니다.
 * 아래 스텁처럼 일반 필드 선언 + 생성자 본문에서 대입하는 방식을 쓰세요.
 */

/**
 * A shared key -> counter store — provided as a working implementation,
 * not a TODO. It deliberately mirrors a real round trip to an external
 * store like Redis: `incr()` reads, awaits (simulating network I/O), then
 * writes — so it is NOT atomic on its own; two concurrent `incr()` calls
 * on the same key can race. That's intentional: making the overall
 * operation safe under concurrent calls is `RateLimiter`'s job (stage 3),
 * exactly like it would be against a real external store used without an
 * atomic command.
 */
export class InMemoryStore {
  private data: Map<string, number> = new Map();

  async incr(key: string): Promise<number> {
    const current = this.data.get(key) ?? 0;
    await new Promise((resolve) => setImmediate(resolve));
    const next = current + 1;
    this.data.set(key, next);
    return next;
  }

  async expire(key: string, seconds: number): Promise<void> {
    await new Promise((resolve) => setImmediate(resolve));
    setTimeout(() => this.data.delete(key), seconds * 1000).unref();
  }
}

/** Always rejects — stage 5 uses this to simulate the backing store (e.g. Redis) being unavailable, so you can test failMode. */
export class FaultyStore {
  async incr(_key: string): Promise<number> {
    throw new Error("store unavailable");
  }

  async expire(_key: string, _seconds: number): Promise<void> {
    throw new Error("store unavailable");
  }
}

export type FailMode = "open" | "closed";

export class RateLimiter {
  private capacity: number;
  private windowSeconds: number;
  private store: InMemoryStore | FaultyStore;
  private failMode: FailMode;

  constructor(capacity: number, windowSeconds: number = 1.0, store?: InMemoryStore | FaultyStore, failMode: FailMode = "open") {
    // TODO(stage 1): store the config. Default to new InMemoryStore() if
    // `store` is undefined (stage 4: callers may pass a *shared* store).
    throw new Error("not implemented");
  }

  async allow(key: string): Promise<boolean> {
    // TODO(stage 1): fixed-window admission — at most `capacity`
    // true results per `windowSeconds` per key. A classic pattern:
    // `count = await this.store.incr(key); if (count === 1) await this.store.expire(key, this.windowSeconds);`
    // TODO(stage 3): the store's incr() is NOT atomic (see its own doc
    // comment) — make this method safe when many calls race on the same
    // key at once.
    // TODO(stage 5): when the store throws, admit if failMode === "open",
    // reject if failMode === "closed".
    // TODO(stage 6): track allowed/rejected counts for `metrics`.
    throw new Error("not implemented");
  }

  get metrics(): { allowed: number; rejected: number; rejectRate: number } {
    // TODO(stage 6): return { allowed, rejected, rejectRate }.
    throw new Error("not implemented");
  }
}
