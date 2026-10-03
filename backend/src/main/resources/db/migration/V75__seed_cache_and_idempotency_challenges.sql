-- PRD §7.1의 초기 Build 과제 중 아직 없던 둘 — Build your own Cache(→ 상품 조회 Drill,
-- ENABLE_SINGLE_FLIGHT·INCREASE_CACHE_TTL), Build your own Idempotency Layer(→ 결제 Drill,
-- ENABLE_IDEMPOTENT_PG_RETRY)를 Python·Java·Kotlin·Go 네 판으로 처음부터 함께 연다.
-- 스텁과 스테이지 테스트는 challenges/<slug>/ 의 파일 그대로다(이 파일은 그 파일들에서 생성했다 —
-- BuildStarterCodeTest가 스텁 일치를, BuildLanguageVariantsIntegrationTest가 "스텁은 전부 실패,
-- 모범 답안은 전부 통과"를 고정한다). 다른 Python 과제처럼 지시문(instructions)은 두지 않는다.
--
-- 학습 개념 네 개의 "직접 구현하기" 링크도 새 과제로 잇는다. MISSING_IDEMPOTENCY는 그동안 맞는
-- 과제가 없어 rate-limiter만 걸려 있었다 — idempotency를 앞에 두고 rate-limiter는 남긴다.

-- cache
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a4000000-0000-0000-0000-000000000001',
    'cache',
    'Build your own Cache',
    'python',
    'cache.py',
    $stub$"""
SysDrill Build Mode — Build your own Cache

Implement the Cache class below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class Cache:
    """An in-process read-through cache in front of a slow loader (e.g. the
    product DB): entries expire after a TTL, the least recently used entry
    is evicted once `capacity` is reached, and concurrent misses for the
    same key share a single load instead of all hitting the loader.
    """

    def __init__(self, capacity: int = 100):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def set(self, key: str, value, ttl: float) -> None:
        # TODO(stage 1): store `value` under `key`; it expires `ttl` seconds from now.
        # TODO(stage 2): once more than `capacity` keys are stored, evict the
        # least recently used one (a get() or set() counts as a use).
        raise NotImplementedError

    def get(self, key: str):
        # TODO(stage 1): the value for `key`, or None if it's missing or expired.
        # TODO(stage 4): count every get() as a hit or a miss for stats().
        raise NotImplementedError

    def get_or_load(self, key: str, loader, ttl: float):
        # TODO(stage 3): return the cached value if present. Otherwise call
        # loader() — a slow call such as a DB query — store its result with
        # `ttl`, and return it. When many threads miss the same key at the
        # same time, loader() must run only ONCE; the others wait for that
        # one load and get its result (single-flight — this is what stops a
        # cache stampede from flattening the DB when a hot key expires).
        raise NotImplementedError

    def invalidate(self, key: str) -> None:
        # TODO(stage 4): drop `key` so the next read misses (e.g. after the
        # product's price changed in the DB).
        raise NotImplementedError

    def stats(self) -> dict:
        # TODO(stage 4): {"hits": int, "misses": int, "hit_ratio": float}
        # (hit_ratio is 0.0 when there were no gets yet).
        raise NotImplementedError
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a4000000-0000-0000-0000-000000000001',
    1,
    'TTL get/set',
    '캐시 값의 수명 — TTL이 지나면 원본을 다시 읽어야 한다',
    $stage$"""Stage 1 — TTL get/set.
학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
"""
import time
from cache import Cache


def main():
    c = Cache(capacity=10)
    assert c.get("missing") is None, "a key that was never set should be a miss (None)"
    c.set("p1", "price=100", ttl=0.3)
    assert c.get("p1") == "price=100", f"expected price=100 before the TTL, got {c.get('p1')}"
    c.set("p2", "price=200", ttl=5.0)
    time.sleep(0.4)
    assert c.get("p1") is None, "p1 should have expired after its 0.3s TTL"
    assert c.get("p2") == "price=200", "p2 has a 5s TTL and should still be cached"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a4000000-0000-0000-0000-000000000001',
    2,
    'LRU eviction',
    '유한한 메모리 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다',
    $stage$"""Stage 2 — LRU eviction.
학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
"""
from cache import Cache


def main():
    c = Cache(capacity=3)
    c.set("a", 1, ttl=60)
    c.set("b", 2, ttl=60)
    c.set("c", 3, ttl=60)
    assert c.get("a") == 1, "a should still be cached (capacity is 3)"  # a is now the most recently used
    c.set("d", 4, ttl=60)  # over capacity: b is the least recently used
    assert c.get("b") is None, "b was the least recently used key and should have been evicted"
    assert c.get("a") == 1, "a was read just before the insert, so it must survive"
    assert c.get("c") == 3, "c should survive — only one key needed to go"
    assert c.get("d") == 4, "the newly inserted key d should be cached"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a4000000-0000-0000-0000-000000000001',
    3,
    'single-flight (cache stampede)',
    'hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만',
    $stage$"""Stage 3 — single-flight (cache stampede).
학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
"""
import threading
import time
from cache import Cache


def main():
    c = Cache(capacity=10)
    loads = []
    loads_lock = threading.Lock()

    def slow_loader():
        with loads_lock:
            loads.append(1)
        time.sleep(0.2)  # a slow DB query
        return "product-42"

    results = []
    errors = []
    start_gate = threading.Barrier(8)

    def reader():
        try:
            start_gate.wait()
            results.append(c.get_or_load("hot", slow_loader, ttl=5.0))
        except Exception as e:  # a crash in a worker thread would otherwise vanish silently
            errors.append(e)

    threads = [threading.Thread(target=reader) for _ in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    if errors:
        raise errors[0]

    assert len(loads) == 1, f"8 concurrent misses on the same key should trigger exactly 1 load, got {len(loads)}"
    assert results == ["product-42"] * 8, f"every caller should get the loaded value, got {results}"
    assert c.get_or_load("hot", slow_loader, ttl=5.0) == "product-42"
    assert len(loads) == 1, "once loaded, the value should come from the cache, not the loader"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a4000000-0000-0000-0000-000000000001',
    4,
    'invalidation + hit ratio',
    '원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다',
    $stage$"""Stage 4 — invalidation + hit ratio.
학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
"""
from cache import Cache


def main():
    c = Cache(capacity=10)
    c.set("p1", "price=100", ttl=60)
    assert c.get("p1") == "price=100"  # hit
    c.invalidate("p1")  # the price changed in the DB
    assert c.get("p1") is None, "an invalidated key should miss"  # miss
    assert c.get("p2") is None  # miss
    c.set("p1", "price=120", ttl=60)
    assert c.get("p1") == "price=120", "after invalidation, the new value should be served"  # hit
    c.invalidate("never-set")  # must not raise

    s = c.stats()
    assert s["hits"] == 2, f"expected 2 hits, got {s['hits']}"
    assert s["misses"] == 2, f"expected 2 misses, got {s['misses']}"
    assert abs(s["hit_ratio"] - 0.5) < 0.01, f"expected hit_ratio 0.5, got {s['hit_ratio']}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
);

-- cache-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a4000000-0000-0000-0000-000000000002',
    'cache-java',
    'Build your own Cache (Java)',
    'java',
    'Cache.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Cache (Java)
 *
 * Implement `Cache` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.function.Supplier;

/** What stats() hands out. hitRatio is 0.0 when there were no gets yet. */
record Stats(long hits, long misses, double hitRatio) {}

/**
 * An in-process read-through cache in front of a slow loader (e.g. the
 * product DB): entries expire after a TTL, the least recently used entry
 * is evicted once `capacity` is reached, and concurrent misses for the
 * same key share a single load instead of all hitting the loader.
 */
public class Cache {
    public Cache() {
        this(100);
    }

    public Cache(int capacity) {
        // TODO(stage 1): store config and set up whatever storage you need.
        throw new UnsupportedOperationException("not implemented");
    }

    public void set(String key, Object value, double ttlSeconds) {
        // TODO(stage 1): store `value` under `key`; it expires `ttlSeconds` from now.
        // TODO(stage 2): once more than `capacity` keys are stored, evict the
        // least recently used one (a get() or set() counts as a use).
        throw new UnsupportedOperationException("not implemented");
    }

    public Object get(String key) {
        // TODO(stage 1): the value for `key`, or null if it's missing or expired.
        // TODO(stage 4): count every get() as a hit or a miss for stats().
        throw new UnsupportedOperationException("not implemented");
    }

    public Object getOrLoad(String key, Supplier<?> loader, double ttlSeconds) {
        // TODO(stage 3): return the cached value if present. Otherwise call
        // loader.get() — a slow call such as a DB query — store its result
        // with `ttlSeconds`, and return it. When many threads miss the same
        // key at the same time, the loader must run only ONCE; the others
        // wait for that one load and get its result (single-flight — this is
        // what stops a cache stampede from flattening the DB when a hot key
        // expires).
        throw new UnsupportedOperationException("not implemented");
    }

    public void invalidate(String key) {
        // TODO(stage 4): drop `key` so the next read misses (e.g. after the
        // product's price changed in the DB).
        throw new UnsupportedOperationException("not implemented");
    }

    public Stats stats() {
        // TODO(stage 4): new Stats(hits, misses, hitRatio)
        // (hitRatio is 0.0 when there were no gets yet).
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a4000000-0000-0000-0000-000000000002',
    1,
    'TTL get/set',
    '캐시 값의 수명 — TTL이 지나면 원본을 다시 읽어야 한다',
    $stage$// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
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
        Cache c = new Cache(10);
        check(c.get("missing") == null, "a key that was never set should be a miss (null)");
        c.set("p1", "price=100", 0.3);
        Object p1 = c.get("p1");
        check("price=100".equals(p1), "expected price=100 before the TTL, got " + p1);
        c.set("p2", "price=200", 5.0);
        Thread.sleep(400);
        check(c.get("p1") == null, "p1 should have expired after its 0.3s TTL");
        check("price=200".equals(c.get("p2")), "p2 has a 5s TTL and should still be cached");
    }
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000002',
    2,
    'LRU eviction',
    '유한한 메모리 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다',
    $stage$// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
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
        Cache c = new Cache(3);
        c.set("a", 1, 60);
        c.set("b", 2, 60);
        c.set("c", 3, 60);
        check(Integer.valueOf(1).equals(c.get("a")), "a should still be cached (capacity is 3)"); // a is now the most recently used
        c.set("d", 4, 60); // over capacity: b is the least recently used
        check(c.get("b") == null, "b was the least recently used key and should have been evicted");
        check(Integer.valueOf(1).equals(c.get("a")), "a was read just before the insert, so it must survive");
        check(Integer.valueOf(3).equals(c.get("c")), "c should survive — only one key needed to go");
        check(Integer.valueOf(4).equals(c.get("d")), "the newly inserted key d should be cached");
    }
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000002',
    3,
    'single-flight (cache stampede)',
    'hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만',
    $stage$// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
        Cache c = new Cache(10);
        AtomicInteger loads = new AtomicInteger();
        Supplier<Object> slowLoader = () -> {
            loads.incrementAndGet();
            try {
                Thread.sleep(200); // a slow DB query
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "product-42";
        };

        List<Object> results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                try {
                    startGate.await();
                    results.add(c.getOrLoad("hot", slowLoader, 5.0));
                } catch (Throwable e) { // a crash in a worker thread would otherwise vanish silently
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        startGate.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(loads.get() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got " + loads.get());
        check(results.equals(Collections.nCopies(8, "product-42")), "every caller should get the loaded value, got " + results);
        check("product-42".equals(c.getOrLoad("hot", slowLoader, 5.0)), "expected product-42 from the cache");
        check(loads.get() == 1, "once loaded, the value should come from the cache, not the loader");
    }
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000002',
    4,
    'invalidation + hit ratio',
    '원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다',
    $stage$// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
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
        Cache c = new Cache(10);
        c.set("p1", "price=100", 60);
        check("price=100".equals(c.get("p1")), "expected price=100"); // hit
        c.invalidate("p1"); // the price changed in the DB
        check(c.get("p1") == null, "an invalidated key should miss"); // miss
        check(c.get("p2") == null, "p2 was never set and should miss"); // miss
        c.set("p1", "price=120", 60);
        check("price=120".equals(c.get("p1")), "after invalidation, the new value should be served"); // hit
        c.invalidate("never-set"); // must not throw

        Stats s = c.stats();
        check(s.hits() == 2, "expected 2 hits, got " + s.hits());
        check(s.misses() == 2, "expected 2 misses, got " + s.misses());
        check(Math.abs(s.hitRatio() - 0.5) < 0.01, "expected hitRatio 0.5, got " + s.hitRatio());
    }
}
$stage$
);

-- cache-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a4000000-0000-0000-0000-000000000003',
    'cache-kotlin',
    'Build your own Cache (Kotlin)',
    'kotlin',
    'Cache.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Cache (Kotlin)
 *
 * Implement `Cache` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** What stats() hands out. hitRatio is 0.0 when there were no gets yet. */
data class Stats(val hits: Long, val misses: Long, val hitRatio: Double)

/**
 * An in-process read-through cache in front of a slow loader (e.g. the
 * product DB): entries expire after a TTL, the least recently used entry
 * is evicted once `capacity` is reached, and concurrent misses for the
 * same key share a single load instead of all hitting the loader.
 */
class Cache(private val capacity: Int = 100) {
    // TODO(stage 1): set up whatever storage you need.

    fun set(key: String, value: Any?, ttlSeconds: Double) {
        // TODO(stage 1): store `value` under `key`; it expires `ttlSeconds` from now.
        // TODO(stage 2): once more than `capacity` keys are stored, evict the
        // least recently used one (a get() or set() counts as a use).
        TODO("not implemented")
    }

    fun get(key: String): Any? {
        // TODO(stage 1): the value for `key`, or null if it's missing or expired.
        // TODO(stage 4): count every get() as a hit or a miss for stats().
        TODO("not implemented")
    }

    fun getOrLoad(key: String, ttlSeconds: Double, loader: () -> Any?): Any? {
        // TODO(stage 3): return the cached value if present. Otherwise call
        // loader() — a slow call such as a DB query — store its result with
        // `ttlSeconds`, and return it. When many threads miss the same key at
        // the same time, loader() must run only ONCE; the others wait for
        // that one load and get its result (single-flight — this is what
        // stops a cache stampede from flattening the DB when a hot key expires).
        TODO("not implemented")
    }

    fun invalidate(key: String) {
        // TODO(stage 4): drop `key` so the next read misses (e.g. after the
        // product's price changed in the DB).
        TODO("not implemented")
    }

    fun stats(): Stats {
        // TODO(stage 4): Stats(hits, misses, hitRatio)
        // (hitRatio is 0.0 when there were no gets yet).
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a4000000-0000-0000-0000-000000000003',
    1,
    'TTL get/set',
    '캐시 값의 수명 — TTL이 지나면 원본을 다시 읽어야 한다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.

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
    val c = Cache(capacity = 10)
    expect(c.get("missing") == null, "a key that was never set should be a miss (null)")
    c.set("p1", "price=100", ttlSeconds = 0.3)
    val p1 = c.get("p1")
    expect(p1 == "price=100", "expected price=100 before the TTL, got $p1")
    c.set("p2", "price=200", ttlSeconds = 5.0)
    Thread.sleep(400)
    expect(c.get("p1") == null, "p1 should have expired after its 0.3s TTL")
    expect(c.get("p2") == "price=200", "p2 has a 5s TTL and should still be cached")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000003',
    2,
    'LRU eviction',
    '유한한 메모리 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.

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
    val c = Cache(capacity = 3)
    c.set("a", 1, ttlSeconds = 60.0)
    c.set("b", 2, ttlSeconds = 60.0)
    c.set("c", 3, ttlSeconds = 60.0)
    expect(c.get("a") == 1, "a should still be cached (capacity is 3)") // a is now the most recently used
    c.set("d", 4, ttlSeconds = 60.0) // over capacity: b is the least recently used
    expect(c.get("b") == null, "b was the least recently used key and should have been evicted")
    expect(c.get("a") == 1, "a was read just before the insert, so it must survive")
    expect(c.get("c") == 3, "c should survive — only one key needed to go")
    expect(c.get("d") == 4, "the newly inserted key d should be cached")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000003',
    3,
    'single-flight (cache stampede)',
    'hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만',
    $stage$@file:JvmName("RunTest")

// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
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
    val c = Cache(capacity = 10)
    val loads = AtomicInteger()
    val slowLoader = {
        loads.incrementAndGet()
        Thread.sleep(200) // a slow DB query
        "product-42"
    }

    val results = Collections.synchronizedList(mutableListOf<Any?>())
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (1..8).map {
        thread {
            try {
                startGate.await()
                results.add(c.getOrLoad("hot", ttlSeconds = 5.0, loader = slowLoader))
            } catch (e: Throwable) { // a crash in a worker thread would otherwise vanish silently
                failure.compareAndSet(null, e)
            }
        }
    }
    startGate.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(loads.get() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got ${loads.get()}")
    expect(results.toList() == List(8) { "product-42" }, "every caller should get the loaded value, got $results")
    expect(c.getOrLoad("hot", ttlSeconds = 5.0, loader = slowLoader) == "product-42", "expected product-42 from the cache")
    expect(loads.get() == 1, "once loaded, the value should come from the cache, not the loader")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000003',
    4,
    'invalidation + hit ratio',
    '원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다',
    $stage$@file:JvmName("RunTest")

// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
import kotlin.math.abs

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
    val c = Cache(capacity = 10)
    c.set("p1", "price=100", ttlSeconds = 60.0)
    expect(c.get("p1") == "price=100", "expected price=100") // hit
    c.invalidate("p1") // the price changed in the DB
    expect(c.get("p1") == null, "an invalidated key should miss") // miss
    expect(c.get("p2") == null, "p2 was never set and should miss") // miss
    c.set("p1", "price=120", ttlSeconds = 60.0)
    expect(c.get("p1") == "price=120", "after invalidation, the new value should be served") // hit
    c.invalidate("never-set") // must not throw

    val s = c.stats()
    expect(s.hits == 2L, "expected 2 hits, got ${s.hits}")
    expect(s.misses == 2L, "expected 2 misses, got ${s.misses}")
    expect(abs(s.hitRatio - 0.5) < 0.01, "expected hitRatio 0.5, got ${s.hitRatio}")
}
$stage$
);

-- cache-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a4000000-0000-0000-0000-000000000004',
    'cache-go',
    'Build your own Cache (Go)',
    'go',
    'cache.go',
    $stub$// SysDrill Build Mode — Build your own Cache (Go)
//
// Implement Cache below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Stats is what Stats() hands out. HitRatio is 0.0 when there were no Gets yet.
type Stats struct {
	Hits     int
	Misses   int
	HitRatio float64
}

// Cache is an in-process read-through cache in front of a slow loader (e.g.
// the product DB): entries expire after a TTL, the least recently used entry
// is evicted once capacity is reached, and concurrent misses for the same
// key share a single load instead of all hitting the loader.
type Cache struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewCache(capacity int) *Cache {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

// Set stores value under key; it expires ttl from now.
func (c *Cache) Set(key string, value any, ttl time.Duration) {
	// TODO(stage 1): store value under key; it expires ttl from now.
	// TODO(stage 2): once more than capacity keys are stored, evict the
	// least recently used one (a Get or Set counts as a use).
	panic("not implemented")
}

// Get returns the value for key, or ok == false if it's missing or expired.
func (c *Cache) Get(key string) (value any, ok bool) {
	// TODO(stage 1): the value for key, or (nil, false) if it's missing or expired.
	// TODO(stage 4): count every Get as a hit or a miss for Stats().
	panic("not implemented")
}

// GetOrLoad returns the cached value for key, loading it with loader on a miss.
func (c *Cache) GetOrLoad(key string, loader func() any, ttl time.Duration) any {
	// TODO(stage 3): return the cached value if present. Otherwise call
	// loader() — a slow call such as a DB query — store its result with
	// ttl, and return it. When many goroutines miss the same key at the
	// same time, loader() must run only ONCE; the others wait for that one
	// load and get its result (single-flight — this is what stops a cache
	// stampede from flattening the DB when a hot key expires).
	panic("not implemented")
}

func (c *Cache) Invalidate(key string) {
	// TODO(stage 4): drop key so the next read misses (e.g. after the
	// product's price changed in the DB).
	panic("not implemented")
}

func (c *Cache) Stats() Stats {
	// TODO(stage 4): Stats{Hits, Misses, HitRatio}
	// (HitRatio is 0.0 when there were no Gets yet).
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a4000000-0000-0000-0000-000000000004',
    1,
    'TTL get/set',
    '캐시 값의 수명 — TTL이 지나면 원본을 다시 읽어야 한다',
    $stage$// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
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
	c := NewCache(10)
	_, ok := c.Get("missing")
	expect(!ok, "a key that was never set should be a miss (ok == false)")
	c.Set("p1", "price=100", 300*time.Millisecond)
	p1, _ := c.Get("p1")
	expect(p1 == "price=100", "expected price=100 before the TTL, got %v", p1)
	c.Set("p2", "price=200", 5*time.Second)
	time.Sleep(400 * time.Millisecond)
	_, ok = c.Get("p1")
	expect(!ok, "p1 should have expired after its 0.3s TTL")
	p2, _ := c.Get("p2")
	expect(p2 == "price=200", "p2 has a 5s TTL and should still be cached")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000004',
    2,
    'LRU eviction',
    '유한한 메모리 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다',
    $stage$// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
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
	c := NewCache(3)
	c.Set("a", 1, 60*time.Second)
	c.Set("b", 2, 60*time.Second)
	c.Set("c", 3, 60*time.Second)
	a, _ := c.Get("a")
	expect(a == 1, "a should still be cached (capacity is 3)") // a is now the most recently used
	c.Set("d", 4, 60*time.Second)                              // over capacity: b is the least recently used
	_, ok := c.Get("b")
	expect(!ok, "b was the least recently used key and should have been evicted")
	a, _ = c.Get("a")
	expect(a == 1, "a was read just before the insert, so it must survive")
	v, _ := c.Get("c")
	expect(v == 3, "c should survive — only one key needed to go")
	d, _ := c.Get("d")
	expect(d == 4, "the newly inserted key d should be cached")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000004',
    3,
    'single-flight (cache stampede)',
    'hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만',
    $stage$// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
package main

import (
	"fmt"
	"os"
	"sync"
	"sync/atomic"
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
	c := NewCache(10)
	var loads atomic.Int32
	slowLoader := func() any {
		loads.Add(1)
		time.Sleep(200 * time.Millisecond) // a slow DB query
		return "product-42"
	}

	var mu sync.Mutex
	var results []any
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	startGate := make(chan struct{})
	for g := 0; g < 8; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			<-startGate
			v := c.GetOrLoad("hot", slowLoader, 5*time.Second)
			mu.Lock()
			results = append(results, v)
			mu.Unlock()
		}()
	}
	close(startGate)
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}

	expect(loads.Load() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got %d", loads.Load())
	allLoaded := len(results) == 8
	for _, v := range results {
		allLoaded = allLoaded && v == "product-42"
	}
	expect(allLoaded, "every caller should get the loaded value, got %v", results)
	v := c.GetOrLoad("hot", slowLoader, 5*time.Second)
	expect(v == "product-42", "expected product-42 from the cache, got %v", v)
	expect(loads.Load() == 1, "once loaded, the value should come from the cache, not the loader")
}
$stage$
),
(
    'a4000000-0000-0000-0000-000000000004',
    4,
    'invalidation + hit ratio',
    '원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다',
    $stage$// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
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
	c := NewCache(10)
	c.Set("p1", "price=100", 60*time.Second)
	v, _ := c.Get("p1") // hit
	expect(v == "price=100", "expected price=100, got %v", v)
	c.Invalidate("p1")   // the price changed in the DB
	_, ok := c.Get("p1") // miss
	expect(!ok, "an invalidated key should miss")
	_, ok = c.Get("p2") // miss
	expect(!ok, "p2 was never set and should miss")
	c.Set("p1", "price=120", 60*time.Second)
	v, _ = c.Get("p1") // hit
	expect(v == "price=120", "after invalidation, the new value should be served")
	c.Invalidate("never-set") // must not panic

	s := c.Stats()
	expect(s.Hits == 2, "expected 2 hits, got %d", s.Hits)
	expect(s.Misses == 2, "expected 2 misses, got %d", s.Misses)
	expect(math.Abs(s.HitRatio-0.5) < 0.01, "expected HitRatio 0.5, got %v", s.HitRatio)
}
$stage$
);

-- idempotency
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a5000000-0000-0000-0000-000000000001',
    'idempotency',
    'Build your own Idempotency Layer',
    'python',
    'idempotency.py',
    $stub$"""
SysDrill Build Mode — Build your own Idempotency Layer

Implement the classes below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class IdempotencyConflictError(Exception):
    """Raised by execute() when a key is reused with a different request."""


class IdempotencyInProgressError(Exception):
    """Raised by execute() when a request with the same key is still running."""


class IdempotencyLayer:
    """Wraps a side-effecting operation (e.g. charging a card through a payment
    gateway) so that retries carrying the same idempotency key — a client
    timing out and resending, a double-clicked button — perform it at most
    once and get the original result back.
    """

    def __init__(self, ttl: float = 86400.0):
        # TODO(stage 1): store config and set up whatever storage you need.
        # TODO(stage 4): keys are kept for `ttl` seconds, then forgotten.
        raise NotImplementedError

    def execute(self, key: str, request, fn):
        # TODO(stage 1): the first call for `key` runs fn() and stores its
        # result; every later call with the same key returns that stored
        # result WITHOUT calling fn again.
        # TODO(stage 2): remember the `request` each key was first used with.
        # The same key with a *different* request (compare by value) is a
        # client bug — raise IdempotencyConflictError instead of replaying.
        # TODO(stage 3): while fn() for a key is still running, another call
        # with that key must neither run fn nor wait — raise
        # IdempotencyInProgressError right away (the client retries later).
        # Don't hold a lock while fn() runs.
        # TODO(stage 4): if fn() raises, store nothing for the key (let the
        # exception propagate) so a retry can try again. Stored keys expire
        # `ttl` seconds after they were stored.
        raise NotImplementedError
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a5000000-0000-0000-0000-000000000001',
    1,
    '결과 재생',
    '같은 멱등성 키로 다시 온 요청은 작업을 다시 하지 않고 처음 결과를 돌려준다',
    $stage$"""Stage 1 — replay the stored result.
학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
"""
from idempotency import IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []

    def charge():
        charges.append(1000)
        return {"charge_id": f"ch_{len(charges)}", "amount": 1000}

    first = layer.execute("order-1", {"amount": 1000}, charge)
    retry = layer.execute("order-1", {"amount": 1000}, charge)
    assert len(charges) == 1, f"a retry with the same key must not charge again, got {len(charges)} charges"
    assert retry == first, f"the retry should get the original result {first}, got {retry}"

    other = layer.execute("order-2", {"amount": 1000}, charge)
    assert len(charges) == 2, "a different key is a different request and should charge"
    assert other["charge_id"] == "ch_2", f"expected ch_2 for the new key, got {other}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a5000000-0000-0000-0000-000000000001',
    2,
    '키 재사용 충돌',
    '같은 키에 다른 요청이 오면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다',
    $stage$"""Stage 2 — same key, different request.
학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
"""
from idempotency import IdempotencyConflictError, IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []

    def charge():
        charges.append(1)
        return f"ch_{len(charges)}"

    layer.execute("order-1", {"amount": 1000, "currency": "KRW"}, charge)
    # a new dict with the same contents is the same request
    replay = layer.execute("order-1", {"amount": 1000, "currency": "KRW"}, charge)
    assert replay == "ch_1", f"an equal request should be replayed, got {replay}"

    try:
        layer.execute("order-1", {"amount": 2000, "currency": "KRW"}, charge)
        assert False, "reusing a key with a different request should raise IdempotencyConflictError"
    except IdempotencyConflictError:
        pass
    assert len(charges) == 1, f"a conflicting request must not charge, got {len(charges)} charges"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a5000000-0000-0000-0000-000000000001',
    3,
    '처리 중 중복',
    '결과가 저장되기 전(처리 중)에 온 중복도 막아야 한다 — 기다리지 않고 거절한다',
    $stage$"""Stage 3 — a duplicate arrives while the first is still running.
학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
"""
import threading
from idempotency import IdempotencyInProgressError, IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []
    started = threading.Event()
    release = threading.Event()

    def slow_charge():  # the payment gateway takes a while to answer
        charges.append("slow")
        started.set()
        release.wait(timeout=5)
        return "ch_1"

    def fast_charge():
        charges.append("fast")
        return "ch_dup"

    first_result = []
    first = threading.Thread(target=lambda: first_result.append(layer.execute("order-1", {"amount": 1000}, slow_charge)))
    first.start()
    assert started.wait(timeout=5), "the first request never started running"

    outcome = []

    def duplicate():
        try:
            outcome.append(layer.execute("order-1", {"amount": 1000}, fast_charge))
        except IdempotencyInProgressError:
            outcome.append("in-progress")
        except Exception as e:
            outcome.append(e)

    dup = threading.Thread(target=duplicate)
    dup.start()
    dup.join(timeout=2)
    blocked = dup.is_alive()
    release.set()
    first.join(timeout=5)
    dup.join(timeout=5)

    assert not blocked, "the duplicate request blocked while the first was running — don't hold a lock while fn runs; reject it instead"
    assert charges == ["slow"], f"a duplicate arriving mid-flight must not charge again, charges were {charges}"
    assert outcome == ["in-progress"], f"the duplicate should get IdempotencyInProgressError, got {outcome}"
    assert first_result == ["ch_1"], f"the first request should still finish normally, got {first_result}"
    assert layer.execute("order-1", {"amount": 1000}, fast_charge) == "ch_1", "once finished, the key should replay ch_1"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a5000000-0000-0000-0000-000000000001',
    4,
    '실패와 보존 기간',
    '실패를 저장하면 재시도가 영원히 실패를 재생한다 — 그리고 키는 보존 기간이 지나면 잊는다',
    $stage$"""Stage 4 — failures and key expiry.
학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
"""
import time
from idempotency import IdempotencyLayer


def main():
    layer = IdempotencyLayer(ttl=0.3)
    attempts = []

    def flaky_charge():
        attempts.append(1)
        if len(attempts) == 1:
            raise ConnectionError("gateway timeout")
        return "ch_1"

    try:
        layer.execute("order-1", {"amount": 1000}, flaky_charge)
        assert False, "the operation's own error should propagate to the caller"
    except ConnectionError:
        pass
    result = layer.execute("order-1", {"amount": 1000}, flaky_charge)
    assert result == "ch_1", f"a retry after a failure should run the operation again, got {result}"
    assert len(attempts) == 2, f"expected 2 attempts (the failure is not stored), got {len(attempts)}"

    time.sleep(0.4)
    again = layer.execute("order-1", {"amount": 5000}, lambda: "ch_new")
    assert again == "ch_new", f"after the ttl the key is forgotten and may be reused, got {again}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
);

