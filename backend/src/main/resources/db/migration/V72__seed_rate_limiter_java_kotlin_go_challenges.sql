-- Java·Kotlin·Go 판 rate-limiter (ADR-0051). V44의 TypeScript 판과 같은 방식 — 같은 6개
-- 스테이지를 언어마다 별도 챌린지(slug 접미사 -java/-kotlin/-go)로 둔다. 테스트 스크립트와
-- 스텁은 challenges/rate-limiter-{java,kotlin,go}/ 의 파일 그대로다(BuildStarterCodeTest가
-- 스텁 일치를 고정한다). 셋 다 Python판과 같은 구조다: InMemoryStore.expire가 비어 있어
-- 2단계가 실제 과제이고, incr는 읽기와 쓰기 사이에 왕복 지연을 둔 비원자적 구현이라
-- 3단계(실제 스레드/고루틴 200회 동시 호출)가 락 없는 allow()를 확실히 걸러낸다.

-- rate-limiter-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'b0000000-0000-0000-0000-000000000003',
    'rate-limiter-java',
    'Build your own Rate Limiter (Java)',
    'java',
    'RateLimiter.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Rate Limiter (Java)
 *
 * Implement `RateLimiter` below across 6 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A key -> counter store, standing in for something like Redis. */
interface Store {
    long incr(String key);

    void expire(String key, double seconds);
}

/** Thrown by a store that can't be reached — see {@link FaultyStore}. */
class StoreUnavailableException extends RuntimeException {
    StoreUnavailableException(String message) {
        super(message);
    }
}

/**
 * Shared by every RateLimiter constructed with the same InMemoryStore object
 * — passing one store to two limiters is how stage 4 simulates "multiple
 * instances behind a shared rate-limit store".
 *
 * Each map operation is thread-safe on its own, but incr() is a read, a
 * round trip, then a write — like a GET and a SET against Redis — so two
 * concurrent incr() calls on the same key can race. That's intentional:
 * making allow() safe under concurrent calls is RateLimiter's job (stage 3).
 */
class InMemoryStore implements Store {
    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    @Override
    public long incr(String key) {
        long current = counts.getOrDefault(key, 0L);
        roundTrip();
        long next = current + 1;
        counts.put(key, next);
        return next;
    }

    @Override
    public void expire(String key, double seconds) {
        // TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        // Until you do, this does nothing — so a window never ends.
    }

    /** Simulated network latency between the read and the write. */
    private static void roundTrip() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/** Always throws — stage 5 uses this to simulate the store (e.g. Redis) being down, so you can test failMode. */
class FaultyStore implements Store {
    @Override
    public long incr(String key) {
        throw new StoreUnavailableException("store unavailable");
    }

    @Override
    public void expire(String key, double seconds) {
        throw new StoreUnavailableException("store unavailable");
    }
}

enum FailMode { OPEN, CLOSED }

record Metrics(long allowed, long rejected, double rejectRate) {}

public class RateLimiter {
    private final int capacity;
    private final double windowSeconds;
    private final Store store;
    private final FailMode failMode;

    public RateLimiter(int capacity, double windowSeconds) {
        this(capacity, windowSeconds, new InMemoryStore(), FailMode.OPEN);
    }

    /** Stage 4: callers may pass a *shared* store. */
    public RateLimiter(int capacity, double windowSeconds, Store store) {
        this(capacity, windowSeconds, store, FailMode.OPEN);
    }

    public RateLimiter(int capacity, double windowSeconds, Store store, FailMode failMode) {
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
        this.store = store;
        this.failMode = failMode;
    }

    public boolean allow(String key) {
        // Stage 1 — uncomment the three lines below, delete the `throw` line, and submit.
        // long count = store.incr(key);
        // if (count == 1) store.expire(key, windowSeconds);
        // return count <= capacity;
        // TODO(stage 3): make this safe under concurrent calls.
        // TODO(stage 5): when the store throws StoreUnavailableException, admit if
        // failMode == OPEN, reject if failMode == CLOSED.
        // TODO(stage 6): track allowed/rejected counts for metrics().
        throw new UnsupportedOperationException("not implemented");
    }

