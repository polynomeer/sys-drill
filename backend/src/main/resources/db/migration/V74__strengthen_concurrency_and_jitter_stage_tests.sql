-- 동시성·지터 단계의 판별력 강화 — queue·event-bus·distributed-lock 4단계, retry-backoff 3단계를
-- Python·Java·Kotlin·Go 네 판 모두에서 바꾼다. 테스트 스크립트는 challenges/ 의 파일 그대로다
-- (이 파일은 그 파일들에서 생성했다). V13–V18·V73 은 이미 적용됐으므로 고치지 않고 덮어쓴다.
--
-- - queue / event-bus 4단계: 20개 → 1000개, 워커 5 → 8, 시작 게이트. Python판은 워커 예외를 main으로
--   올리고 sys.setswitchinterval(1e-6)로 GIL 전환을 잦게 한다.
-- - distributed-lock 4단계: 한 번의 20-스레드 경쟁 → 새 키 200개에서 8-스레드 경쟁을 반복.
-- - retry-backoff 3단계: "지연 값이 서로 다른가"는 지터 없는 지수 지연(10ms, 20ms, …)도 통과했다 →
--   같은 정책을 두 번 돌려 지연 수열이 달라야 통과.
-- Go판의 동시성 판정은 이 스크립트가 아니라 `go run -race`가 맡는다(ADR-0052).

-- challenges/queue/stages/stage4_test.py
update build_stages set test_script = $stage$from queue_impl import Queue
import sys
import threading
# Switch threads every microsecond instead of every 5ms, so an unguarded
# check-then-update actually gets interleaved instead of running to completion.
sys.setswitchinterval(1e-6)
try:
    q = Queue(visibility_timeout=5.0, max_retries=3)
    for i in range(1000):
        q.enqueue(i)

    received = []
    errors = []
    lock = threading.Lock()
    start_gate = threading.Event()

    def worker():
        start_gate.wait()
        try:
            while True:
                msg = q.dequeue()
                if msg is None:
                    break
                with lock:
                    received.append(msg["payload"])
        except Exception as e:  # a crash in a worker thread would otherwise vanish silently
            errors.append(e)

    threads = [threading.Thread(target=worker) for _ in range(8)]
    for t in threads:
        t.start()
    start_gate.set()
    for t in threads:
        t.join()
    if errors:
        raise errors[0]

    assert len(received) == 1000, f"expected 1000 deliveries, got {len(received)}"
    assert sorted(received) == list(range(1000)), "each message should be delivered exactly once across concurrent consumers"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$stage$
where challenge_id = 'e0000000-0000-0000-0000-000000000001' and stage_order = 4;