-- idempotency-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a5000000-0000-0000-0000-000000000002',
    'idempotency-java',
    'Build your own Idempotency Layer (Java)',
    'java',
    'IdempotencyLayer.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Idempotency Layer (Java)
 *
 * Implement the classes below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;

/** Thrown by execute() when a key is reused with a different request. */
class IdempotencyConflictException extends RuntimeException {
    IdempotencyConflictException(String message) {
        super(message);
    }
}

/** Thrown by execute() when a request with the same key is still running. */
class IdempotencyInProgressException extends RuntimeException {
    IdempotencyInProgressException(String message) {
        super(message);
    }
}

/**
 * Wraps a side-effecting operation (e.g. charging a card through a payment
 * gateway) so that retries carrying the same idempotency key — a client
 * timing out and resending, a double-clicked button — perform it at most
 * once and get the original result back.
 */
public class IdempotencyLayer {
    public IdempotencyLayer() {
        this(86400.0);
    }

    public IdempotencyLayer(double ttlSeconds) {
        // TODO(stage 1): store config and set up whatever storage you need.
        // TODO(stage 4): keys are kept for ttlSeconds, then forgotten.
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T execute(String key, Object request, Callable<T> operation) throws Exception {
        // TODO(stage 1): the first call for `key` runs operation.call() and
        // stores its result; every later call with the same key returns that
        // stored result WITHOUT calling the operation again.
        // TODO(stage 2): remember the `request` each key was first used with.
        // The same key with a *different* request (compare by value with
        // equals()) is a client bug — throw IdempotencyConflictException
        // instead of replaying.
        // TODO(stage 3): while the operation for a key is still running,
        // another call with that key must neither run it nor wait — throw
        // IdempotencyInProgressException right away (the client retries
        // later). Don't hold a lock while the operation runs.
        // TODO(stage 4): if the operation throws, store nothing for the key
        // (let the exception propagate) so a retry can try again. Stored keys
        // expire ttlSeconds after they were stored.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a5000000-0000-0000-0000-000000000002',
    1,
    '결과 재생',
    '같은 멱등성 키로 다시 온 요청은 작업을 다시 하지 않고 처음 결과를 돌려준다',
    $stage$// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    static void run() throws Exception {
        IdempotencyLayer layer = new IdempotencyLayer();
        List<Integer> charges = new ArrayList<>();
        Callable<Map<String, Object>> charge = () -> {
            charges.add(1000);
            return Map.of("charge_id", "ch_" + charges.size(), "amount", 1000);
        };

        Map<String, Object> first = layer.execute("order-1", Map.of("amount", 1000), charge);
        Map<String, Object> retry = layer.execute("order-1", Map.of("amount", 1000), charge);
        check(charges.size() == 1, "a retry with the same key must not charge again, got " + charges.size() + " charges");
        check(first.equals(retry), "the retry should get the original result " + first + ", got " + retry);

        Map<String, Object> other = layer.execute("order-2", Map.of("amount", 1000), charge);
        check(charges.size() == 2, "a different key is a different request and should charge");
        check("ch_2".equals(other.get("charge_id")), "expected ch_2 for the new key, got " + other);
    }
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000002',
    2,
    '키 재사용 충돌',
    '같은 키에 다른 요청이 오면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다',
    $stage$// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    static void run() throws Exception {
        IdempotencyLayer layer = new IdempotencyLayer();
        List<Integer> charges = new ArrayList<>();
        Callable<String> charge = () -> {
            charges.add(1);
            return "ch_" + charges.size();
        };

        layer.execute("order-1", Map.of("amount", 1000, "currency", "KRW"), charge);
        // a new map with the same contents is the same request
        String replay = layer.execute("order-1", Map.of("amount", 1000, "currency", "KRW"), charge);
        check("ch_1".equals(replay), "an equal request should be replayed, got " + replay);

        try {
            layer.execute("order-1", Map.of("amount", 2000, "currency", "KRW"), charge);
            check(false, "reusing a key with a different request should throw IdempotencyConflictException");
        } catch (IdempotencyConflictException e) {
            // expected
        }
        check(charges.size() == 1, "a conflicting request must not charge, got " + charges.size() + " charges");
    }
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000002',
    3,
    '처리 중 중복',
    '결과가 저장되기 전(처리 중)에 온 중복도 막아야 한다 — 기다리지 않고 거절한다',
    $stage$// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        IdempotencyLayer layer = new IdempotencyLayer();
        List<String> charges = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Callable<String> slowCharge = () -> { // the payment gateway takes a while to answer
            charges.add("slow");
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "ch_1";
        };
        Callable<String> fastCharge = () -> {
            charges.add("fast");
            return "ch_dup";
        };