    public Metrics metrics() {
        // TODO(stage 6): return new Metrics(allowed, rejected, rejectRate).
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, instructions, test_script)
values
(
    'b0000000-0000-0000-0000-000000000003',
    1,
    '단일 프로세스 fixed window',
    '경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청)',
    '목표: 키마다 윈도우(windowSeconds) 안에서 최대 capacity개 요청만 true를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: new RateLimiter(3, 10.0) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
할 일: 생성자와 InMemoryStore.incr는 이미 채워져 있습니다. allow() 안의 주석 처리된 세 줄의 주석을 풀고, 그 아래 throw new UnsupportedOperationException(...) 줄을 지운 뒤 제출하세요. 그 세 줄이 무엇을 하는지 읽어 두면 다음 단계가 쉬워집니다.',
    $stage$// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        RateLimiter rl = new RateLimiter(3, 10.0);
        int allowed = 0;
        for (int i = 0; i < 5; i++) if (rl.allow("user-a")) allowed++;
        check(allowed == 3, "expected exactly 3 allowed within the window, got " + allowed);
        check(rl.allow("user-b"), "a different key should not be affected by user-a's budget");
    }
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000003',
    2,
    '윈도우 회복 (sliding/token bucket 감각)',
    '정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가)',
    '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, windowSeconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: 지금 InMemoryStore.expire는 아무것도 하지 않아서 윈도우가 끝나지 않습니다. 키별 만료 시각을 저장해 두고, incr 때 그 시각이 지났으면 카운터를 0부터 다시 세세요.',
    $stage$// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() throws InterruptedException {
        RateLimiter rl = new RateLimiter(2, 0.5);
        check(rl.allow("k"), "expected the first request to be allowed");
        check(rl.allow("k"), "expected the second request to be allowed");
        check(!rl.allow("k"), "third request within the window should be rejected");
        Thread.sleep(700);
        check(rl.allow("k"), "after the window elapses, capacity should replenish");
    }
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000003',
    3,
    '동시성 안전성',
    'atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다)',
    '목표: 여러 스레드가 동시에 allow()를 불러도 capacity를 넘기지 않게 합니다.
테스트가 확인하는 것: capacity=50 리미터에 10개 스레드가 20번씩(총 200번) 호출한 뒤 metrics().allowed() <= 50 이어야 합니다.
주의: 이 테스트는 metrics().allowed()를 읽습니다 — 6단계의 metrics 중 allowed 카운트는 여기서 미리 만들어 두세요.
힌트: 제공된 InMemoryStore.incr는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸 지연이 있어 원자적이지 않습니다(Redis에 GET 후 SET 하는 것과 같습니다). "incr하고-비교하기"가 한 번에 일어나도록 allow()를 synchronized로 만들거나 ReentrantLock으로 감싸세요.',
    $stage$// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.incr()는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — allow()에 동시성 제어가 없으면 capacity를 넘긴다.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() throws Throwable {
        RateLimiter rl = new RateLimiter(50, 5.0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            threads.add(new Thread(() -> {
                try {
                    for (int i = 0; i < 20; i++) rl.allow("shared-key");
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();
        long allowed = rl.metrics().allowed();
        check(allowed <= 50, "concurrent access let " + allowed + " requests through, expected <= 50");
    }
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000003',
    4,
    '공유("분산") 스토어',
    '네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가 따로 놀아서 총 허용량이 커진다)',
    '목표: 여러 리미터 인스턴스가 같은 store를 공유하면 총 허용량도 공유되게 합니다(서버 여러 대 + Redis 상황).
테스트가 확인하는 것: InMemoryStore 하나를 공유하는 capacity=5 리미터 두 개에 번갈아 10번 요청하면 합쳐서 정확히 5번만 허용되어야 합니다.
힌트: 카운트를 인스턴스 필드가 아니라 store에 두어야 합니다. 락도 인스턴스마다 따로면 공유 store를 지키지 못합니다.',
    $stage$// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        InMemoryStore sharedStore = new InMemoryStore();
        RateLimiter instanceA = new RateLimiter(5, 5.0, sharedStore);
        RateLimiter instanceB = new RateLimiter(5, 5.0, sharedStore);
        int totalAllowed = 0;
        for (int i = 0; i < 10; i++) {
            RateLimiter limiter = i % 2 == 0 ? instanceA : instanceB;
            if (limiter.allow("shared-key")) totalAllowed++;
        }
        check(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got " + totalAllowed);
    }
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000003',
    5,
    'fail-open / fail-closed',
    '가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는 설계 선택이다)',
    '목표: store(예: Redis)가 죽었을 때의 동작을 failMode로 고를 수 있게 합니다.
테스트가 확인하는 것: FaultyStore(항상 StoreUnavailableException)를 쓸 때 FailMode.OPEN이면 허용(true), FailMode.CLOSED면 거절(false)해야 합니다.
힌트: store 호출을 try/catch(StoreUnavailableException)로 감싸세요. 어느 쪽이 맞는지는 서비스의 선택입니다 — 가용성(open)과 보호(closed)의 trade-off를 설계 단계에서도 다시 만나게 됩니다.',
    $stage$// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        RateLimiter openLimiter = new RateLimiter(1, 1.0, new FaultyStore(), FailMode.OPEN);
        check(openLimiter.allow("k"), "failMode=OPEN should admit requests when the store is unavailable");

        RateLimiter closedLimiter = new RateLimiter(1, 1.0, new FaultyStore(), FailMode.CLOSED);
        check(!closedLimiter.allow("k"), "failMode=CLOSED should reject requests when the store is unavailable");
    }
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000003',
    6,
    '운영 metric',
    'reject rate, latency, key skew 같은 운영 지표가 있어야 실제로 튜닝하고 대응할 수 있다',
    '목표: 운영자가 튜닝할 수 있도록 지표를 노출합니다.
테스트가 확인하는 것: capacity=2 리미터에 3번 요청한 뒤 metrics가 new Metrics(2, 1, 약 0.333) 이어야 합니다.
힌트: allow()가 결정할 때마다 allowed/rejected를 세고(여러 스레드가 부르므로 원자적으로), rejectRate = rejected / (allowed + rejected) (요청이 0개면 0.0)로 계산하세요.',
    $stage$// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        RateLimiter rl = new RateLimiter(2, 5.0);
        rl.allow("k");
        rl.allow("k");
        rl.allow("k");
        Metrics m = rl.metrics();
        check(m.allowed() == 2, "expected 2 allowed, got " + m.allowed());
        check(m.rejected() == 1, "expected 1 rejected, got " + m.rejected());
        check(Math.abs(m.rejectRate() - 1.0 / 3) < 0.01, "expected rejectRate ~0.333, got " + m.rejectRate());
    }
}
$stage$
);

-- rate-limiter-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'b0000000-0000-0000-0000-000000000004',
    'rate-limiter-kotlin',
    'Build your own Rate Limiter (Kotlin)',
    'kotlin',
    'RateLimiter.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Rate Limiter (Kotlin)
 *
 * Implement `RateLimiter` below across 6 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.ConcurrentHashMap

/** A key -> counter store, standing in for something like Redis. */
interface Store {
    fun incr(key: String): Long

