-- docs/LEARNING_EXPANSION_PLAN.md L4-b (PLAN.md Round E2) — /bridge가 7개 챌린지 전부를
-- 웹에서 풀 수 있도록 스텁을 DB에 둔다. 지금까지 스텁은 rate-limiter(Python/TS) 두 개만
-- 프론트 상수로 있었고 나머지 5개는 challenges/ CLI로만 풀 수 있었다.
-- 서버 채점이 이미 DB의 test_script를 쓰는 것과 같은 "콘텐츠는 DB" 원칙(ADR-0006).
-- challenges/<slug>/ 파일과의 일치는 BuildStarterCodeTest가 고정한다.

alter table build_challenges add column starter_code text;

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Rate Limiter

Implement the classes below across 6 stages (see stages/README.md).
Keep the class and method names as-is — the stage tests import this
module directly. Submit by running ./submit.sh once you're ready (see
README.md at the repo root).
"""


class InMemoryStore:
    """A minimal key -> counter store, shared by every RateLimiter
    instance that's constructed with the same InMemoryStore object.
    Passing the same store to two RateLimiter instances is how stage 4
    simulates "multiple instances behind a shared rate-limit store"
    without needing a real network call.
    """

    def __init__(self):
        self._data: dict[str, int] = {}

    def incr(self, key: str) -> int:
        self._data[key] = self._data.get(key, 0) + 1
        return self._data[key]

    def expire(self, key: str, seconds: float) -> None:
        # TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        # Until you do, this does nothing — so a window never ends.
        pass


class FaultyStore:
    """Always raises — stage 5 uses this to simulate the backing store
    (e.g. Redis) being unavailable, so you can test fail_mode."""

    def incr(self, key: str) -> int:
        raise ConnectionError("store unavailable")

    def expire(self, key: str, seconds: float) -> None:
        raise ConnectionError("store unavailable")


class RateLimiter:
    def __init__(
        self,
        capacity: int,
        window_seconds: float = 1.0,
        store=None,
        fail_mode: str = "open",
    ):
        self.capacity = capacity
        self.window_seconds = window_seconds
        # Stage 4: callers may pass a *shared* store.
        self.store = store if store is not None else InMemoryStore()
        self.fail_mode = fail_mode

    def allow(self, key: str) -> bool:
        # Stage 1 — uncomment the four lines below and submit.
        # count = self.store.incr(key)
        # if count == 1:
        #     self.store.expire(key, self.window_seconds)
        # return count <= self.capacity
        # TODO(stage 3): make this safe under concurrent calls.
        # TODO(stage 5): when the store raises, admit if fail_mode == "open",
        # reject if fail_mode == "closed".
        # TODO(stage 6): track allowed/rejected counts for `metrics`.
        raise NotImplementedError

    @property
    def metrics(self) -> dict:
        # TODO(stage 6): return {"allowed": int, "rejected": int, "reject_rate": float}.
        raise NotImplementedError
$stub$ where slug = 'rate-limiter';

update build_challenges set starter_code = $stub$/**
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
    this.capacity = capacity;
    this.windowSeconds = windowSeconds;
    // Stage 4: callers may pass a *shared* store.
    this.store = store ?? new InMemoryStore();
    this.failMode = failMode;
  }

  async allow(key: string): Promise<boolean> {
    // Stage 1 — uncomment the three lines below and submit.
    // const count = await this.store.incr(key);
    // if (count === 1) await this.store.expire(key, this.windowSeconds);
    // return count <= this.capacity;
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
$stub$ where slug = 'rate-limiter-ts';

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Queue

Implement the Queue class below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class Queue:
    """An at-least-once message queue with visibility timeouts (like SQS),
    not a plain FIFO: a dequeued message stays invisible to other
    dequeue() calls until it's ack()'d or the visibility timeout expires,
    at which point it's redelivered — up to max_retries times before it
    moves to the dead_letter_queue.
    """

    def __init__(self, visibility_timeout: float = 5.0, max_retries: int = 3):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def enqueue(self, payload) -> str:
        # TODO(stage 1): add a message, return its message id.
        raise NotImplementedError

    def dequeue(self) -> dict | None:
        # TODO(stage 1): pop the oldest *visible* message (FIFO), or None
        # if nothing is visible. Return {"id": ..., "payload": ...}.
        # TODO(stage 2): once returned, the message must stay invisible to
        # other dequeue() calls until ack()'d or visibility_timeout elapses.
        # TODO(stage 3): if a message's attempts reach max_retries without
        # being ack'd, move it to dead_letter_queue instead of redelivering.
        # TODO(stage 4): make this safe when called concurrently from
        # multiple threads — no two callers may receive the same message.
        raise NotImplementedError

    def ack(self, message_id: str) -> None:
        # TODO(stage 2): permanently remove the message so it's never redelivered.
        raise NotImplementedError

    @property
    def dead_letter_queue(self) -> list:
        # TODO(stage 3): messages that exceeded max_retries without being ack'd.
        raise NotImplementedError
$stub$ where slug = 'queue';

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Circuit Breaker

Implement the CircuitBreaker class below across 4 stages (see README.md).
Keep the class/method names as-is — the stage tests import this module
directly. Submit by running ./submit.sh once you're ready.
"""