        List<Object> firstResult = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                firstResult.add(layer.execute("order-1", Map.of("amount", 1000), slowCharge));
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
                started.countDown(); // don't make the main thread wait for a start that won't come
            }
        });
        first.start();
        check(started.await(5, TimeUnit.SECONDS), "the first request never started running");
        if (failure.get() != null) throw failure.get();

        List<Object> outcome = Collections.synchronizedList(new ArrayList<>());
        Thread dup = new Thread(() -> {
            try {
                outcome.add(layer.execute("order-1", Map.of("amount", 1000), fastCharge));
            } catch (IdempotencyInProgressException e) {
                outcome.add("in-progress");
            } catch (Exception e) {
                outcome.add(e);
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });
        dup.start();
        dup.join(2000);
        boolean blocked = dup.isAlive();
        release.countDown();
        first.join(5000);
        dup.join(5000);
        if (failure.get() != null) throw failure.get();

        check(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while the operation runs; reject it instead");
        check(charges.equals(List.of("slow")), "a duplicate arriving mid-flight must not charge again, charges were " + charges);
        check(outcome.equals(List.of("in-progress")), "the duplicate should get IdempotencyInProgressException, got " + outcome);
        check(firstResult.equals(List.of("ch_1")), "the first request should still finish normally, got " + firstResult);
        check("ch_1".equals(layer.execute("order-1", Map.of("amount", 1000), fastCharge)), "once finished, the key should replay ch_1");
    }
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000002',
    4,
    '실패와 보존 기간',
    '실패를 저장하면 재시도가 영원히 실패를 재생한다 — 그리고 키는 보존 기간이 지나면 잊는다',
    $stage$// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    static void run() throws Exception {
        IdempotencyLayer layer = new IdempotencyLayer(0.3);
        List<Integer> attempts = new ArrayList<>();
        Callable<String> flakyCharge = () -> {
            attempts.add(1);
            if (attempts.size() == 1) throw new ConnectException("gateway timeout");
            return "ch_1";
        };

        try {
            layer.execute("order-1", Map.of("amount", 1000), flakyCharge);
            check(false, "the operation's own error should propagate to the caller");
        } catch (ConnectException e) {
            // expected
        }
        String result = layer.execute("order-1", Map.of("amount", 1000), flakyCharge);
        check("ch_1".equals(result), "a retry after a failure should run the operation again, got " + result);
        check(attempts.size() == 2, "expected 2 attempts (the failure is not stored), got " + attempts.size());

        Thread.sleep(400);
        String again = layer.execute("order-1", Map.of("amount", 5000), () -> "ch_new");
        check("ch_new".equals(again), "after the ttl the key is forgotten and may be reused, got " + again);
    }
}
$stage$
);