    fun expire(key: String, seconds: Double)
}

/** Thrown by a store that can't be reached — see [FaultyStore]. */
class StoreUnavailableException(message: String) : RuntimeException(message)

/**
 * Shared by every RateLimiter constructed with the same InMemoryStore object
 * — passing one store to two limiters is how stage 4 simulates "multiple
 * instances behind a shared rate-limit store".
 *
 * Each map operation is thread-safe on its own, but incr() is a read, a
 * round trip, then a write — like a GET and a SET against Redis — so two
 * concurrent incr() calls on the same key can race. That's intentional:
 * making allow() safe under concurrent calls is RateLimiter's job (stage 3).
 */
class InMemoryStore : Store {
    private val counts = ConcurrentHashMap<String, Long>()

    override fun incr(key: String): Long {
        val current = counts.getOrDefault(key, 0L)
        Thread.sleep(1) // simulated network latency between the read and the write
        val next = current + 1
        counts[key] = next
        return next
    }

    override fun expire(key: String, seconds: Double) {
        // TODO(stage 2): make the counter for `key` reset to 0 after `seconds`.
        // Until you do, this does nothing — so a window never ends.
    }
}

/** Always throws — stage 5 uses this to simulate the store (e.g. Redis) being down, so you can test failMode. */
class FaultyStore : Store {
    override fun incr(key: String): Long = throw StoreUnavailableException("store unavailable")