class CircuitOpenError(Exception):
    """Raised by call() when the breaker is OPEN — the wrapped function must not run."""


class CircuitBreaker:
    """Wraps calls to a possibly-failing function (e.g. an external API) and
    stops calling it once it's clearly broken, instead of letting every
    caller wait out its own timeout.
    """

    def __init__(self, failure_threshold: int = 3, recovery_timeout: float = 5.0):
        # TODO(stage 1): store config and start CLOSED.
        raise NotImplementedError

    @property
    def state(self) -> str:
        # TODO(stage 1): "CLOSED" | "OPEN" | "HALF_OPEN".
        # TODO(stage 3): once OPEN and recovery_timeout has elapsed since the
        # trip, reading state should report "HALF_OPEN" (a real trial call
        # hasn't necessarily happened yet — this is a state *transition*,
        # not just a label).
        raise NotImplementedError

    def call(self, fn, *args, **kwargs):
        # TODO(stage 1): while CLOSED, call fn(*args, **kwargs) and return its result.
        # TODO(stage 2): count consecutive failures; once failure_threshold is
        # reached, trip to OPEN. While OPEN, raise CircuitOpenError immediately
        # WITHOUT calling fn — that's the whole point (fail fast).
        # TODO(stage 3): once state has moved to HALF_OPEN (see the `state`
        # property), the next call() is a *trial*: run fn for real, and if it
        # succeeds, recover to CLOSED (reset the failure count too).
        # TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        # and restart the recovery_timeout countdown from now.
        raise NotImplementedError
$stub$ where slug = 'circuit-breaker';

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Distributed Lock

Implement the classes below across 4 stages (see README.md). Keep the
class/method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class LockStore:
    """A minimal shared key -> (owner, expiry, fencing token) store, shared
    by every DistributedLock instance constructed with the same LockStore
    object. Passing the same store to two DistributedLock instances is how
    the stages simulate "multiple processes/instances talking to the same
    external lock service (e.g. Redis)" without needing a real one.
    """

    def __init__(self):
        # TODO(stage 1): set up whatever storage you need.
        raise NotImplementedError

    def try_acquire(self, key: str, owner_id: str, lease_seconds: float):
        # TODO(stage 1): if `key` is free, claim it for `owner_id` and return
        # a fencing token (an int). If it's already held by someone whose
        # lease hasn't expired, return None.
        # TODO(stage 2): a lease expires `lease_seconds` after it was
        # acquired — after that, the key is free again even without release().
        # TODO(stage 3): each successful acquisition must get a fencing token
        # strictly greater than every token issued before it, even across
        # different owners and even after the key was released/expired.
        raise NotImplementedError

    def try_release(self, key: str, owner_id: str, token: int) -> bool:
        # TODO(stage 1): release `key` only if `owner_id`+`token` match the
        # current holder; return whether it actually released anything.
        # TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        # after its lease already expired and someone else acquired the
        # lock) must NOT be able to release the current holder's lock.
        raise NotImplementedError

    def is_locked(self, key: str) -> bool:
        # TODO(stage 1): whether `key` is currently held by an unexpired lease.
        raise NotImplementedError


class DistributedLock:
    def __init__(self, key: str, store: LockStore | None = None, lease_seconds: float = 5.0):
        # TODO(stage 1): store config. Default to LockStore() if `store` is
        # None (stage 4: callers may pass a *shared* store).
        raise NotImplementedError

    def acquire(self, owner_id: str):
        # TODO(stage 1): delegate to self.store.try_acquire(...).
        # TODO(stage 4): make this safe when called concurrently — only one
        # of many simultaneous callers for the same key may succeed.
        raise NotImplementedError

    def release(self, owner_id: str, fencing_token: int) -> bool:
        # TODO(stage 1): delegate to self.store.try_release(...).
        raise NotImplementedError

    def is_locked(self) -> bool:
        # TODO(stage 1): delegate to self.store.is_locked(...).
        raise NotImplementedError