-- idempotency-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a5000000-0000-0000-0000-000000000003',
    'idempotency-kotlin',
    'Build your own Idempotency Layer (Kotlin)',
    'kotlin',
    'IdempotencyLayer.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Idempotency Layer (Kotlin)
 *
 * Implement the classes below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** Thrown by execute() when a key is reused with a different request. */
class IdempotencyConflictException(message: String) : RuntimeException(message)

/** Thrown by execute() when a request with the same key is still running. */
class IdempotencyInProgressException(message: String) : RuntimeException(message)

/**
 * Wraps a side-effecting operation (e.g. charging a card through a payment
 * gateway) so that retries carrying the same idempotency key — a client
 * timing out and resending, a double-clicked button — perform it at most
 * once and get the original result back.
 */
class IdempotencyLayer(
    private val ttlSeconds: Double = 86400.0,
) {
    // TODO(stage 1): set up whatever storage you need.
    // TODO(stage 4): keys are kept for ttlSeconds, then forgotten.

    fun <T> execute(key: String, request: Any, operation: () -> T): T {
        // TODO(stage 1): the first call for `key` runs operation() and stores
        // its result; every later call with the same key returns that stored
        // result WITHOUT calling the operation again.
        // TODO(stage 2): remember the `request` each key was first used with.
        // The same key with a *different* request (compare by value with ==)
        // is a client bug — throw IdempotencyConflictException instead of
        // replaying.
        // TODO(stage 3): while the operation for a key is still running,
        // another call with that key must neither run it nor wait — throw
        // IdempotencyInProgressException right away (the client retries
        // later). Don't hold a lock while the operation runs.
        // TODO(stage 4): if the operation throws, store nothing for the key
        // (let the exception propagate) so a retry can try again. Stored keys
        // expire ttlSeconds after they were stored.
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a5000000-0000-0000-0000-000000000003',
    1,
    '결과 재생',
    '같은 멱등성 키로 다시 온 요청은 작업을 다시 하지 않고 처음 결과를 돌려준다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.

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
    val layer = IdempotencyLayer()
    val charges = mutableListOf<Int>()
    val charge = {
        charges.add(1000)
        mapOf("charge_id" to "ch_${charges.size}", "amount" to 1000)
    }

    val first = layer.execute("order-1", mapOf("amount" to 1000), charge)
    val retry = layer.execute("order-1", mapOf("amount" to 1000), charge)
    expect(charges.size == 1, "a retry with the same key must not charge again, got ${charges.size} charges")
    expect(retry == first, "the retry should get the original result $first, got $retry")

    val other = layer.execute("order-2", mapOf("amount" to 1000), charge)
    expect(charges.size == 2, "a different key is a different request and should charge")
    expect(other["charge_id"] == "ch_2", "expected ch_2 for the new key, got $other")
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000003',
    2,
    '키 재사용 충돌',
    '같은 키에 다른 요청이 오면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.

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
    val layer = IdempotencyLayer()
    val charges = mutableListOf<Int>()
    val charge = {
        charges.add(1)
        "ch_${charges.size}"
    }

    layer.execute("order-1", mapOf("amount" to 1000, "currency" to "KRW"), charge)
    // a new map with the same contents is the same request
    val replay = layer.execute("order-1", mapOf("amount" to 1000, "currency" to "KRW"), charge)
    expect(replay == "ch_1", "an equal request should be replayed, got $replay")

    try {
        layer.execute("order-1", mapOf("amount" to 2000, "currency" to "KRW"), charge)
        expect(false, "reusing a key with a different request should throw IdempotencyConflictException")
    } catch (e: IdempotencyConflictException) {
        // expected
    }
    expect(charges.size == 1, "a conflicting request must not charge, got ${charges.size} charges")
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000003',
    3,
    '처리 중 중복',
    '결과가 저장되기 전(처리 중)에 온 중복도 막아야 한다 — 기다리지 않고 거절한다',
    $stage$@file:JvmName("RunTest")

// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    val layer = IdempotencyLayer()
    val charges = Collections.synchronizedList(mutableListOf<String>())
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)

    val slowCharge = { // the payment gateway takes a while to answer
        charges.add("slow")
        started.countDown()
        release.await(5, TimeUnit.SECONDS)
        "ch_1"
    }
    val fastCharge = {
        charges.add("fast")
        "ch_dup"
    }

    val firstResult = Collections.synchronizedList(mutableListOf<Any>())
    val failure = AtomicReference<Throwable>()
    val first = thread {
        try {
            firstResult.add(layer.execute("order-1", mapOf("amount" to 1000), slowCharge))
        } catch (e: Throwable) {
            failure.compareAndSet(null, e)
            started.countDown() // don't make the main thread wait for a start that won't come
        }
    }
    expect(started.await(5, TimeUnit.SECONDS), "the first request never started running")
    failure.get()?.let { throw it }

    val outcome = Collections.synchronizedList(mutableListOf<Any>())
    val dup = thread {
        try {
            outcome.add(layer.execute("order-1", mapOf("amount" to 1000), fastCharge))
        } catch (e: IdempotencyInProgressException) {
            outcome.add("in-progress")
        } catch (e: Exception) {
            outcome.add(e)
        } catch (e: Throwable) {
            failure.compareAndSet(null, e)
        }
    }
    dup.join(2000)
    val blocked = dup.isAlive
    release.countDown()
    first.join(5000)
    dup.join(5000)
    failure.get()?.let { throw it }

    expect(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while the operation runs; reject it instead")
    expect(charges == listOf("slow"), "a duplicate arriving mid-flight must not charge again, charges were $charges")
    expect(outcome == listOf("in-progress"), "the duplicate should get IdempotencyInProgressException, got $outcome")
    expect(firstResult == listOf("ch_1"), "the first request should still finish normally, got $firstResult")
    expect(layer.execute("order-1", mapOf("amount" to 1000), fastCharge) == "ch_1", "once finished, the key should replay ch_1")
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000003',
    4,
    '실패와 보존 기간',
    '실패를 저장하면 재시도가 영원히 실패를 재생한다 — 그리고 키는 보존 기간이 지나면 잊는다',
    $stage$@file:JvmName("RunTest")

// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
import java.net.ConnectException

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
    val layer = IdempotencyLayer(ttlSeconds = 0.3)
    val attempts = mutableListOf<Int>()
    val flakyCharge = {
        attempts.add(1)
        if (attempts.size == 1) throw ConnectException("gateway timeout")
        "ch_1"
    }

    try {
        layer.execute("order-1", mapOf("amount" to 1000), flakyCharge)
        expect(false, "the operation's own error should propagate to the caller")
    } catch (e: ConnectException) {
        // expected
    }
    val result = layer.execute("order-1", mapOf("amount" to 1000), flakyCharge)
    expect(result == "ch_1", "a retry after a failure should run the operation again, got $result")
    expect(attempts.size == 2, "expected 2 attempts (the failure is not stored), got ${attempts.size}")

    Thread.sleep(400)
    val again = layer.execute("order-1", mapOf("amount" to 5000)) { "ch_new" }
    expect(again == "ch_new", "after the ttl the key is forgotten and may be reused, got $again")
}
$stage$
);