-- challenges/queue-java/stages/stage4_test.java
update build_stages set test_script = $stage$// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 스레드가
// 동시에 dequeue()해도 각 메시지는 정확히 한 번만 전달돼야 한다).
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        Queue q = new Queue(5.0, 3);
        for (int i = 0; i < 1000; i++) q.enqueue(i);

        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                try {
                    startGate.await();
                    while (true) {
                        Message msg = q.dequeue();
                        if (msg == null) break;
                        received.add((Integer) msg.payload());
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        startGate.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(received.size() == 1000, "expected 1000 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>(received);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 1000; i++) expected.add(i);
        check(sorted.equals(expected), "each message should be delivered exactly once across concurrent consumers");
    }
}
$stage$
where challenge_id = 'e0000000-0000-0000-0000-000000000002' and stage_order = 4;

-- challenges/queue-kotlin/stages/stage4_test.kt
update build_stages set test_script = $stage$@file:JvmName("RunTest")

// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 스레드가
// 동시에 dequeue()해도 각 메시지는 정확히 한 번만 전달돼야 한다).
import java.util.Collections
import java.util.concurrent.CountDownLatch
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
    val q = Queue(visibilityTimeoutSeconds = 5.0, maxRetries = 3)
    for (i in 0 until 1000) q.enqueue(i)

    val received = Collections.synchronizedList(mutableListOf<Int>())
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (1..8).map {
        thread {
            try {
                startGate.await()
                while (true) {
                    val msg = q.dequeue() ?: break
                    received.add(msg.payload as Int)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    startGate.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(received.size == 1000, "expected 1000 deliveries, got ${received.size}")
    expect(received.sorted() == (0 until 1000).toList(), "each message should be delivered exactly once across concurrent consumers")
}
$stage$
where challenge_id = 'e0000000-0000-0000-0000-000000000003' and stage_order = 4;

-- challenges/queue-go/stages/stage4.go
update build_stages set test_script = $stage$// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 고루틴이
// 동시에 Dequeue해도 각 메시지는 정확히 한 번만 전달돼야 한다).
package main

import (
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

func stage() {
	q := NewQueue(5*time.Second, 3)
	for i := 0; i < 1000; i++ {
		q.Enqueue(i)
	}

	var mu sync.Mutex
	var received []int
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
			for {
				msg, ok := q.Dequeue()
				if !ok {
					break
				}
				mu.Lock()
				received = append(received, msg.Payload.(int))
				mu.Unlock()
			}
		}()
	}
	close(startGate)
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}

	expect(len(received) == 1000, "expected 1000 deliveries, got %d", len(received))
	slices.Sort(received)
	expected := make([]int, 1000)
	for i := range expected {
		expected[i] = i
	}
	expect(slices.Equal(received, expected), "each message should be delivered exactly once across concurrent consumers")
}
$stage$
where challenge_id = 'e0000000-0000-0000-0000-000000000004' and stage_order = 4;

-- challenges/event-bus/stages/stage4_test.py
update build_stages set test_script = $stage$from event_bus import EventBus
import sys
import threading
# Switch threads every microsecond instead of every 5ms, so an unguarded
# check-then-update actually gets interleaved instead of running to completion.
sys.setswitchinterval(1e-6)
try:
    bus = EventBus()
    sub = bus.subscribe("orders")
    for i in range(1000):
        bus.publish("orders", i)

    received = []
    errors = []
    lock = threading.Lock()
    start_gate = threading.Event()

    def worker():
        start_gate.wait()
        try:
            while True:
                msg = bus.poll(sub)
                if msg is None:
                    break
                with lock:
                    received.append(msg["payload"])
        except Exception as e:  # a crash in a worker thread would otherwise vanish silently
            errors.append(e)

    threads = [threading.Thread(target=worker) for _ in range(8)]
    for t in threads:
        t.start()
    start_gate.set()
    for t in threads:
        t.join()
    if errors:
        raise errors[0]

    assert len(received) == 1000, f"expected 1000 deliveries, got {len(received)}"
    assert sorted(received) == list(range(1000)), "each event should be delivered exactly once across concurrent pollers"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$stage$
where challenge_id = 'a3000000-0000-0000-0000-000000000001' and stage_order = 4;

-- challenges/event-bus-java/stages/stage4_test.java
update build_stages set test_script = $stage$// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        EventBus bus = new EventBus();
        String sub = bus.subscribe("orders");
        for (int i = 0; i < 1000; i++) bus.publish("orders", i);

        List<Object> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                try {
                    start.await();
                    while (true) {
                        Event msg = bus.poll(sub);
                        if (msg == null) break;
                        received.add(msg.payload());
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        start.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(received.size() == 1000, "expected 1000 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>();
        for (Object p : received) sorted.add((Integer) p);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 1000; i++) expected.add(i);
        check(sorted.equals(expected), "each event should be delivered exactly once across concurrent pollers");
    }
}
$stage$
where challenge_id = 'a3000000-0000-0000-0000-000000000002' and stage_order = 4;

-- challenges/event-bus-kotlin/stages/stage4_test.kt
update build_stages set test_script = $stage$@file:JvmName("RunTest")

// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음.
import java.util.Collections
import java.util.concurrent.CountDownLatch
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
    val bus = EventBus()
    val sub = bus.subscribe("orders")
    for (i in 0 until 1000) bus.publish("orders", i)

    val received = Collections.synchronizedList(mutableListOf<Any?>())
    val failure = AtomicReference<Throwable>()
    val start = CountDownLatch(1)
    val threads = (1..8).map {
        thread {
            try {
                start.await()
                while (true) {
                    val msg = bus.poll(sub) ?: break
                    received.add(msg.payload)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
    }
    start.countDown()
    threads.forEach { it.join() }
    failure.get()?.let { throw it }

    expect(received.size == 1000, "expected 1000 deliveries, got ${received.size}")
    expect(received.map { it as Int }.sorted() == (0 until 1000).toList(), "each event should be delivered exactly once across concurrent pollers")
}
$stage$
where challenge_id = 'a3000000-0000-0000-0000-000000000003' and stage_order = 4;

-- challenges/event-bus-go/stages/stage4.go
update build_stages set test_script = $stage$// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 고루틴이 동시에 poll해도 중복/유실 없음.
package main

import (
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

func stage() {
	bus := NewEventBus(5*time.Second, 3)
	sub := bus.Subscribe("orders")
	for i := 0; i < 1000; i++ {
		bus.Publish("orders", i)
	}

	var mu sync.Mutex
	var received []int
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	start := make(chan struct{})
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
			<-start
			for {
				msg, ok := bus.Poll(sub)
				if !ok {
					return
				}
				mu.Lock()
				received = append(received, msg.Payload.(int))
				mu.Unlock()
			}
		}()
	}
	close(start)
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}

	expect(len(received) == 1000, "expected 1000 deliveries, got %d", len(received))
	slices.Sort(received)
	expected := make([]int, 1000)
	for i := range expected {
		expected[i] = i
	}
	expect(slices.Equal(received, expected), "each event should be delivered exactly once across concurrent pollers")
}
$stage$
where challenge_id = 'a3000000-0000-0000-0000-000000000004' and stage_order = 4;

-- challenges/distributed-lock/stages/stage4_test.py
update build_stages set test_script = $stage$from distributed_lock import DistributedLock, LockStore
import sys
import threading
# Switch threads every microsecond instead of every 5ms, so an unguarded
# check-then-claim actually gets interleaved instead of running to completion.
sys.setswitchinterval(1e-6)
ROUNDS = 200
CONTENDERS = 8
try:
    # One round of 8 simultaneous acquires can easily come out right by luck — the
    # check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
    store = LockStore()
    for round_no in range(ROUNDS):
        results = []
        errors = []
        results_lock = threading.Lock()
        start_gate = threading.Event()

        def try_acquire(i, key=f"resource-{round_no}"):
            try:
                lock = DistributedLock(key, store=store, lease_seconds=5.0)
                start_gate.wait()
                token = lock.acquire(f"owner-{i}")
                if token is not None:
                    with results_lock:
                        results.append(i)
            except Exception as e:  # a crash in a worker thread would otherwise vanish silently
                errors.append(e)

        threads = [threading.Thread(target=try_acquire, args=(i,)) for i in range(CONTENDERS)]
        for t in threads:
            t.start()
        start_gate.set()
        for t in threads:
            t.join()
        if errors:
            raise errors[0]

        assert len(results) == 1, (
            f"round {round_no}: expected exactly 1 successful acquire among {CONTENDERS} concurrent attempts, got {len(results)}"
        )
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$stage$
where challenge_id = 'a1000000-0000-0000-0000-000000000001' and stage_order = 4;

-- challenges/distributed-lock-java/stages/stage4_test.java
update build_stages set test_script = $stage$// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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

    static final int ROUNDS = 200;
    static final int CONTENDERS = 8;

    // One round of 8 simultaneous acquires can easily come out right by luck — the
    // check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
    static void run() throws Throwable {
        LockStore store = new LockStore();
        for (int round = 0; round < ROUNDS; round++) {
            String key = "resource-" + round;
            AtomicInteger successes = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch startGate = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < CONTENDERS; i++) {
                String ownerId = "owner-" + i;
                threads.add(new Thread(() -> {
                    try {
                        DistributedLock lock = new DistributedLock(key, store, 5.0);
                        startGate.await();
                        if (lock.acquire(ownerId) != null) successes.incrementAndGet();
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                }));
            }
            for (Thread t : threads) t.start();
            startGate.countDown();
            for (Thread t : threads) t.join();
            if (failure.get() != null) throw failure.get();
            check(successes.get() == 1, "round " + round + ": expected exactly 1 successful acquire among "
                    + CONTENDERS + " concurrent attempts, got " + successes.get());
        }
    }
}
$stage$
where challenge_id = 'a1000000-0000-0000-0000-000000000002' and stage_order = 4;

-- challenges/distributed-lock-kotlin/stages/stage4_test.kt
update build_stages set test_script = $stage$@file:JvmName("RunTest")

// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
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

const val ROUNDS = 200
const val CONTENDERS = 8

// One round of 8 simultaneous acquires can easily come out right by luck — the
// check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
fun stage() {
    val store = LockStore()
    repeat(ROUNDS) { round ->
        val successes = AtomicInteger()
        val failure = AtomicReference<Throwable>()
        val startGate = CountDownLatch(1)
        val threads = (0 until CONTENDERS).map { i ->
            thread {
                try {
                    val lock = DistributedLock("resource-$round", store = store, leaseSeconds = 5.0)
                    startGate.await()
                    if (lock.acquire("owner-$i") != null) successes.incrementAndGet()
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        startGate.countDown()
        threads.forEach { it.join() }
        failure.get()?.let { throw it }
        expect(
            successes.get() == 1,
            "round $round: expected exactly 1 successful acquire among $CONTENDERS concurrent attempts, got ${successes.get()}",
        )
    }
}
$stage$
where challenge_id = 'a1000000-0000-0000-0000-000000000003' and stage_order = 4;

-- challenges/distributed-lock-go/stages/stage4.go
update build_stages set test_script = $stage$// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
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

const (
	rounds     = 200
	contenders = 8
)

// One round of 8 simultaneous acquires can easily come out right by luck — the
// check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
func stage() {
	store := NewLockStore()
	for round := 0; round < rounds; round++ {
		key := fmt.Sprintf("resource-%d", round)
		var successes atomic.Int64
		var wg sync.WaitGroup
		var once sync.Once
		var crashed any
		startGate := make(chan struct{})
		for i := 0; i < contenders; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				// A panic in a goroutine would kill the process before main can report it.
				defer func() {
					if r := recover(); r != nil {
						once.Do(func() { crashed = r })
					}
				}()
				lock := NewDistributedLock(key, store, 5*time.Second)
				<-startGate
				if _, ok := lock.Acquire(fmt.Sprintf("owner-%d", i)); ok {
					successes.Add(1)
				}
			}()
		}
		close(startGate)
		wg.Wait()
		if crashed != nil {
			panic(crashed)
		}
		expect(successes.Load() == 1, "round %d: expected exactly 1 successful acquire among %d concurrent attempts, got %d",
			round, contenders, successes.Load())
	}
}
$stage$
where challenge_id = 'a1000000-0000-0000-0000-000000000004' and stage_order = 4;

-- challenges/retry-backoff/stages/stage3_test.py
update build_stages set test_script = $stage$from retry_backoff import RetryPolicy, RetryExhaustedError


def record_delays():
    """One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts."""
    recorded_delays = []

    def always_fail():
        raise ValueError("boom")

    policy = RetryPolicy(max_attempts=6, base_delay=0.01, max_delay=10.0, sleep_fn=lambda d: recorded_delays.append(d))
    try:
        policy.execute(always_fail)
    except RetryExhaustedError:
        pass
    return recorded_delays


try:
    first = record_delays()
    second = record_delays()
    for recorded_delays in (first, second):
        assert len(recorded_delays) == 5, f"expected 5 delays between 6 attempts, got {len(recorded_delays)}"
        for i, d in enumerate(recorded_delays):
            cap = min(10.0, 0.01 * (2 ** i))
            assert 0 <= d <= cap, f"delay {i} = {d} should be within [0, {cap}] (exponential backoff cap)"
    # Plain exponential delays (10ms, 20ms, 40ms, ...) already all differ from each other, so
    # "they vary" proves nothing — jitter means two runs don't wait the same amounts.
    assert first != second, f"jitter should randomize the delays, but two runs waited exactly the same: {first}"
    print("RESULT:PASS")
except AssertionError as e:
    print(f"RESULT:FAIL:{e}")
except NotImplementedError:
    print("RESULT:FAIL:not implemented")
except Exception as e:
    print(f"RESULT:FAIL:unexpected error: {e}")
$stage$
where challenge_id = 'a2000000-0000-0000-0000-000000000001' and stage_order = 3;

-- challenges/retry-backoff-java/stages/stage3_test.java
update build_stages set test_script = $stage$// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
import java.util.ArrayList;
import java.util.List;

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

    /** One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts. */
    static List<Double> recordDelays() {
        List<Double> recordedDelays = new ArrayList<>();
        RetryPolicy policy = new RetryPolicy(6, 0.01, 10.0, d -> recordedDelays.add(d));
        try {
            policy.execute(() -> {
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        return recordedDelays;
    }

    static void run() {
        List<Double> first = recordDelays();
        List<Double> second = recordDelays();
        for (List<Double> recordedDelays : List.of(first, second)) {
            check(recordedDelays.size() == 5, "expected 5 delays between 6 attempts, got " + recordedDelays.size());
            for (int i = 0; i < recordedDelays.size(); i++) {
                double d = recordedDelays.get(i);
                double cap = Math.min(10.0, 0.01 * Math.pow(2, i));
                check(0 <= d && d <= cap, "delay " + i + " = " + d + " should be within [0, " + cap + "] (exponential backoff cap)");
            }
        }
        // Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
        // "they vary" proves nothing — jitter means two runs don't wait the same amounts.
        check(!first.equals(second), "jitter should randomize the delays, but two runs waited exactly the same: " + first);
    }
}
$stage$
where challenge_id = 'a2000000-0000-0000-0000-000000000002' and stage_order = 3;

-- challenges/retry-backoff-kotlin/stages/stage3_test.kt
update build_stages set test_script = $stage$@file:JvmName("RunTest")

// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
import kotlin.math.min
import kotlin.math.pow

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

/** One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts. */
fun recordDelays(): List<Double> {
    val recordedDelays = mutableListOf<Double>()
    val policy = RetryPolicy(maxAttempts = 6, baseDelay = 0.01, maxDelay = 10.0, sleepFn = { d -> recordedDelays.add(d) })
    try {
        policy.execute<Unit> { throw IllegalArgumentException("boom") }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    return recordedDelays
}

fun stage() {
    val first = recordDelays()
    val second = recordDelays()
    for (recordedDelays in listOf(first, second)) {
        expect(recordedDelays.size == 5, "expected 5 delays between 6 attempts, got ${recordedDelays.size}")
        recordedDelays.forEachIndexed { i, d ->
            val cap = min(10.0, 0.01 * 2.0.pow(i))
            expect(d in 0.0..cap, "delay $i = $d should be within [0, $cap] (exponential backoff cap)")
        }
    }
    // Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
    // "they vary" proves nothing — jitter means two runs don't wait the same amounts.
    expect(first != second, "jitter should randomize the delays, but two runs waited exactly the same: $first")
}
$stage$
where challenge_id = 'a2000000-0000-0000-0000-000000000003' and stage_order = 3;

-- challenges/retry-backoff-go/stages/stage3.go
update build_stages set test_script = $stage$// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
package main

import (
	"errors"
	"fmt"
	"os"
	"slices"
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

// recordDelays is one full run of a policy that always fails: the 5 delays it
// asked to sleep between 6 attempts.
func recordDelays() []time.Duration {
	var recordedDelays []time.Duration
	alwaysFail := func() (any, error) {
		return nil, errors.New("boom")
	}

	policy := NewRetryPolicy(6, 10*time.Millisecond, 10*time.Second, nil, func(d time.Duration) {
		recordedDelays = append(recordedDelays, d)
	})
	if _, err := policy.Execute(alwaysFail); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}
	return recordedDelays
}

func stage() {
	first := recordDelays()
	second := recordDelays()
	for _, recordedDelays := range [][]time.Duration{first, second} {
		expect(len(recordedDelays) == 5, "expected 5 delays between 6 attempts, got %d", len(recordedDelays))
		for i, d := range recordedDelays {
			limit := min(10*time.Second, 10*time.Millisecond*time.Duration(1<<i))
			expect(0 <= d && d <= limit, "delay %d = %v should be within [0, %v] (exponential backoff cap)", i, d, limit)
		}
	}
	// Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
	// "they vary" proves nothing — jitter means two runs don't wait the same amounts.
	expect(!slices.Equal(first, second), "jitter should randomize the delays, but two runs waited exactly the same: %v", first)
}
$stage$
where challenge_id = 'a2000000-0000-0000-0000-000000000004' and stage_order = 3;