$stub$ where slug = 'distributed-lock';

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Retry/Backoff Middleware

Implement the classes below across 4 stages (see README.md). Keep the
class/method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class RetryExhaustedError(Exception):
    """Raised by execute() when every attempt failed."""


class RetryBudget:
    """A shared token bucket that caps the *total* number of retries across
    every RetryPolicy that shares it — protects a downstream dependency from
    a retry storm even when many independent callers are each individually
    retrying. Pass the same RetryBudget instance to multiple RetryPolicy
    instances to share it (stage 4).
    """

    def __init__(self, capacity: int = 10):
        # TODO(stage 4): store the starting capacity.
        raise NotImplementedError

    def try_consume(self) -> bool:
        # TODO(stage 4): if a token is available, consume it and return True.
        # If the budget is exhausted, return False (and consume nothing).
        raise NotImplementedError


class RetryPolicy:
    def __init__(
        self,
        max_attempts: int = 5,
        base_delay: float = 0.01,
        max_delay: float = 1.0,
        budget: RetryBudget | None = None,
        sleep_fn=None,
    ):
        # TODO(stage 1): store config. Default sleep_fn to time.sleep if None
        # (tests pass their own sleep_fn so they don't have to actually wait).
        raise NotImplementedError

    def execute(self, fn, *args, **kwargs):
        # TODO(stage 1): call fn(*args, **kwargs). On success, return its
        # result immediately. On failure, retry up to max_attempts total
        # calls, then raise RetryExhaustedError.
        # TODO(stage 3): between attempts, call self.sleep_fn(delay) where
        # delay grows exponentially with the attempt number (base_delay *
        # 2**attempt, capped at max_delay) *with jitter* — don't use the
        # exact exponential value, pick randomly within [0, capped_value]
        # ("full jitter") so many simultaneous retriers don't all retry at
        # the exact same moment (thundering herd).
        # TODO(stage 4): if a budget was provided, call budget.try_consume()
        # before each retry (not before the first attempt). If it returns
        # False, stop retrying immediately (raise RetryExhaustedError) even
        # if max_attempts hasn't been reached yet.
        raise NotImplementedError
$stub$ where slug = 'retry-backoff';

update build_challenges set starter_code = $stub$"""
SysDrill Build Mode — Build your own Event Bus

Implement the EventBus class below across 4 stages (see README.md). Keep
the class/method names as-is — the stage tests import this module
directly. Submit by running ./submit.sh once you're ready.
"""


class EventBus:
    """A topic-based pub/sub bus with at-least-once delivery: publish(topic,
    payload) fans out a copy of the event to every current subscriber of
    that topic. Each subscriber pulls its own copy via poll() — like
    Build your own Queue, a polled event stays invisible to that same
    subscriber's later poll() calls until it's ack()'d or the visibility
    timeout expires, at which point it's redelivered (up to max_retries).
    """

    def __init__(self, visibility_timeout: float = 5.0, max_retries: int = 3):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def subscribe(self, topic: str) -> str:
        # TODO(stage 1): register a new subscriber for `topic`, return a
        # subscriber id used by poll()/ack(). Only events published *after*
        # subscribe() need to reach this subscriber.
        raise NotImplementedError

    def publish(self, topic: str, payload) -> str:
        # TODO(stage 1): deliver a copy of this event to every subscriber
        # currently subscribed to `topic` (fan-out) — subscribers of other
        # topics must not receive it. Return an event id.
        raise NotImplementedError

    def poll(self, subscriber_id: str) -> dict | None:
        # TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
        # per subscriber), or None if nothing is visible. Return
        # {"id": ..., "payload": ...}.
        # TODO(stage 2): once returned, the event must stay invisible to
        # this subscriber's other poll() calls until ack()'d or
        # visibility_timeout elapses (then it's redelivered).
        # TODO(stage 3): events for one subscriber must come out in the
        # same order they were published to its topic.
        # TODO(stage 4): make this safe when called concurrently from
        # multiple threads for the same subscriber — no event may be
        # delivered twice or lost.
        raise NotImplementedError

    def ack(self, subscriber_id: str, event_id: str) -> None:
        # TODO(stage 2): permanently remove the event so it's never redelivered.
        raise NotImplementedError
$stub$ where slug = 'event-bus';