-- idempotency-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a5000000-0000-0000-0000-000000000004',
    'idempotency-go',
    'Build your own Idempotency Layer (Go)',
    'go',
    'idempotency.go',
    $stub$// SysDrill Build Mode — Build your own Idempotency Layer (Go)
//
// Implement IdempotencyLayer below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"time"
)

// ErrIdempotencyConflict is returned by Execute when a key is reused with a different request.
var ErrIdempotencyConflict = errors.New("idempotency key reused with a different request")

// ErrIdempotencyInProgress is returned by Execute when a request with the same key is still running.
var ErrIdempotencyInProgress = errors.New("a request with this idempotency key is still running")

// IdempotencyLayer wraps a side-effecting operation (e.g. charging a card
// through a payment gateway) so that retries carrying the same idempotency
// key — a client timing out and resending, a double-clicked button — perform
// it at most once and get the original result back.
type IdempotencyLayer struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewIdempotencyLayer(ttl time.Duration) *IdempotencyLayer {
	// TODO(stage 1): store config and set up whatever storage you need.
	// TODO(stage 4): keys are kept for ttl, then forgotten.
	panic("not implemented")
}

// Execute runs operation at most once per key and returns its result; a
// non-nil error from operation means it failed.
func (l *IdempotencyLayer) Execute(key string, request map[string]any, operation func() (any, error)) (any, error) {
	// TODO(stage 1): the first call for key runs operation() and stores its
	// result; every later call with the same key returns that stored result
	// WITHOUT calling operation again.
	// TODO(stage 2): remember the request each key was first used with. The
	// same key with a *different* request (compare by value with
	// reflect.DeepEqual) is a client bug — return ErrIdempotencyConflict
	// instead of replaying.
	// TODO(stage 3): while operation for a key is still running, another call
	// with that key must neither run it nor wait — return
	// ErrIdempotencyInProgress right away (the client retries later). Don't
	// hold a lock while operation runs.
	// TODO(stage 4): if operation returns an error, store nothing for the key
	// (return that error) so a retry can try again. Stored keys expire ttl
	// after they were stored.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a5000000-0000-0000-0000-000000000004',
    1,
    '결과 재생',
    '같은 멱등성 키로 다시 온 요청은 작업을 다시 하지 않고 처음 결과를 돌려준다',
    $stage$// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
package main

import (
	"fmt"
	"os"
	"reflect"
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

// must fails the stage on an error the stage did not expect.
func must(result any, err error) any {
	if err != nil {
		panic(failure(fmt.Sprintf("unexpected error: %v", err)))
	}
	return result
}

func stage() {
	layer := NewIdempotencyLayer(24 * time.Hour)
	var charges []int
	charge := func() (any, error) {
		charges = append(charges, 1000)
		return map[string]any{"charge_id": fmt.Sprintf("ch_%d", len(charges)), "amount": 1000}, nil
	}

	first := must(layer.Execute("order-1", map[string]any{"amount": 1000}, charge))
	retry := must(layer.Execute("order-1", map[string]any{"amount": 1000}, charge))
	expect(len(charges) == 1, "a retry with the same key must not charge again, got %d charges", len(charges))
	expect(reflect.DeepEqual(retry, first), "the retry should get the original result %v, got %v", first, retry)

	other := must(layer.Execute("order-2", map[string]any{"amount": 1000}, charge))
	expect(len(charges) == 2, "a different key is a different request and should charge")
	otherMap, _ := other.(map[string]any)
	expect(otherMap["charge_id"] == "ch_2", "expected ch_2 for the new key, got %v", other)
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000004',
    2,
    '키 재사용 충돌',
    '같은 키에 다른 요청이 오면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다',
    $stage$// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
package main

import (
	"errors"
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

// must fails the stage on an error the stage did not expect.
func must(result any, err error) any {
	if err != nil {
		panic(failure(fmt.Sprintf("unexpected error: %v", err)))
	}
	return result
}

func stage() {
	layer := NewIdempotencyLayer(24 * time.Hour)
	var charges []int
	charge := func() (any, error) {
		charges = append(charges, 1)
		return fmt.Sprintf("ch_%d", len(charges)), nil
	}

	must(layer.Execute("order-1", map[string]any{"amount": 1000, "currency": "KRW"}, charge))
	// a new map with the same contents is the same request
	replay := must(layer.Execute("order-1", map[string]any{"amount": 1000, "currency": "KRW"}, charge))
	expect(replay == "ch_1", "an equal request should be replayed, got %v", replay)

	_, err := layer.Execute("order-1", map[string]any{"amount": 2000, "currency": "KRW"}, charge)
	expect(errors.Is(err, ErrIdempotencyConflict), "reusing a key with a different request should return ErrIdempotencyConflict, got %v", err)
	expect(len(charges) == 1, "a conflicting request must not charge, got %d charges", len(charges))
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000004',
    3,
    '처리 중 중복',
    '결과가 저장되기 전(처리 중)에 온 중복도 막아야 한다 — 기다리지 않고 거절한다',
    $stage$// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
package main

import (
	"errors"
	"fmt"
	"os"
	"slices"
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

// must fails the stage on an error the stage did not expect.
func must(result any, err error) any {
	if err != nil {
		panic(failure(fmt.Sprintf("unexpected error: %v", err)))
	}
	return result
}

func stage() {
	layer := NewIdempotencyLayer(24 * time.Hour)
	var mu sync.Mutex // guards charges, firstResult, outcome and crashed
	var charges []string
	started := make(chan struct{})
	release := make(chan struct{})

	slowCharge := func() (any, error) { // the payment gateway takes a while to answer
		mu.Lock()
		charges = append(charges, "slow")
		mu.Unlock()
		close(started)
		select {
		case <-release:
		case <-time.After(5 * time.Second):
		}
		return "ch_1", nil
	}
	fastCharge := func() (any, error) {
		mu.Lock()
		charges = append(charges, "fast")
		mu.Unlock()
		return "ch_dup", nil
	}

	var firstResult, outcome []any
	var crashed any
	// A panic in a goroutine would kill the process before main can report it.
	recordPanic := func() {
		if r := recover(); r != nil {
			mu.Lock()
			if crashed == nil {
				crashed = r
			}
			mu.Unlock()
		}
	}
	firstDone := make(chan struct{})
	go func() {
		defer close(firstDone)
		defer recordPanic()
		result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, slowCharge)
		mu.Lock()
		if err != nil {
			firstResult = append(firstResult, err)
		} else {
			firstResult = append(firstResult, result)
		}
		mu.Unlock()
	}()
	select {
	case <-started:
	case <-firstDone: // finished (or crashed) without ever running slowCharge
	case <-time.After(5 * time.Second):
		expect(false, "the first request never started running")
	}
	mu.Lock()
	firstCrash := crashed
	mu.Unlock()
	if firstCrash != nil {
		panic(firstCrash)
	}

	dupDone := make(chan struct{})
	go func() {
		defer close(dupDone)
		defer recordPanic()
		result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, fastCharge)
		mu.Lock()
		switch {
		case errors.Is(err, ErrIdempotencyInProgress):
			outcome = append(outcome, "in-progress")
		case err != nil:
			outcome = append(outcome, err)
		default:
			outcome = append(outcome, result)
		}
		mu.Unlock()
	}()
	blocked := false
	select {
	case <-dupDone:
	case <-time.After(2 * time.Second):
		blocked = true
	}
	close(release)
	for _, done := range []chan struct{}{firstDone, dupDone} {
		select {
		case <-done:
		case <-time.After(5 * time.Second):
		}
	}

	mu.Lock()
	gotCharges, gotOutcome, gotFirst, gotCrash := slices.Clone(charges), slices.Clone(outcome), slices.Clone(firstResult), crashed
	mu.Unlock()
	if gotCrash != nil {
		panic(gotCrash)
	}
	expect(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while operation runs; reject it instead")
	expect(slices.Equal(gotCharges, []string{"slow"}), "a duplicate arriving mid-flight must not charge again, charges were %v", gotCharges)
	expect(slices.Equal(gotOutcome, []any{"in-progress"}), "the duplicate should get ErrIdempotencyInProgress, got %v", gotOutcome)
	expect(slices.Equal(gotFirst, []any{"ch_1"}), "the first request should still finish normally, got %v", gotFirst)
	again := must(layer.Execute("order-1", map[string]any{"amount": 1000}, fastCharge))
	expect(again == "ch_1", "once finished, the key should replay ch_1")
}
$stage$
),
(
    'a5000000-0000-0000-0000-000000000004',
    4,
    '실패와 보존 기간',
    '실패를 저장하면 재시도가 영원히 실패를 재생한다 — 그리고 키는 보존 기간이 지나면 잊는다',
    $stage$// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
package main

import (
	"errors"
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

// must fails the stage on an error the stage did not expect.
func must(result any, err error) any {
	if err != nil {
		panic(failure(fmt.Sprintf("unexpected error: %v", err)))
	}
	return result
}

var errGatewayTimeout = errors.New("gateway timeout")

func stage() {
	layer := NewIdempotencyLayer(300 * time.Millisecond)
	attempts := 0
	flakyCharge := func() (any, error) {
		attempts++
		if attempts == 1 {
			return nil, errGatewayTimeout
		}
		return "ch_1", nil
	}

	_, err := layer.Execute("order-1", map[string]any{"amount": 1000}, flakyCharge)
	expect(errors.Is(err, errGatewayTimeout), "the operation's own error should propagate to the caller, got %v", err)
	result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, flakyCharge)
	expect(err == nil && result == "ch_1", "a retry after a failure should run the operation again, got %v (error %v)", result, err)
	expect(attempts == 2, "expected 2 attempts (the failure is not stored), got %d", attempts)

	time.Sleep(400 * time.Millisecond)
	again, err := layer.Execute("order-1", map[string]any{"amount": 5000}, func() (any, error) { return "ch_new", nil })
	expect(err == nil && again == "ch_new", "after the ttl the key is forgotten and may be reused, got %v (error %v)", again, err)
}
$stage$
);

-- 학습 개념 → 새 Build 과제
update learning_concepts set related_challenges = $ct$["idempotency", "rate-limiter"]$ct$::jsonb where risk_key = 'MISSING_IDEMPOTENCY';
update learning_concepts set related_challenges = $ct$["idempotency"]$ct$::jsonb where risk_key = 'MISSING_PAYMENT_IDEMPOTENCY';
update learning_concepts set related_challenges = $ct$["cache"]$ct$::jsonb where risk_key = 'MISSING_CACHE_POLICY_SEPARATION';
update learning_concepts set related_challenges = $ct$["cache"]$ct$::jsonb where risk_key = 'MISSING_SINGLE_FLIGHT';