    override fun expire(key: String, seconds: Double): Unit = throw StoreUnavailableException("store unavailable")
}

enum class FailMode { OPEN, CLOSED }

data class Metrics(val allowed: Long, val rejected: Long, val rejectRate: Double)

class RateLimiter(
    private val capacity: Int,
    private val windowSeconds: Double = 1.0,
    // Stage 4: callers may pass a *shared* store.
    private val store: Store = InMemoryStore(),
    private val failMode: FailMode = FailMode.OPEN,
) {
    fun allow(key: String): Boolean {
        // Stage 1 — uncomment the three lines below, delete the `TODO(...)` call, and submit.
        // val count = store.incr(key)
        // if (count == 1L) store.expire(key, windowSeconds)
        // return count <= capacity
        // TODO(stage 3): make this safe under concurrent calls.
        // TODO(stage 5): when the store throws StoreUnavailableException, admit if
        // failMode == OPEN, reject if failMode == CLOSED.
        // TODO(stage 6): track allowed/rejected counts for `metrics`.
        TODO("not implemented")
    }

    val metrics: Metrics
        // TODO(stage 6): return Metrics(allowed, rejected, rejectRate).
        get() = TODO("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, instructions, test_script)
values
(
    'b0000000-0000-0000-0000-000000000004',
    1,
    '단일 프로세스 fixed window',
    '경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청)',
    '목표: 키마다 윈도우(windowSeconds) 안에서 최대 capacity개 요청만 true를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: RateLimiter(capacity = 3, windowSeconds = 10.0) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
할 일: 생성자와 InMemoryStore.incr는 이미 채워져 있습니다. allow() 안의 주석 처리된 세 줄의 주석을 풀고, 그 아래 TODO("not implemented") 줄을 지운 뒤 제출하세요. 그 세 줄이 무엇을 하는지 읽어 두면 다음 단계가 쉬워집니다.',
    $stage$@file:JvmName("RunTest")

// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val rl = RateLimiter(capacity = 3, windowSeconds = 10.0)
    val allowed = (1..5).count { rl.allow("user-a") }
    expect(allowed == 3, "expected exactly 3 allowed within the window, got $allowed")
    expect(rl.allow("user-b"), "a different key should not be affected by user-a's budget")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000004',
    2,
    '윈도우 회복 (sliding/token bucket 감각)',
    '정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가)',
    '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, windowSeconds=0.5 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: 지금 InMemoryStore.expire는 아무것도 하지 않아서 윈도우가 끝나지 않습니다. 키별 만료 시각을 저장해 두고, incr 때 그 시각이 지났으면 카운터를 0부터 다시 세세요.',
    $stage$@file:JvmName("RunTest")

// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val rl = RateLimiter(capacity = 2, windowSeconds = 0.5)
    expect(rl.allow("k"), "expected the first request to be allowed")
    expect(rl.allow("k"), "expected the second request to be allowed")
    expect(!rl.allow("k"), "third request within the window should be rejected")
    Thread.sleep(700)
    expect(rl.allow("k"), "after the window elapses, capacity should replenish")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000004',
    3,
    '동시성 안전성',
    'atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다)',
    '목표: 여러 스레드가 동시에 allow()를 불러도 capacity를 넘기지 않게 합니다.
테스트가 확인하는 것: capacity=50 리미터에 10개 스레드가 20번씩(총 200번) 호출한 뒤 metrics.allowed <= 50 이어야 합니다.
주의: 이 테스트는 metrics.allowed를 읽습니다 — 6단계의 metrics 중 allowed 카운트는 여기서 미리 만들어 두세요.
힌트: 제공된 InMemoryStore.incr는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸 지연이 있어 원자적이지 않습니다(Redis에 GET 후 SET 하는 것과 같습니다). "incr하고-비교하기"가 한 번에 일어나도록 allow()에 @Synchronized를 붙이거나 synchronized(lock) { } 블록으로 감싸세요.',
    $stage$@file:JvmName("RunTest")

// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.incr()는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — allow()에 동시성 제어가 없으면 capacity를 넘긴다.
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val rl = RateLimiter(capacity = 50, windowSeconds = 5.0)
    val failure = AtomicReference<Throwable>()
    val threads = (1..10).map {
        thread {
            try {
                repeat(20) { rl.allow("shared-key") }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    threads.forEach { it.join() }
    failure.get()?.let { throw it }
    val allowed = rl.metrics.allowed
    expect(allowed <= 50, "concurrent access let $allowed requests through, expected <= 50")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000004',
    4,
    '공유("분산") 스토어',
    '네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가 따로 놀아서 총 허용량이 커진다)',
    '목표: 여러 리미터 인스턴스가 같은 store를 공유하면 총 허용량도 공유되게 합니다(서버 여러 대 + Redis 상황).
테스트가 확인하는 것: InMemoryStore 하나를 공유하는 capacity=5 리미터 두 개에 번갈아 10번 요청하면 합쳐서 정확히 5번만 허용되어야 합니다.
힌트: 카운트를 인스턴스 필드가 아니라 store에 두어야 합니다. 락도 인스턴스마다 따로면 공유 store를 지키지 못합니다.',
    $stage$@file:JvmName("RunTest")

// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val sharedStore = InMemoryStore()
    val instanceA = RateLimiter(capacity = 5, windowSeconds = 5.0, store = sharedStore)
    val instanceB = RateLimiter(capacity = 5, windowSeconds = 5.0, store = sharedStore)
    val totalAllowed = (0 until 10).count { i -> (if (i % 2 == 0) instanceA else instanceB).allow("shared-key") }
    expect(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got $totalAllowed")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000004',
    5,
    'fail-open / fail-closed',
    '가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는 설계 선택이다)',
    '목표: store(예: Redis)가 죽었을 때의 동작을 failMode로 고를 수 있게 합니다.
테스트가 확인하는 것: FaultyStore(항상 StoreUnavailableException)를 쓸 때 FailMode.OPEN이면 허용(true), FailMode.CLOSED면 거절(false)해야 합니다.
힌트: store 호출을 try/catch(StoreUnavailableException)로 감싸세요. 어느 쪽이 맞는지는 서비스의 선택입니다 — 가용성(open)과 보호(closed)의 trade-off를 설계 단계에서도 다시 만나게 됩니다.',
    $stage$@file:JvmName("RunTest")

// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val openLimiter = RateLimiter(capacity = 1, store = FaultyStore(), failMode = FailMode.OPEN)
    expect(openLimiter.allow("k"), "failMode=OPEN should admit requests when the store is unavailable")

    val closedLimiter = RateLimiter(capacity = 1, store = FaultyStore(), failMode = FailMode.CLOSED)
    expect(!closedLimiter.allow("k"), "failMode=CLOSED should reject requests when the store is unavailable")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000004',
    6,
    '운영 metric',
    'reject rate, latency, key skew 같은 운영 지표가 있어야 실제로 튜닝하고 대응할 수 있다',
    '목표: 운영자가 튜닝할 수 있도록 지표를 노출합니다.
테스트가 확인하는 것: capacity=2 리미터에 3번 요청한 뒤 metrics가 Metrics(allowed = 2, rejected = 1, rejectRate = 약 0.333) 이어야 합니다.
힌트: allow()가 결정할 때마다 allowed/rejected를 세고(여러 스레드가 부르므로 원자적으로), rejectRate = rejected / (allowed + rejected) (요청이 0개면 0.0)로 계산하세요.',
    $stage$@file:JvmName("RunTest")

// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val rl = RateLimiter(capacity = 2, windowSeconds = 5.0)
    repeat(3) { rl.allow("k") }
    val m = rl.metrics
    expect(m.allowed == 2L, "expected 2 allowed, got ${m.allowed}")
    expect(m.rejected == 1L, "expected 1 rejected, got ${m.rejected}")
    expect(Math.abs(m.rejectRate - 1.0 / 3) < 0.01, "expected rejectRate ~0.333, got ${m.rejectRate}")
}
$stage$
);

-- rate-limiter-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'b0000000-0000-0000-0000-000000000005',
    'rate-limiter-go',
    'Build your own Rate Limiter (Go)',
    'go',
    'rate_limiter.go',
    $stub$// SysDrill Build Mode — Build your own Rate Limiter (Go)
//
// Implement RateLimiter below across 6 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"sync"
	"time"
)

// Store is a key -> counter store, standing in for something like Redis.
type Store interface {
	Incr(key string) (int64, error)
	Expire(key string, ttl time.Duration) error
}

// ErrStoreUnavailable is what a store that can't be reached returns — see FaultyStore.
var ErrStoreUnavailable = errors.New("store unavailable")

// InMemoryStore is shared by every RateLimiter constructed with the same
// *InMemoryStore — passing one store to two limiters is how stage 4
// simulates "multiple instances behind a shared rate-limit store".
//
// Each map access is guarded on its own, but Incr is a read, a round trip,
// then a write — like a GET and a SET against Redis — so two concurrent
// Incr calls on the same key can race. That's intentional: making Allow
// safe under concurrent calls is RateLimiter's job (stage 3).
type InMemoryStore struct {
	mu     sync.Mutex
	counts map[string]int64
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{counts: map[string]int64{}}
}

func (s *InMemoryStore) Incr(key string) (int64, error) {
	current := s.load(key)
	time.Sleep(time.Millisecond) // simulated network latency between the read and the write
	next := current + 1
	s.store(key, next)
	return next, nil
}

func (s *InMemoryStore) Expire(key string, ttl time.Duration) error {
	// TODO(stage 2): make the counter for key reset to 0 after ttl.
	// Until you do, this does nothing — so a window never ends.
	return nil
}

func (s *InMemoryStore) load(key string) int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.counts[key]
}

func (s *InMemoryStore) store(key string, value int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.counts[key] = value
}

// FaultyStore always fails — stage 5 uses it to simulate the store (e.g. Redis)
// being down, so you can test the fail mode.
type FaultyStore struct{}

func (FaultyStore) Incr(key string) (int64, error) { return 0, ErrStoreUnavailable }

func (FaultyStore) Expire(key string, ttl time.Duration) error { return ErrStoreUnavailable }

type FailMode int

const (
	FailOpen FailMode = iota
	FailClosed
)

type Metrics struct {
	Allowed    int64
	Rejected   int64
	RejectRate float64
}

type RateLimiter struct {
	capacity int
	window   time.Duration
	store    Store
	failMode FailMode
}

// NewRateLimiter builds a limiter. Stage 4: callers may pass a *shared*
// store; nil means a fresh InMemoryStore of its own.
func NewRateLimiter(capacity int, window time.Duration, store Store, failMode FailMode) *RateLimiter {
	if store == nil {
		store = NewInMemoryStore()
	}
	return &RateLimiter{capacity: capacity, window: window, store: store, failMode: failMode}
}

func (r *RateLimiter) Allow(key string) bool {
	// Stage 1 — uncomment the three lines below, delete the `panic(...)` line, and submit.
	// count, _ := r.store.Incr(key)
	// if count == 1 { r.store.Expire(key, r.window) }
	// return count <= int64(r.capacity)
	// TODO(stage 3): make this safe under concurrent calls.
	// TODO(stage 5): when the store returns an error, admit if failMode == FailOpen,
	// reject if failMode == FailClosed.
	// TODO(stage 6): track allowed/rejected counts for Metrics.
	panic("not implemented")
}

func (r *RateLimiter) Metrics() Metrics {
	// TODO(stage 6): return Metrics{Allowed: ..., Rejected: ..., RejectRate: ...}.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, instructions, test_script)
values
(
    'b0000000-0000-0000-0000-000000000005',
    1,
    '단일 프로세스 fixed window',
    '경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청)',
    '목표: 키마다 윈도우(window) 안에서 최대 capacity개 요청만 true를 돌려주는 fixed window 리미터를 만듭니다.
테스트가 확인하는 것: NewRateLimiter(3, 10*time.Second, nil, FailOpen) 에 같은 키로 5번 요청하면 정확히 3번만 허용되고, 다른 키는 영향을 받지 않아야 합니다.
할 일: 생성자와 InMemoryStore.Incr는 이미 채워져 있습니다. Allow() 안의 주석 처리된 세 줄의 주석을 풀고, 그 아래 panic("not implemented") 줄을 지운 뒤 제출하세요. 그 세 줄이 무엇을 하는지 읽어 두면 다음 단계가 쉬워집니다.',
    $stage$// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	rl := NewRateLimiter(3, 10*time.Second, nil, FailOpen)
	allowed := 0
	for i := 0; i < 5; i++ {
		if rl.Allow("user-a") {
			allowed++
		}
	}
	expect(allowed == 3, "expected exactly 3 allowed within the window, got %d", allowed)
	expect(rl.Allow("user-b"), "a different key should not be affected by user-a's budget")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000005',
    2,
    '윈도우 회복 (sliding/token bucket 감각)',
    '정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가)',
    '목표: 윈도우가 지나면 용량이 다시 회복되게 합니다.
테스트가 확인하는 것: capacity=2, window=500ms 에서 세 번째 요청은 거절되고, 0.7초 뒤에는 다시 허용되어야 합니다.
힌트: 지금 InMemoryStore.Expire는 아무것도 하지 않아서 윈도우가 끝나지 않습니다. 키별 만료 시각을 저장해 두고, Incr 때 그 시각이 지났으면 카운터를 0부터 다시 세세요.',
    $stage$// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	rl := NewRateLimiter(2, 500*time.Millisecond, nil, FailOpen)
	expect(rl.Allow("k"), "expected the first request to be allowed")
	expect(rl.Allow("k"), "expected the second request to be allowed")
	expect(!rl.Allow("k"), "third request within the window should be rejected")
	time.Sleep(700 * time.Millisecond)
	expect(rl.Allow("k"), "after the window elapses, capacity should replenish")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000005',
    3,
    '동시성 안전성',
    'atomicity (여러 고루틴이 동시에 호출해도 capacity를 넘기면 안 된다)',
    '목표: 여러 고루틴이 동시에 Allow()를 불러도 capacity를 넘기지 않게 합니다.
테스트가 확인하는 것: capacity=50 리미터에 10개 고루틴이 20번씩(총 200번) 호출한 뒤 Metrics().Allowed <= 50 이어야 합니다.
주의: 이 테스트는 Metrics().Allowed를 읽습니다 — 6단계의 metrics 중 allowed 카운트는 여기서 미리 만들어 두세요.
힌트: 제공된 InMemoryStore.Incr는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸 지연이 있어 원자적이지 않습니다(Redis에 GET 후 SET 하는 것과 같습니다). "Incr하고-비교하기"가 한 번에 일어나도록 RateLimiter에 sync.Mutex를 두고 Allow() 전체를 감싸세요.',
    $stage$// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 고루틴이 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.Incr는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — Allow에 동시성 제어가 없으면 capacity를 넘긴다.
package main

import (
	"fmt"
	"os"
	"sync"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	rl := NewRateLimiter(50, 5*time.Second, nil, FailOpen)
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	for g := 0; g < 10; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			for i := 0; i < 20; i++ {
				rl.Allow("shared-key")
			}
		}()
	}
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}
	allowed := rl.Metrics().Allowed
	expect(allowed <= 50, "concurrent access let %d requests through, expected <= 50", allowed)
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000005',
    4,
    '공유("분산") 스토어',
    '네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다 capacity가 따로 놀아서 총 허용량이 커진다)',
    '목표: 여러 리미터 인스턴스가 같은 store를 공유하면 총 허용량도 공유되게 합니다(서버 여러 대 + Redis 상황).
테스트가 확인하는 것: InMemoryStore 하나를 공유하는 capacity=5 리미터 두 개에 번갈아 10번 요청하면 합쳐서 정확히 5번만 허용되어야 합니다.
힌트: 카운트를 인스턴스 필드가 아니라 store에 두어야 합니다. 락도 인스턴스마다 따로면 공유 store를 지키지 못합니다.',
    $stage$// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	sharedStore := NewInMemoryStore()
	instanceA := NewRateLimiter(5, 5*time.Second, sharedStore, FailOpen)
	instanceB := NewRateLimiter(5, 5*time.Second, sharedStore, FailOpen)
	totalAllowed := 0
	for i := 0; i < 10; i++ {
		limiter := instanceB
		if i%2 == 0 {
			limiter = instanceA
		}
		if limiter.Allow("shared-key") {
			totalAllowed++
		}
	}
	expect(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got %d", totalAllowed)
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000005',
    5,
    'fail-open / fail-closed',
    '가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는 설계 선택이다)',
    '목표: store(예: Redis)가 죽었을 때의 동작을 failMode로 고를 수 있게 합니다.
테스트가 확인하는 것: FaultyStore(항상 ErrStoreUnavailable을 반환)를 쓸 때 FailOpen이면 허용(true), FailClosed면 거절(false)해야 합니다.
힌트: Incr/Expire가 돌려주는 error를 확인하세요 — 1단계 코드는 그 error를 버리고 있습니다. 어느 쪽이 맞는지는 서비스의 선택입니다 — 가용성(open)과 보호(closed)의 trade-off를 설계 단계에서도 다시 만나게 됩니다.',
    $stage$// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	openLimiter := NewRateLimiter(1, time.Second, FaultyStore{}, FailOpen)
	expect(openLimiter.Allow("k"), "FailOpen should admit requests when the store is unavailable")

	closedLimiter := NewRateLimiter(1, time.Second, FaultyStore{}, FailClosed)
	expect(!closedLimiter.Allow("k"), "FailClosed should reject requests when the store is unavailable")
}
$stage$
),
(
    'b0000000-0000-0000-0000-000000000005',
    6,
    '운영 metric',
    'reject rate, latency, key skew 같은 운영 지표가 있어야 실제로 튜닝하고 대응할 수 있다',
    '목표: 운영자가 튜닝할 수 있도록 지표를 노출합니다.
테스트가 확인하는 것: capacity=2 리미터에 3번 요청한 뒤 metrics가 Metrics{Allowed: 2, Rejected: 1, RejectRate: 약 0.333} 이어야 합니다.
힌트: Allow()가 결정할 때마다 Allowed/Rejected를 세고(여러 고루틴이 부르므로 원자적으로), RejectRate = rejected / (allowed + rejected) (요청이 0개면 0.0)로 계산하세요.',
    $stage$// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
package main

import (
	"fmt"
	"math"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	rl := NewRateLimiter(2, 5*time.Second, nil, FailOpen)
	rl.Allow("k")
	rl.Allow("k")
	rl.Allow("k")
	m := rl.Metrics()
	expect(m.Allowed == 2, "expected 2 allowed, got %d", m.Allowed)
	expect(m.Rejected == 1, "expected 1 rejected, got %d", m.Rejected)
	expect(math.Abs(m.RejectRate-1.0/3) < 0.01, "expected RejectRate ~0.333, got %v", m.RejectRate)
}
$stage$
);
