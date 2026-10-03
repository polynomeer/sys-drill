-- Java·Kotlin·Go 판 — queue, circuit-breaker, distributed-lock, retry-backoff, event-bus
-- (ADR-0051, V72의 rate-limiter 판에 이어). 언어별 판은 slug 접미사(-java/-kotlin/-go)로 구분되는
-- 별도 챌린지이고, 스텁과 스테이지 테스트는 challenges/<slug>/ 의 파일 그대로다(이 파일은 그 파일들에서
-- 생성했다 — BuildStarterCodeTest가 스텁 일치를, BuildLanguageVariantsIntegrationTest가 "스텁은 전부
-- 실패, 모범 답안은 전부 통과"를 고정한다). 스테이지 제목·학습 포인트는 Python 판(V13–V18)과 같고,
-- Python 판처럼 지시문(instructions)은 두지 않는다 — 화면이 spec으로 대신한다.

-- queue-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'e0000000-0000-0000-0000-000000000002',
    'queue-java',
    'Build your own Queue (Java)',
    'java',
    'Queue.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Queue (Java)
 *
 * Implement `Queue` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.List;

/** What dequeue() hands out: the id to ack() it with, plus the payload. */
record Message(String id, Object payload) {}

/**
 * An at-least-once message queue with visibility timeouts (like SQS),
 * not a plain FIFO: a dequeued message stays invisible to other
 * dequeue() calls until it's ack()'d or the visibility timeout expires,
 * at which point it's redelivered — up to maxRetries times before it
 * moves to the dead-letter queue.
 */
public class Queue {
    public Queue() {
        this(5.0, 3);
    }

    public Queue(double visibilityTimeoutSeconds, int maxRetries) {
        // TODO(stage 1): store config and set up whatever storage you need.
        throw new UnsupportedOperationException("not implemented");
    }

    public String enqueue(Object payload) {
        // TODO(stage 1): add a message, return its message id.
        throw new UnsupportedOperationException("not implemented");
    }

    public Message dequeue() {
        // TODO(stage 1): pop the oldest *visible* message (FIFO), or null
        // if nothing is visible. Return new Message(id, payload).
        // TODO(stage 2): once returned, the message must stay invisible to
        // other dequeue() calls until ack()'d or visibilityTimeoutSeconds elapses.
        // TODO(stage 3): if a message's attempts reach maxRetries without
        // being ack'd, move it to the dead-letter queue instead of redelivering.
        // TODO(stage 4): make this safe when called concurrently from
        // multiple threads — no two callers may receive the same message.
        throw new UnsupportedOperationException("not implemented");
    }

    public void ack(String messageId) {
        // TODO(stage 2): permanently remove the message so it's never redelivered.
        throw new UnsupportedOperationException("not implemented");
    }

    public List<Message> deadLetterQueue() {
        // TODO(stage 3): messages that exceeded maxRetries without being ack'd.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'e0000000-0000-0000-0000-000000000002',
    1,
    '기본 FIFO enqueue/dequeue',
    '큐의 기본 순서 보장 — 넣은 순서대로 나와야 한다',
    $stage$// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).
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
        Queue q = new Queue(5.0, 3);
        q.enqueue("a");
        q.enqueue("b");
        q.enqueue("c");
        Message msg1 = q.dequeue();
        Message msg2 = q.dequeue();
        Message msg3 = q.dequeue();
        check("a".equals(msg1.payload()), "expected a, got " + msg1.payload());
        check("b".equals(msg2.payload()), "expected b, got " + msg2.payload());
        check("c".equals(msg3.payload()), "expected c, got " + msg3.payload());
        check(q.dequeue() == null, "queue should be empty");
    }
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000002',
    2,
    'ack / visibility timeout',
    'at-least-once — ack 없이 visibility timeout이 지나면 재전달되고, ack하면 재전달되지 않는다',
    $stage$// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).
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
        Queue q = new Queue(0.3, 3);
        q.enqueue("x");
        Message msg = q.dequeue();
        check(msg != null, "expected a message");
        check(q.dequeue() == null, "in-flight message should not be immediately re-deliverable");
        Thread.sleep(400);
        Message redelivered = q.dequeue();
        check(redelivered != null, "message should be redelivered after visibility timeout without ack");
        check("x".equals(redelivered.payload()), "expected x, got " + redelivered.payload());
        q.ack(redelivered.id());
        check(q.dequeue() == null, "acked message should not be redelivered");
    }
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000002',
    3,
    '최대 재시도 + DLQ',
    'poison message 격리 — maxRetries를 넘기면 재전달을 멈추고 deadLetterQueue()로 옮긴다',
    $stage$// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).
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

    static void run() throws InterruptedException {
        Queue q = new Queue(0.2, 2);
        q.enqueue("y");
        for (int i = 0; i < 2; i++) {
            Message msg = q.dequeue();
            check(msg != null, "expected a message");
            Thread.sleep(300);
        }
        check(q.dequeue() == null, "message should no longer be deliverable after exceeding maxRetries");
        List<Message> dlq = q.deadLetterQueue();
        check(dlq.size() == 1, "expected 1 message in DLQ, got " + dlq.size());
        check("y".equals(dlq.get(0).payload()), "expected y in DLQ, got " + dlq.get(0).payload());
    }
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000002',
    4,
    '동시성 안전성',
    '두 컨슈머가 같은 메시지를 동시에 받으면 안 된다 — dequeue()는 여러 스레드에서 안전해야 한다',
    $stage$// Stage 4 — concurrency safety.
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
        for (int i = 0; i < 20; i++) q.enqueue(i);

        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
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

        check(received.size() == 20, "expected 20 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>(received);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) expected.add(i);
        check(sorted.equals(expected), "each message should be delivered exactly once across concurrent consumers");
    }
}
$stage$
);

-- queue-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'e0000000-0000-0000-0000-000000000003',
    'queue-kotlin',
    'Build your own Queue (Kotlin)',
    'kotlin',
    'Queue.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Queue (Kotlin)
 *
 * Implement `Queue` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** What dequeue() hands out: the id to ack() it with, plus the payload. */
data class Message(val id: String, val payload: Any)

/**
 * An at-least-once message queue with visibility timeouts (like SQS),
 * not a plain FIFO: a dequeued message stays invisible to other
 * dequeue() calls until it's ack()'d or the visibility timeout expires,
 * at which point it's redelivered — up to maxRetries times before it
 * moves to the dead-letter queue.
 */
class Queue(
    private val visibilityTimeoutSeconds: Double = 5.0,
    private val maxRetries: Int = 3,
) {
    // TODO(stage 1): set up whatever storage you need.

    fun enqueue(payload: Any): String {
        // TODO(stage 1): add a message, return its message id.
        TODO("not implemented")
    }

    fun dequeue(): Message? {
        // TODO(stage 1): pop the oldest *visible* message (FIFO), or null
        // if nothing is visible. Return Message(id, payload).
        // TODO(stage 2): once returned, the message must stay invisible to
        // other dequeue() calls until ack()'d or visibilityTimeoutSeconds elapses.
        // TODO(stage 3): if a message's attempts reach maxRetries without
        // being ack'd, move it to deadLetterQueue instead of redelivering.
        // TODO(stage 4): make this safe when called concurrently from
        // multiple threads — no two callers may receive the same message.
        TODO("not implemented")
    }

    fun ack(messageId: String) {
        // TODO(stage 2): permanently remove the message so it's never redelivered.
        TODO("not implemented")
    }

    val deadLetterQueue: List<Message>
        // TODO(stage 3): messages that exceeded maxRetries without being ack'd.
        get() = TODO("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'e0000000-0000-0000-0000-000000000003',
    1,
    '기본 FIFO enqueue/dequeue',
    '큐의 기본 순서 보장 — 넣은 순서대로 나와야 한다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).

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
    q.enqueue("a")
    q.enqueue("b")
    q.enqueue("c")
    val msg1 = q.dequeue()!!
    val msg2 = q.dequeue()!!
    val msg3 = q.dequeue()!!
    expect(msg1.payload == "a", "expected a, got ${msg1.payload}")
    expect(msg2.payload == "b", "expected b, got ${msg2.payload}")
    expect(msg3.payload == "c", "expected c, got ${msg3.payload}")
    expect(q.dequeue() == null, "queue should be empty")
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000003',
    2,
    'ack / visibility timeout',
    'at-least-once — ack 없이 visibility timeout이 지나면 재전달되고, ack하면 재전달되지 않는다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).

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
    val q = Queue(visibilityTimeoutSeconds = 0.3, maxRetries = 3)
    q.enqueue("x")
    val msg = q.dequeue()
    expect(msg != null, "expected a message")
    expect(q.dequeue() == null, "in-flight message should not be immediately re-deliverable")
    Thread.sleep(400)
    val redelivered = q.dequeue()
    expect(redelivered != null, "message should be redelivered after visibility timeout without ack")
    expect(redelivered!!.payload == "x", "expected x, got ${redelivered.payload}")
    q.ack(redelivered.id)
    expect(q.dequeue() == null, "acked message should not be redelivered")
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000003',
    3,
    '최대 재시도 + DLQ',
    'poison message 격리 — maxRetries를 넘기면 재전달을 멈추고 deadLetterQueue로 옮긴다',
    $stage$@file:JvmName("RunTest")

// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).

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
    val q = Queue(visibilityTimeoutSeconds = 0.2, maxRetries = 2)
    q.enqueue("y")
    repeat(2) {
        val msg = q.dequeue()
        expect(msg != null, "expected a message")
        Thread.sleep(300)
    }
    expect(q.dequeue() == null, "message should no longer be deliverable after exceeding maxRetries")
    val dlq = q.deadLetterQueue
    expect(dlq.size == 1, "expected 1 message in DLQ, got ${dlq.size}")
    expect(dlq[0].payload == "y", "expected y in DLQ, got ${dlq[0].payload}")
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000003',
    4,
    '동시성 안전성',
    '두 컨슈머가 같은 메시지를 동시에 받으면 안 된다 — dequeue()는 여러 스레드에서 안전해야 한다',
    $stage$@file:JvmName("RunTest")

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
    for (i in 0 until 20) q.enqueue(i)

    val received = Collections.synchronizedList(mutableListOf<Int>())
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (1..5).map {
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

    expect(received.size == 20, "expected 20 deliveries, got ${received.size}")
    expect(received.sorted() == (0 until 20).toList(), "each message should be delivered exactly once across concurrent consumers")
}
$stage$
);

-- queue-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'e0000000-0000-0000-0000-000000000004',
    'queue-go',
    'Build your own Queue (Go)',
    'go',
    'queue.go',
    $stub$// SysDrill Build Mode — Build your own Queue (Go)
//
// Implement Queue below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Message is what Dequeue hands out: the ID to Ack it with, plus the payload.
type Message struct {
	ID      string
	Payload any
}

// Queue is an at-least-once message queue with visibility timeouts (like
// SQS), not a plain FIFO: a dequeued message stays invisible to other
// Dequeue calls until it's Ack'd or the visibility timeout expires, at which
// point it's redelivered — up to maxRetries times before it moves to the
// dead-letter queue.
type Queue struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewQueue(visibilityTimeout time.Duration, maxRetries int) *Queue {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

// Enqueue adds a message and returns its message ID.
func (q *Queue) Enqueue(payload any) string {
	// TODO(stage 1): add a message, return its message ID.
	panic("not implemented")
}

// Dequeue returns the oldest visible message, or ok == false if none is visible.
func (q *Queue) Dequeue() (msg Message, ok bool) {
	// TODO(stage 1): pop the oldest *visible* message (FIFO), or return
	// (Message{}, false) if nothing is visible.
	// TODO(stage 2): once returned, the message must stay invisible to
	// other Dequeue calls until Ack'd or visibilityTimeout elapses.
	// TODO(stage 3): if a message's attempts reach maxRetries without
	// being Ack'd, move it to the dead-letter queue instead of redelivering.
	// TODO(stage 4): make this safe when called concurrently from
	// multiple goroutines — no two callers may receive the same message.
	panic("not implemented")
}

func (q *Queue) Ack(messageID string) {
	// TODO(stage 2): permanently remove the message so it's never redelivered.
	panic("not implemented")
}

// DeadLetterQueue returns the messages that exceeded maxRetries without being Ack'd.
func (q *Queue) DeadLetterQueue() []Message {
	// TODO(stage 3): messages that exceeded maxRetries without being Ack'd.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'e0000000-0000-0000-0000-000000000004',
    1,
    '기본 FIFO enqueue/dequeue',
    '큐의 기본 순서 보장 — 넣은 순서대로 나와야 한다',
    $stage$// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).
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
	q := NewQueue(5*time.Second, 3)
	q.Enqueue("a")
	q.Enqueue("b")
	q.Enqueue("c")
	msg1, _ := q.Dequeue()
	msg2, _ := q.Dequeue()
	msg3, _ := q.Dequeue()
	expect(msg1.Payload == "a", "expected a, got %v", msg1.Payload)
	expect(msg2.Payload == "b", "expected b, got %v", msg2.Payload)
	expect(msg3.Payload == "c", "expected c, got %v", msg3.Payload)
	_, ok := q.Dequeue()
	expect(!ok, "queue should be empty")
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000004',
    2,
    'ack / visibility timeout',
    'at-least-once — ack 없이 visibility timeout이 지나면 재전달되고, ack하면 재전달되지 않는다',
    $stage$// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).
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
	q := NewQueue(300*time.Millisecond, 3)
	q.Enqueue("x")
	_, ok := q.Dequeue()
	expect(ok, "expected a message")
	_, ok = q.Dequeue()
	expect(!ok, "in-flight message should not be immediately re-deliverable")
	time.Sleep(400 * time.Millisecond)
	redelivered, ok := q.Dequeue()
	expect(ok, "message should be redelivered after visibility timeout without ack")
	expect(redelivered.Payload == "x", "expected x, got %v", redelivered.Payload)
	q.Ack(redelivered.ID)
	_, ok = q.Dequeue()
	expect(!ok, "acked message should not be redelivered")
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000004',
    3,
    '최대 재시도 + DLQ',
    'poison message 격리 — maxRetries를 넘기면 재전달을 멈추고 DeadLetterQueue()로 옮긴다',
    $stage$// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).
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
	q := NewQueue(200*time.Millisecond, 2)
	q.Enqueue("y")
	for i := 0; i < 2; i++ {
		_, ok := q.Dequeue()
		expect(ok, "expected a message")
		time.Sleep(300 * time.Millisecond)
	}
	_, ok := q.Dequeue()
	expect(!ok, "message should no longer be deliverable after exceeding maxRetries")
	dlq := q.DeadLetterQueue()
	expect(len(dlq) == 1, "expected 1 message in DLQ, got %d", len(dlq))
	expect(dlq[0].Payload == "y", "expected y in DLQ, got %v", dlq[0].Payload)
}
$stage$
),
(
    'e0000000-0000-0000-0000-000000000004',
    4,
    '동시성 안전성',
    '두 컨슈머가 같은 메시지를 동시에 받으면 안 된다 — Dequeue()는 여러 고루틴에서 안전해야 한다',
    $stage$// Stage 4 — concurrency safety.
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
	for i := 0; i < 20; i++ {
		q.Enqueue(i)
	}

	var mu sync.Mutex
	var received []int
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	startGate := make(chan struct{})
	for g := 0; g < 5; g++ {
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

	expect(len(received) == 20, "expected 20 deliveries, got %d", len(received))
	slices.Sort(received)
	expected := make([]int, 20)
	for i := range expected {
		expected[i] = i
	}
	expect(slices.Equal(received, expected), "each message should be delivered exactly once across concurrent consumers")
}
$stage$
);

-- circuit-breaker-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'f0000000-0000-0000-0000-000000000002',
    'circuit-breaker-java',
    'Build your own Circuit Breaker (Java)',
    'java',
    'CircuitBreaker.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Java)
 *
 * Implement `CircuitBreaker` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;

enum State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException extends RuntimeException {
    CircuitOpenException(String message) {
        super(message);
    }
}

/**
 * Wraps calls to a possibly-failing function (e.g. an external API) and
 * stops calling it once it's clearly broken, instead of letting every
 * caller wait out its own timeout.
 */
public class CircuitBreaker {

    public CircuitBreaker() {
        this(3, 5.0);
    }

    public CircuitBreaker(int failureThreshold, double recoveryTimeoutSeconds) {
        // TODO(stage 1): store config and start CLOSED.
        throw new UnsupportedOperationException("not implemented");
    }

    public State state() {
        // TODO(stage 1): CLOSED | OPEN | HALF_OPEN.
        // TODO(stage 3): once OPEN and recoveryTimeoutSeconds has elapsed since the
        // trip, reading state should report HALF_OPEN (a real trial call
        // hasn't necessarily happened yet — this is a state *transition*,
        // not just a label).
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T call(Callable<T> fn) throws Exception {
        // TODO(stage 1): while CLOSED, call fn and return its result.
        // TODO(stage 2): count consecutive failures; once failureThreshold is
        // reached, trip to OPEN. While OPEN, throw CircuitOpenException immediately
        // WITHOUT calling fn — that's the whole point (fail fast).
        // TODO(stage 3): once state has moved to HALF_OPEN (see state()),
        // the next call() is a *trial*: run fn for real, and if it
        // succeeds, recover to CLOSED (reset the failure count too).
        // TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        // and restart the recoveryTimeoutSeconds countdown from now.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'f0000000-0000-0000-0000-000000000002',
    1,
    '정상 동작 (CLOSED)',
    'pass-through 기본 동작 — 호출이 성공하는 동안 breaker는 CLOSED를 유지하고 결과를 그대로 반환한다',
    $stage$// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).
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
        CircuitBreaker cb = new CircuitBreaker(3, 1.0);
        Object result = cb.call(() -> 42);
        check(Integer.valueOf(42).equals(result), "expected 42, got " + result);
        check(cb.state() == State.CLOSED, "expected CLOSED, got " + cb.state());
        for (int i = 0; i < 5; i++) {
            Object ok = cb.call(() -> "ok");
            check("ok".equals(ok), "expected \"ok\", got " + ok);
        }
        check(cb.state() == State.CLOSED, "expected CLOSED after 5 successful calls, got " + cb.state());
    }
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000002',
    2,
    'failure threshold 도달 시 OPEN',
    'fail fast — OPEN 상태에서는 실제 함수를 호출하지 않고 즉시 CircuitOpenException을 던진다',
    $stage$// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.
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
        int[] calls = {0};
        Callable<String> flaky = () -> {
            calls[0]++;
            throw new IllegalArgumentException("boom");
        };

        CircuitBreaker cb = new CircuitBreaker(3, 10.0);
        for (int i = 0; i < 3; i++) {
            try {
                cb.call(flaky);
            } catch (IllegalArgumentException e) {
                // the wrapped function's own failure — expected
            }
        }
        check(cb.state() == State.OPEN, "expected OPEN after 3 failures, got " + cb.state());
        check(calls[0] == 3, "expected the function to run 3 times, got " + calls[0]);

        try {
            cb.call(flaky);
            check(false, "expected CircuitOpenException while OPEN");
        } catch (CircuitOpenException e) {
            // fail fast — expected
        }
        check(calls[0] == 3, "the underlying function must not run while the circuit is OPEN (fail fast)");
    }
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000002',
    3,
    'recovery timeout 경과 후 HALF_OPEN 복구',
    '언제, 어떻게 재시도를 허용할지 — recovery timeout이 지나면 HALF_OPEN으로 전이하고, 시도가 성공하면 CLOSED로 복구한다',
    $stage$// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).
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
        Callable<String> fail = () -> {
            throw new IllegalArgumentException("boom");
        };

        CircuitBreaker cb = new CircuitBreaker(1, 0.3);
        try {
            cb.call(fail);
        } catch (IllegalArgumentException e) {
            // expected
        }
        check(cb.state() == State.OPEN, "expected OPEN after 1 failure, got " + cb.state());

        Thread.sleep(400);
        check(cb.state() == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got " + cb.state());

        Object result = cb.call(() -> "recovered");
        check("recovered".equals(result), "expected \"recovered\", got " + result);
        check(cb.state() == State.CLOSED, "a successful HALF_OPEN trial should recover to CLOSED, got " + cb.state());
    }
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000002',
    4,
    'HALF_OPEN 시도 실패 시 재차단',
    '복구 판단이 틀렸을 때의 대응 — 시도 호출이 실패하면 다시 OPEN으로 돌아가고 recovery timeout이 그 시점부터 재시작된다',
    $stage$// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).
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
        Callable<String> fail = () -> {
            throw new IllegalArgumentException("boom");
        };

        CircuitBreaker cb = new CircuitBreaker(1, 0.3);
        try {
            cb.call(fail);
        } catch (IllegalArgumentException e) {
            // expected
        }
        Thread.sleep(400);
        check(cb.state() == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got " + cb.state());

        try {
            cb.call(fail);
        } catch (IllegalArgumentException e) {
            // the trial call's own failure — expected
        }
        check(cb.state() == State.OPEN, "a failed HALF_OPEN trial should return to OPEN, got " + cb.state());

        try {
            cb.call(() -> "should not run");
            check(false, "expected CircuitOpenException immediately after a failed trial (timeout must reset)");
        } catch (CircuitOpenException e) {
            // fail fast — expected
        }
    }
}
$stage$
);

-- circuit-breaker-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'f0000000-0000-0000-0000-000000000003',
    'circuit-breaker-kotlin',
    'Build your own Circuit Breaker (Kotlin)',
    'kotlin',
    'CircuitBreaker.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Kotlin)
 *
 * Implement `CircuitBreaker` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

enum class State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException(message: String) : RuntimeException(message)

/**
 * Wraps calls to a possibly-failing function (e.g. an external API) and
 * stops calling it once it's clearly broken, instead of letting every
 * caller wait out its own timeout.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val recoveryTimeout: Double = 5.0, // seconds
) {
    init {
        // TODO(stage 1): store config and start CLOSED.
        TODO("not implemented")
    }

    val state: State
        // TODO(stage 1): CLOSED | OPEN | HALF_OPEN.
        // TODO(stage 3): once OPEN and recoveryTimeout has elapsed since the
        // trip, reading state should report HALF_OPEN (a real trial call
        // hasn't necessarily happened yet — this is a state *transition*,
        // not just a label).
        get() = TODO("not implemented")

    fun <T> call(fn: () -> T): T {
        // TODO(stage 1): while CLOSED, call fn() and return its result.
        // TODO(stage 2): count consecutive failures; once failureThreshold is
        // reached, trip to OPEN. While OPEN, throw CircuitOpenException immediately
        // WITHOUT calling fn — that's the whole point (fail fast).
        // TODO(stage 3): once state has moved to HALF_OPEN (see `state`),
        // the next call() is a *trial*: run fn for real, and if it
        // succeeds, recover to CLOSED (reset the failure count too).
        // TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        // and restart the recoveryTimeout countdown from now.
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'f0000000-0000-0000-0000-000000000003',
    1,
    '정상 동작 (CLOSED)',
    'pass-through 기본 동작 — 호출이 성공하는 동안 breaker는 CLOSED를 유지하고 결과를 그대로 반환한다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).

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
    val cb = CircuitBreaker(failureThreshold = 3, recoveryTimeout = 1.0)
    val result = cb.call { 42 }
    expect(result == 42, "expected 42, got $result")
    expect(cb.state == State.CLOSED, "expected CLOSED, got ${cb.state}")
    repeat(5) {
        val ok = cb.call { "ok" }
        expect(ok == "ok", "expected \"ok\", got $ok")
    }
    expect(cb.state == State.CLOSED, "expected CLOSED after 5 successful calls, got ${cb.state}")
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000003',
    2,
    'failure threshold 도달 시 OPEN',
    'fail fast — OPEN 상태에서는 실제 함수를 호출하지 않고 즉시 CircuitOpenException을 던진다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.

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
    var calls = 0
    val flaky: () -> String = {
        calls++
        throw IllegalArgumentException("boom")
    }

    val cb = CircuitBreaker(failureThreshold = 3, recoveryTimeout = 10.0)
    repeat(3) {
        try {
            cb.call(flaky)
        } catch (e: IllegalArgumentException) {
            // the wrapped function's own failure — expected
        }
    }
    expect(cb.state == State.OPEN, "expected OPEN after 3 failures, got ${cb.state}")
    expect(calls == 3, "expected the function to run 3 times, got $calls")

    try {
        cb.call(flaky)
        expect(false, "expected CircuitOpenException while OPEN")
    } catch (e: CircuitOpenException) {
        // fail fast — expected
    }
    expect(calls == 3, "the underlying function must not run while the circuit is OPEN (fail fast)")
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000003',
    3,
    'recovery timeout 경과 후 HALF_OPEN 복구',
    '언제, 어떻게 재시도를 허용할지 — recovery timeout이 지나면 HALF_OPEN으로 전이하고, 시도가 성공하면 CLOSED로 복구한다',
    $stage$@file:JvmName("RunTest")

// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).

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
    val fail: () -> String = { throw IllegalArgumentException("boom") }

    val cb = CircuitBreaker(failureThreshold = 1, recoveryTimeout = 0.3)
    try {
        cb.call(fail)
    } catch (e: IllegalArgumentException) {
        // expected
    }
    expect(cb.state == State.OPEN, "expected OPEN after 1 failure, got ${cb.state}")

    Thread.sleep(400)
    expect(cb.state == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got ${cb.state}")

    val result = cb.call { "recovered" }
    expect(result == "recovered", "expected \"recovered\", got $result")
    expect(cb.state == State.CLOSED, "a successful HALF_OPEN trial should recover to CLOSED, got ${cb.state}")
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000003',
    4,
    'HALF_OPEN 시도 실패 시 재차단',
    '복구 판단이 틀렸을 때의 대응 — 시도 호출이 실패하면 다시 OPEN으로 돌아가고 recovery timeout이 그 시점부터 재시작된다',
    $stage$@file:JvmName("RunTest")

// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).

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
    val fail: () -> String = { throw IllegalArgumentException("boom") }

    val cb = CircuitBreaker(failureThreshold = 1, recoveryTimeout = 0.3)
    try {
        cb.call(fail)
    } catch (e: IllegalArgumentException) {
        // expected
    }
    Thread.sleep(400)
    expect(cb.state == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got ${cb.state}")

    try {
        cb.call(fail)
    } catch (e: IllegalArgumentException) {
        // the trial call's own failure — expected
    }
    expect(cb.state == State.OPEN, "a failed HALF_OPEN trial should return to OPEN, got ${cb.state}")

    try {
        cb.call { "should not run" }
        expect(false, "expected CircuitOpenException immediately after a failed trial (timeout must reset)")
    } catch (e: CircuitOpenException) {
        // fail fast — expected
    }
}
$stage$
);

-- circuit-breaker-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'f0000000-0000-0000-0000-000000000004',
    'circuit-breaker-go',
    'Build your own Circuit Breaker (Go)',
    'go',
    'circuit_breaker.go',
    $stub$// SysDrill Build Mode — Build your own Circuit Breaker (Go)
//
// Implement CircuitBreaker below across 4 stages (see README.md).
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

type State string

const (
	StateClosed   State = "CLOSED"
	StateOpen     State = "OPEN"
	StateHalfOpen State = "HALF_OPEN"
)

// ErrCircuitOpen is what Call returns when the breaker is OPEN — the wrapped
// function must not run.
var ErrCircuitOpen = errors.New("circuit open")

// CircuitBreaker wraps calls to a possibly-failing function (e.g. an external
// API) and stops calling it once it's clearly broken, instead of letting every
// caller wait out its own timeout.
type CircuitBreaker struct {
	// TODO(stage 1): fields for the config and the current state.
}

func NewCircuitBreaker(failureThreshold int, recoveryTimeout time.Duration) *CircuitBreaker {
	// TODO(stage 1): store config and start StateClosed.
	panic("not implemented")
}

func (cb *CircuitBreaker) State() State {
	// TODO(stage 1): StateClosed | StateOpen | StateHalfOpen.
	// TODO(stage 3): once OPEN and recoveryTimeout has elapsed since the
	// trip, reading the state should report StateHalfOpen (a real trial call
	// hasn't necessarily happened yet — this is a state *transition*,
	// not just a label).
	panic("not implemented")
}

func (cb *CircuitBreaker) Call(fn func() (any, error)) (any, error) {
	// TODO(stage 1): while CLOSED, call fn and return its result and error.
	// TODO(stage 2): count consecutive failures (fn returning a non-nil error);
	// once failureThreshold is reached, trip to OPEN. While OPEN, return
	// ErrCircuitOpen immediately WITHOUT calling fn — that's the whole point
	// (fail fast).
	// TODO(stage 3): once the state has moved to HALF_OPEN (see State), the
	// next Call is a *trial*: run fn for real, and if it succeeds, recover
	// to CLOSED (reset the failure count too).
	// TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN and
	// restart the recoveryTimeout countdown from now.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'f0000000-0000-0000-0000-000000000004',
    1,
    '정상 동작 (CLOSED)',
    'pass-through 기본 동작 — 호출이 성공하는 동안 breaker는 CLOSED를 유지하고 결과를 그대로 반환한다',
    $stage$// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).
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
	cb := NewCircuitBreaker(3, time.Second)
	result, err := cb.Call(func() (any, error) { return 42, nil })
	if err != nil {
		panic(err)
	}
	expect(result == 42, "expected 42, got %v", result)
	expect(cb.State() == StateClosed, "expected CLOSED, got %s", cb.State())
	for i := 0; i < 5; i++ {
		ok, err := cb.Call(func() (any, error) { return "ok", nil })
		if err != nil {
			panic(err)
		}
		expect(ok == "ok", "expected \"ok\", got %v", ok)
	}
	expect(cb.State() == StateClosed, "expected CLOSED after 5 successful calls, got %s", cb.State())
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000004',
    2,
    'failure threshold 도달 시 OPEN',
    'fail fast — OPEN 상태에서는 실제 함수를 호출하지 않고 즉시 ErrCircuitOpen을 반환한다',
    $stage$// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.
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

var errBoom = errors.New("boom")

func stage() {
	calls := 0
	flaky := func() (any, error) {
		calls++
		return nil, errBoom
	}

	cb := NewCircuitBreaker(3, 10*time.Second)
	for i := 0; i < 3; i++ {
		// The wrapped function's own error is expected; anything else is not.
		if _, err := cb.Call(flaky); err != nil && !errors.Is(err, errBoom) {
			panic(err)
		}
	}
	expect(cb.State() == StateOpen, "expected OPEN after 3 failures, got %s", cb.State())
	expect(calls == 3, "expected the function to run 3 times, got %d", calls)

	_, err := cb.Call(flaky)
	expect(errors.Is(err, ErrCircuitOpen), "expected ErrCircuitOpen while OPEN, got %v", err)
	expect(calls == 3, "the underlying function must not run while the circuit is OPEN (fail fast)")
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000004',
    3,
    'recovery timeout 경과 후 HALF_OPEN 복구',
    '언제, 어떻게 재시도를 허용할지 — recovery timeout이 지나면 HALF_OPEN으로 전이하고, 시도가 성공하면 CLOSED로 복구한다',
    $stage$// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).
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

var errBoom = errors.New("boom")

func stage() {
	fail := func() (any, error) { return nil, errBoom }

	cb := NewCircuitBreaker(1, 300*time.Millisecond)
	if _, err := cb.Call(fail); err != nil && !errors.Is(err, errBoom) {
		panic(err)
	}
	expect(cb.State() == StateOpen, "expected OPEN after 1 failure, got %s", cb.State())

	time.Sleep(400 * time.Millisecond)
	expect(cb.State() == StateHalfOpen, "expected HALF_OPEN after the recovery timeout elapsed, got %s", cb.State())

	result, err := cb.Call(func() (any, error) { return "recovered", nil })
	if err != nil {
		panic(err)
	}
	expect(result == "recovered", "expected \"recovered\", got %v", result)
	expect(cb.State() == StateClosed, "a successful HALF_OPEN trial should recover to CLOSED, got %s", cb.State())
}
$stage$
),
(
    'f0000000-0000-0000-0000-000000000004',
    4,
    'HALF_OPEN 시도 실패 시 재차단',
    '복구 판단이 틀렸을 때의 대응 — 시도 호출이 실패하면 다시 OPEN으로 돌아가고 recovery timeout이 그 시점부터 재시작된다',
    $stage$// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).
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

var errBoom = errors.New("boom")

func stage() {
	fail := func() (any, error) { return nil, errBoom }

	cb := NewCircuitBreaker(1, 300*time.Millisecond)
	if _, err := cb.Call(fail); err != nil && !errors.Is(err, errBoom) {
		panic(err)
	}
	time.Sleep(400 * time.Millisecond)
	expect(cb.State() == StateHalfOpen, "expected HALF_OPEN after the recovery timeout elapsed, got %s", cb.State())

	// The trial call's own error is expected; anything else is not.
	if _, err := cb.Call(fail); err != nil && !errors.Is(err, errBoom) {
		panic(err)
	}
	expect(cb.State() == StateOpen, "a failed HALF_OPEN trial should return to OPEN, got %s", cb.State())

	_, err := cb.Call(func() (any, error) { return "should not run", nil })
	expect(errors.Is(err, ErrCircuitOpen), "expected ErrCircuitOpen immediately after a failed trial (timeout must reset), got %v", err)
}
$stage$
);

-- distributed-lock-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a1000000-0000-0000-0000-000000000002',
    'distributed-lock-java',
    'Build your own Distributed Lock (Java)',
    'java',
    'DistributedLock.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Distributed Lock (Java)
 *
 * Implement `LockStore` and `DistributedLock` below across 4 stages (see
 * README.md). Keep the class and method names as-is — the stage tests call
 * them directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/**
 * A minimal shared key -> (owner, expiry, fencing token) store, shared by
 * every DistributedLock constructed with the same LockStore object. Passing
 * the same store to two DistributedLock instances is how the stages simulate
 * "multiple processes/instances talking to the same external lock service
 * (e.g. Redis)" without needing a real one.
 */
class LockStore {
    public LockStore() {
        // TODO(stage 1): set up whatever storage you need.
    }

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public Long tryAcquire(String key, String ownerId, double leaseSeconds) {
        // TODO(stage 1): if `key` is free, claim it for `ownerId` and return
        // a fencing token. If it's already held by someone whose lease hasn't
        // expired, return null.
        // TODO(stage 2): a lease expires `leaseSeconds` after it was
        // acquired — after that, the key is free again even without tryRelease().
        // TODO(stage 3): each successful acquisition must get a fencing token
        // strictly greater than every token issued before it, even across
        // different owners and even after the key was released/expired.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean tryRelease(String key, String ownerId, long token) {
        // TODO(stage 1): release `key` only if `ownerId`+`token` match the
        // current holder; return whether it actually released anything.
        // TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        // after its lease already expired and someone else acquired the
        // lock) must NOT be able to release the current holder's lock.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isLocked(String key) {
        // TODO(stage 1): whether `key` is currently held by an unexpired lease.
        throw new UnsupportedOperationException("not implemented");
    }
}

public class DistributedLock {
    private final String key;
    private final LockStore store;
    private final double leaseSeconds;

    public DistributedLock(String key) {
        this(key, new LockStore(), 5.0);
    }

    /** Stage 4: callers may pass a *shared* store. */
    public DistributedLock(String key, LockStore store) {
        this(key, store, 5.0);
    }

    public DistributedLock(String key, LockStore store, double leaseSeconds) {
        this.key = key;
        this.store = store;
        this.leaseSeconds = leaseSeconds;
    }

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public Long acquire(String ownerId) {
        // TODO(stage 1): delegate to store.tryAcquire(...).
        // TODO(stage 4): make this safe when called concurrently — only one
        // of many simultaneous callers for the same key may succeed.
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean release(String ownerId, long fencingToken) {
        // TODO(stage 1): delegate to store.tryRelease(...).
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isLocked() {
        // TODO(stage 1): delegate to store.isLocked(...).
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a1000000-0000-0000-0000-000000000002',
    1,
    'mutual exclusion',
    '기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다',
    $stage$// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.
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
        LockStore store = new LockStore();
        DistributedLock lockA = new DistributedLock("resource-1", store, 5.0);
        DistributedLock lockB = new DistributedLock("resource-1", store, 5.0);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        check(lockA.isLocked(), "the lock should report locked while owner-a holds it");

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB == null, "owner-b should not acquire while owner-a holds the lock");

        boolean released = lockA.release("owner-a", tokenA);
        check(released, "owner-a should be able to release its own lock");

        Long tokenB2 = lockB.acquire("owner-b");
        check(tokenB2 != null, "owner-b should acquire after owner-a releases");
    }
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000002',
    2,
    'lease/TTL 만료',
    'release 없이도 lease가 지나면 락이 풀려야 하는 이유',
    $stage$// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.
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
        LockStore store = new LockStore();
        DistributedLock lockA = new DistributedLock("resource-1", store, 0.3);
        DistributedLock lockB = new DistributedLock("resource-1", store, 0.3);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        check(lockB.acquire("owner-b") == null, "owner-b should not acquire before the lease expires");

        Thread.sleep(400);
        check(!lockA.isLocked(), "the lock should report unlocked once the lease has expired");

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB != null, "owner-b should acquire once owner-a's lease has expired without release");
    }
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000002',
    3,
    'fencing token',
    '오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유',
    $stage$// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.
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
        LockStore store = new LockStore();
        DistributedLock lockA = new DistributedLock("resource-1", store, 0.3);
        DistributedLock lockB = new DistributedLock("resource-1", store, 5.0);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        Thread.sleep(400); // owner-a stalls (e.g. GC pause) past its own lease

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB != null, "owner-b should acquire after owner-a's lease expired");
        check(tokenB > tokenA, "fencing tokens must increase monotonically across acquisitions");

        // owner-a wakes up late and tries to release with its now-stale token
        boolean released = lockA.release("owner-a", tokenA);
        check(!released, "a stale owner/token pair must not be able to release the current holder's lock");
        check(lockB.isLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt");
    }
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000002',
    4,
    '동시성',
    '여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공',
    $stage$// Stage 4 — concurrency.
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

    static void run() throws Throwable {
        LockStore store = new LockStore();
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String ownerId = "owner-" + i;
            threads.add(new Thread(() -> {
                try {
                    DistributedLock lock = new DistributedLock("resource-1", store, 5.0);
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
        check(successes.get() == 1,
                "expected exactly 1 successful acquire among 20 concurrent attempts, got " + successes.get());
    }
}
$stage$
);

-- distributed-lock-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a1000000-0000-0000-0000-000000000003',
    'distributed-lock-kotlin',
    'Build your own Distributed Lock (Kotlin)',
    'kotlin',
    'DistributedLock.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Distributed Lock (Kotlin)
 *
 * Implement `LockStore` and `DistributedLock` below across 4 stages (see
 * README.md). Keep the class and member names as-is — the stage tests call
 * them directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/**
 * A minimal shared key -> (owner, expiry, fencing token) store, shared by
 * every DistributedLock constructed with the same LockStore object. Passing
 * the same store to two DistributedLock instances is how the stages simulate
 * "multiple processes/instances talking to the same external lock service
 * (e.g. Redis)" without needing a real one.
 */
class LockStore {
    // TODO(stage 1): set up whatever storage you need.

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    fun tryAcquire(key: String, ownerId: String, leaseSeconds: Double): Long? {
        // TODO(stage 1): if `key` is free, claim it for `ownerId` and return
        // a fencing token. If it's already held by someone whose lease hasn't
        // expired, return null.
        // TODO(stage 2): a lease expires `leaseSeconds` after it was
        // acquired — after that, the key is free again even without tryRelease().
        // TODO(stage 3): each successful acquisition must get a fencing token
        // strictly greater than every token issued before it, even across
        // different owners and even after the key was released/expired.
        TODO("not implemented")
    }

    fun tryRelease(key: String, ownerId: String, token: Long): Boolean {
        // TODO(stage 1): release `key` only if `ownerId`+`token` match the
        // current holder; return whether it actually released anything.
        // TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
        // after its lease already expired and someone else acquired the
        // lock) must NOT be able to release the current holder's lock.
        TODO("not implemented")
    }

    fun isLocked(key: String): Boolean {
        // TODO(stage 1): whether `key` is currently held by an unexpired lease.
        TODO("not implemented")
    }
}

class DistributedLock(
    private val key: String,
    // Stage 4: callers may pass a *shared* store.
    private val store: LockStore = LockStore(),
    private val leaseSeconds: Double = 5.0,
) {
    /** Returns a fencing token, or null if the lock wasn't acquired. */
    fun acquire(ownerId: String): Long? {
        // TODO(stage 1): delegate to store.tryAcquire(...).
        // TODO(stage 4): make this safe when called concurrently — only one
        // of many simultaneous callers for the same key may succeed.
        TODO("not implemented")
    }

    fun release(ownerId: String, fencingToken: Long): Boolean {
        // TODO(stage 1): delegate to store.tryRelease(...).
        TODO("not implemented")
    }

    fun isLocked(): Boolean {
        // TODO(stage 1): delegate to store.isLocked(...).
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a1000000-0000-0000-0000-000000000003',
    1,
    'mutual exclusion',
    '기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.

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
    val store = LockStore()
    val lockA = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    expect(lockA.isLocked(), "the lock should report locked while owner-a holds it")

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB == null, "owner-b should not acquire while owner-a holds the lock")

    val released = lockA.release("owner-a", tokenA!!)
    expect(released, "owner-a should be able to release its own lock")

    val tokenB2 = lockB.acquire("owner-b")
    expect(tokenB2 != null, "owner-b should acquire after owner-a releases")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000003',
    2,
    'lease/TTL 만료',
    'release 없이도 lease가 지나면 락이 풀려야 하는 이유',
    $stage$@file:JvmName("RunTest")

// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.

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
    val store = LockStore()
    val lockA = DistributedLock("resource-1", store = store, leaseSeconds = 0.3)
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 0.3)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    expect(lockB.acquire("owner-b") == null, "owner-b should not acquire before the lease expires")

    Thread.sleep(400)
    expect(!lockA.isLocked(), "the lock should report unlocked once the lease has expired")

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB != null, "owner-b should acquire once owner-a's lease has expired without release")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000003',
    3,
    'fencing token',
    '오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유',
    $stage$@file:JvmName("RunTest")

// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.

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
    val store = LockStore()
    val lockA = DistributedLock("resource-1", store = store, leaseSeconds = 0.3)
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    Thread.sleep(400) // owner-a stalls (e.g. GC pause) past its own lease

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB != null, "owner-b should acquire after owner-a's lease expired")
    expect(tokenB!! > tokenA!!, "fencing tokens must increase monotonically across acquisitions")

    // owner-a wakes up late and tries to release with its now-stale token
    val released = lockA.release("owner-a", tokenA)
    expect(!released, "a stale owner/token pair must not be able to release the current holder's lock")
    expect(lockB.isLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000003',
    4,
    '동시성',
    '여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공',
    $stage$@file:JvmName("RunTest")

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

fun stage() {
    val store = LockStore()
    val successes = AtomicInteger()
    val failure = AtomicReference<Throwable>()
    val startGate = CountDownLatch(1)
    val threads = (0 until 20).map { i ->
        thread {
            try {
                val lock = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)
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
        "expected exactly 1 successful acquire among 20 concurrent attempts, got ${successes.get()}",
    )
}
$stage$
);

-- distributed-lock-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a1000000-0000-0000-0000-000000000004',
    'distributed-lock-go',
    'Build your own Distributed Lock (Go)',
    'go',
    'distributed_lock.go',
    $stub$// SysDrill Build Mode — Build your own Distributed Lock (Go)
//
// Implement LockStore and DistributedLock below across 4 stages (see
// README.md). Keep the type, function and method names as-is — the stage
// tests call them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// LockStore is a minimal shared key -> (owner, expiry, fencing token) store,
// shared by every DistributedLock constructed with the same *LockStore.
// Passing the same store to two DistributedLocks is how the stages simulate
// "multiple processes/instances talking to the same external lock service
// (e.g. Redis)" without needing a real one.
type LockStore struct {
	// TODO(stage 1): add whatever storage you need.
}

func NewLockStore() *LockStore {
	// TODO(stage 1): initialize that storage.
	return &LockStore{}
}

// TryAcquire returns a fencing token and true, or (0, false) if the lock
// wasn't acquired.
func (s *LockStore) TryAcquire(key, ownerID string, lease time.Duration) (int64, bool) {
	// TODO(stage 1): if key is free, claim it for ownerID and return a
	// fencing token. If it's already held by someone whose lease hasn't
	// expired, return (0, false).
	// TODO(stage 2): a lease expires `lease` after it was acquired — after
	// that, the key is free again even without TryRelease.
	// TODO(stage 3): each successful acquisition must get a fencing token
	// strictly greater than every token issued before it, even across
	// different owners and even after the key was released/expired.
	panic("not implemented")
}

func (s *LockStore) TryRelease(key, ownerID string, token int64) bool {
	// TODO(stage 1): release key only if ownerID+token match the current
	// holder; return whether it actually released anything.
	// TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
	// after its lease already expired and someone else acquired the lock)
	// must NOT be able to release the current holder's lock.
	panic("not implemented")
}

func (s *LockStore) IsLocked(key string) bool {
	// TODO(stage 1): whether key is currently held by an unexpired lease.
	panic("not implemented")
}

type DistributedLock struct {
	key   string
	store *LockStore
	lease time.Duration
}

// NewDistributedLock builds a lock handle for key. Stage 4: callers may pass
// a *shared* store; nil means a fresh LockStore of its own.
func NewDistributedLock(key string, store *LockStore, lease time.Duration) *DistributedLock {
	if store == nil {
		store = NewLockStore()
	}
	return &DistributedLock{key: key, store: store, lease: lease}
}

// Acquire returns a fencing token and true, or (0, false) if the lock
// wasn't acquired.
func (l *DistributedLock) Acquire(ownerID string) (int64, bool) {
	// TODO(stage 1): delegate to l.store.TryAcquire(...).
	// TODO(stage 4): make this safe when called concurrently — only one of
	// many simultaneous callers for the same key may succeed.
	panic("not implemented")
}

func (l *DistributedLock) Release(ownerID string, fencingToken int64) bool {
	// TODO(stage 1): delegate to l.store.TryRelease(...).
	panic("not implemented")
}

func (l *DistributedLock) IsLocked() bool {
	// TODO(stage 1): delegate to l.store.IsLocked(...).
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a1000000-0000-0000-0000-000000000004',
    1,
    'mutual exclusion',
    '기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다',
    $stage$// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.
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
	store := NewLockStore()
	lockA := NewDistributedLock("resource-1", store, 5*time.Second)
	lockB := NewDistributedLock("resource-1", store, 5*time.Second)

	tokenA, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	expect(lockA.IsLocked(), "the lock should report locked while owner-a holds it")

	_, ok = lockB.Acquire("owner-b")
	expect(!ok, "owner-b should not acquire while owner-a holds the lock")

	released := lockA.Release("owner-a", tokenA)
	expect(released, "owner-a should be able to release its own lock")

	_, ok = lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire after owner-a releases")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000004',
    2,
    'lease/TTL 만료',
    'release 없이도 lease가 지나면 락이 풀려야 하는 이유',
    $stage$// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.
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
	store := NewLockStore()
	lockA := NewDistributedLock("resource-1", store, 300*time.Millisecond)
	lockB := NewDistributedLock("resource-1", store, 300*time.Millisecond)

	_, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	_, ok = lockB.Acquire("owner-b")
	expect(!ok, "owner-b should not acquire before the lease expires")

	time.Sleep(400 * time.Millisecond)
	expect(!lockA.IsLocked(), "the lock should report unlocked once the lease has expired")

	_, ok = lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire once owner-a's lease has expired without release")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000004',
    3,
    'fencing token',
    '오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유',
    $stage$// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.
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
	store := NewLockStore()
	lockA := NewDistributedLock("resource-1", store, 300*time.Millisecond)
	lockB := NewDistributedLock("resource-1", store, 5*time.Second)

	tokenA, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	time.Sleep(400 * time.Millisecond) // owner-a stalls (e.g. GC pause) past its own lease

	tokenB, ok := lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire after owner-a's lease expired")
	expect(tokenB > tokenA, "fencing tokens must increase monotonically across acquisitions")

	// owner-a wakes up late and tries to release with its now-stale token
	released := lockA.Release("owner-a", tokenA)
	expect(!released, "a stale owner/token pair must not be able to release the current holder's lock")
	expect(lockB.IsLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt")
}
$stage$
),
(
    'a1000000-0000-0000-0000-000000000004',
    4,
    '동시성',
    '여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공',
    $stage$// Stage 4 — concurrency.
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

func stage() {
	store := NewLockStore()
	var successes atomic.Int64
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	startGate := make(chan struct{})
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			lock := NewDistributedLock("resource-1", store, 5*time.Second)
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
	expect(successes.Load() == 1, "expected exactly 1 successful acquire among 20 concurrent attempts, got %d", successes.Load())
}
$stage$
);

-- retry-backoff-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a2000000-0000-0000-0000-000000000002',
    'retry-backoff-java',
    'Build your own Retry/Backoff Middleware (Java)',
    'java',
    'RetryPolicy.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Java)
 *
 * Implement `RetryPolicy` and `RetryBudget` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;
import java.util.function.DoubleConsumer;

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException extends RuntimeException {
    RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * A shared token bucket that caps the *total* number of retries across
 * every RetryPolicy that shares it — protects a downstream dependency from
 * a retry storm even when many independent callers are each individually
 * retrying. Pass the same RetryBudget instance to multiple RetryPolicy
 * instances to share it (stage 4).
 */
class RetryBudget {
    RetryBudget() {
        this(10);
    }

    RetryBudget(int capacity) {
        // TODO(stage 4): store the starting capacity.
        throw new UnsupportedOperationException("not implemented");
    }

    boolean tryConsume() {
        // TODO(stage 4): if a token is available, consume it and return true.
        // If the budget is exhausted, return false (and consume nothing).
        throw new UnsupportedOperationException("not implemented");
    }
}

public class RetryPolicy {
    public RetryPolicy() {
        this(5, 0.01, 1.0, null, null);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, null, sleepFn);
    }

    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, maxDelay, null, sleepFn);
    }

    /** Stage 4: callers may pass a *shared* budget. */
    public RetryPolicy(int maxAttempts, double baseDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        this(maxAttempts, baseDelay, 1.0, budget, sleepFn);
    }

    /**
     * Delays are in seconds. `budget` may be null (no shared budget). `sleepFn`
     * receives each delay in seconds; null means "really sleep".
     */
    public RetryPolicy(int maxAttempts, double baseDelay, double maxDelay, RetryBudget budget, DoubleConsumer sleepFn) {
        // TODO(stage 1): store config. Default sleepFn to a real sleep if null
        // (tests pass their own sleepFn so they don't have to actually wait).
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T execute(Callable<T> fn) {
        // TODO(stage 1): call fn.call(). On success, return its result
        // immediately. On failure (an exception), retry up to maxAttempts total
        // calls, then throw RetryExhaustedException.
        // TODO(stage 3): between attempts, call sleepFn.accept(delay) where
        // delay grows exponentially with the attempt number (baseDelay *
        // 2^attempt, capped at maxDelay) *with jitter* — don't use the
        // exact exponential value, pick randomly within [0, cappedValue]
        // ("full jitter") so many simultaneous retriers don't all retry at
        // the exact same moment (thundering herd).
        // TODO(stage 4): if a budget was provided, call budget.tryConsume()
        // before each retry (not before the first attempt). If it returns
        // false, stop retrying immediately (throw RetryExhaustedException) even
        // if maxAttempts hasn't been reached yet.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a2000000-0000-0000-0000-000000000002',
    1,
    '기본 재시도 동작',
    '실패 시 재시도, 성공하면 즉시 반환',
    $stage$// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.
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
        int[] attempts = {0};
        RetryPolicy policy = new RetryPolicy(5, 0.001, d -> {});
        String result = policy.execute(() -> {
            attempts[0]++;
            if (attempts[0] < 3) throw new IllegalArgumentException("boom");
            return "success";
        });
        check("success".equals(result), "expected success, got " + result);
        check(attempts[0] == 3, "expected exactly 3 attempts (2 failures + 1 success), got " + attempts[0]);
    }
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000002',
    2,
    '재시도 소진',
    'maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음',
    $stage$// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음.
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
        int[] attempts = {0};
        RetryPolicy policy = new RetryPolicy(4, 0.001, d -> {});
        try {
            policy.execute(() -> {
                attempts[0]++;
                throw new IllegalArgumentException("boom");
            });
            check(false, "expected RetryExhaustedException once maxAttempts is exceeded");
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attempts[0] == 4, "expected exactly 4 attempts (maxAttempts), got " + attempts[0]);
    }
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000002',
    3,
    'exponential backoff + jitter',
    '지수적으로 커지는 대기 시간과 thundering herd를 막는 지터',
    $stage$// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
import java.util.ArrayList;
import java.util.HashSet;
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

    static void run() {
        List<Double> recordedDelays = new ArrayList<>();
        RetryPolicy policy = new RetryPolicy(6, 0.01, 10.0, d -> recordedDelays.add(d));
        try {
            policy.execute(() -> {
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }

        check(recordedDelays.size() == 5, "expected 5 delays between 6 attempts, got " + recordedDelays.size());
        for (int i = 0; i < recordedDelays.size(); i++) {
            double d = recordedDelays.get(i);
            double cap = Math.min(10.0, 0.01 * Math.pow(2, i));
            check(0 <= d && d <= cap, "delay " + i + " = " + d + " should be within [0, " + cap + "] (exponential backoff cap)");
        }
        check(new HashSet<>(recordedDelays).size() > 1, "jitter should make delays vary, not all be identical");
    }
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000002',
    4,
    'retry budget',
    '여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제',
    $stage$// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.
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
        RetryBudget budget = new RetryBudget(2);

        int[] attemptsP1 = {0};
        RetryPolicy policy1 = new RetryPolicy(10, 0.001, budget, d -> {});
        try {
            policy1.execute(() -> {
                attemptsP1[0]++;
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attemptsP1[0] < 10, "a shared retry budget should cut retries short before maxAttempts is reached");

        int[] attemptsP2 = {0};
        RetryPolicy policy2 = new RetryPolicy(10, 0.001, budget, d -> {});
        try {
            policy2.execute(() -> {
                attemptsP2[0]++;
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attemptsP2[0] <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all");
    }
}
$stage$
);

-- retry-backoff-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a2000000-0000-0000-0000-000000000003',
    'retry-backoff-kotlin',
    'Build your own Retry/Backoff Middleware (Kotlin)',
    'kotlin',
    'RetryPolicy.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Retry/Backoff Middleware (Kotlin)
 *
 * Implement `RetryPolicy` and `RetryBudget` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** Thrown by execute() when every attempt failed. */
class RetryExhaustedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * A shared token bucket that caps the *total* number of retries across
 * every RetryPolicy that shares it — protects a downstream dependency from
 * a retry storm even when many independent callers are each individually
 * retrying. Pass the same RetryBudget instance to multiple RetryPolicy
 * instances to share it (stage 4).
 */
class RetryBudget(capacity: Int = 10) {
    init {
        // TODO(stage 4): store the starting capacity.
        TODO("not implemented")
    }

    fun tryConsume(): Boolean {
        // TODO(stage 4): if a token is available, consume it and return true.
        // If the budget is exhausted, return false (and consume nothing).
        TODO("not implemented")
    }
}

/**
 * Delays are in seconds. `budget` may be null (no shared budget) — stage 4
 * passes a *shared* one. `sleepFn` receives each delay in seconds; null
 * means "really sleep".
 */
class RetryPolicy(
    maxAttempts: Int = 5,
    baseDelay: Double = 0.01,
    maxDelay: Double = 1.0,
    budget: RetryBudget? = null,
    sleepFn: ((Double) -> Unit)? = null,
) {
    init {
        // TODO(stage 1): store config. Default sleepFn to a real sleep if null
        // (tests pass their own sleepFn so they don't have to actually wait).
        TODO("not implemented")
    }

    fun <T> execute(fn: () -> T): T {
        // TODO(stage 1): call fn(). On success, return its result immediately.
        // On failure (an exception), retry up to maxAttempts total calls, then
        // throw RetryExhaustedException.
        // TODO(stage 3): between attempts, call sleepFn(delay) where delay
        // grows exponentially with the attempt number (baseDelay * 2^attempt,
        // capped at maxDelay) *with jitter* — don't use the exact exponential
        // value, pick randomly within [0, cappedValue] ("full jitter") so many
        // simultaneous retriers don't all retry at the exact same moment
        // (thundering herd).
        // TODO(stage 4): if a budget was provided, call budget.tryConsume()
        // before each retry (not before the first attempt). If it returns
        // false, stop retrying immediately (throw RetryExhaustedException) even
        // if maxAttempts hasn't been reached yet.
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a2000000-0000-0000-0000-000000000003',
    1,
    '기본 재시도 동작',
    '실패 시 재시도, 성공하면 즉시 반환',
    $stage$@file:JvmName("RunTest")

// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.

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
    var attempts = 0
    val policy = RetryPolicy(maxAttempts = 5, baseDelay = 0.001, sleepFn = { })
    val result = policy.execute {
        attempts++
        if (attempts < 3) throw IllegalArgumentException("boom")
        "success"
    }
    expect(result == "success", "expected success, got $result")
    expect(attempts == 3, "expected exactly 3 attempts (2 failures + 1 success), got $attempts")
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000003',
    2,
    '재시도 소진',
    'maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음',
    $stage$@file:JvmName("RunTest")

// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음.

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
    var attempts = 0
    val policy = RetryPolicy(maxAttempts = 4, baseDelay = 0.001, sleepFn = { })
    try {
        policy.execute<Unit> {
            attempts++
            throw IllegalArgumentException("boom")
        }
        expect(false, "expected RetryExhaustedException once maxAttempts is exceeded")
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attempts == 4, "expected exactly 4 attempts (maxAttempts), got $attempts")
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000003',
    3,
    'exponential backoff + jitter',
    '지수적으로 커지는 대기 시간과 thundering herd를 막는 지터',
    $stage$@file:JvmName("RunTest")

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

fun stage() {
    val recordedDelays = mutableListOf<Double>()
    val policy = RetryPolicy(maxAttempts = 6, baseDelay = 0.01, maxDelay = 10.0, sleepFn = { d -> recordedDelays.add(d) })
    try {
        policy.execute<Unit> { throw IllegalArgumentException("boom") }
    } catch (e: RetryExhaustedException) {
        // expected
    }

    expect(recordedDelays.size == 5, "expected 5 delays between 6 attempts, got ${recordedDelays.size}")
    recordedDelays.forEachIndexed { i, d ->
        val cap = min(10.0, 0.01 * 2.0.pow(i))
        expect(d in 0.0..cap, "delay $i = $d should be within [0, $cap] (exponential backoff cap)")
    }
    expect(recordedDelays.toSet().size > 1, "jitter should make delays vary, not all be identical")
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000003',
    4,
    'retry budget',
    '여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제',
    $stage$@file:JvmName("RunTest")

// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.

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
    val budget = RetryBudget(capacity = 2)

    var attemptsP1 = 0
    val policy1 = RetryPolicy(maxAttempts = 10, baseDelay = 0.001, budget = budget, sleepFn = { })
    try {
        policy1.execute<Unit> {
            attemptsP1++
            throw IllegalArgumentException("boom")
        }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attemptsP1 < 10, "a shared retry budget should cut retries short before maxAttempts is reached")

    var attemptsP2 = 0
    val policy2 = RetryPolicy(maxAttempts = 10, baseDelay = 0.001, budget = budget, sleepFn = { })
    try {
        policy2.execute<Unit> {
            attemptsP2++
            throw IllegalArgumentException("boom")
        }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attemptsP2 <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all")
}
$stage$
);

-- retry-backoff-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a2000000-0000-0000-0000-000000000004',
    'retry-backoff-go',
    'Build your own Retry/Backoff Middleware (Go)',
    'go',
    'retry_backoff.go',
    $stub$// SysDrill Build Mode — Build your own Retry/Backoff Middleware (Go)
//
// Implement RetryPolicy and RetryBudget below across 4 stages (see README.md).
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

// ErrRetryExhausted is what Execute returns when every attempt failed
// (check it with errors.Is — you may wrap the last attempt's error too).
var ErrRetryExhausted = errors.New("retry exhausted")

// RetryBudget is a shared token bucket that caps the *total* number of
// retries across every RetryPolicy that shares it — protects a downstream
// dependency from a retry storm even when many independent callers are each
// individually retrying. Pass the same *RetryBudget to multiple RetryPolicy
// instances to share it (stage 4).
type RetryBudget struct {
	// TODO(stage 4): fields.
}

func NewRetryBudget(capacity int) *RetryBudget {
	// TODO(stage 4): store the starting capacity.
	panic("not implemented")
}

func (b *RetryBudget) TryConsume() bool {
	// TODO(stage 4): if a token is available, consume it and return true.
	// If the budget is exhausted, return false (and consume nothing).
	panic("not implemented")
}

type RetryPolicy struct {
	// TODO(stage 1): fields.
}

// NewRetryPolicy builds a policy. budget may be nil (no shared budget) —
// stage 4 passes a *shared* one. sleep receives each delay; nil means
// time.Sleep.
func NewRetryPolicy(maxAttempts int, baseDelay, maxDelay time.Duration, budget *RetryBudget, sleep func(time.Duration)) *RetryPolicy {
	// TODO(stage 1): store config. Default sleep to time.Sleep if nil
	// (tests pass their own sleep so they don't have to actually wait).
	panic("not implemented")
}

func (p *RetryPolicy) Execute(fn func() (any, error)) (any, error) {
	// TODO(stage 1): call fn(). On success (nil error), return its result
	// immediately. On failure, retry up to maxAttempts total calls, then
	// return an error that matches ErrRetryExhausted.
	// TODO(stage 3): between attempts, call p's sleep(delay) where delay
	// grows exponentially with the attempt number (baseDelay * 2^attempt,
	// capped at maxDelay) *with jitter* — don't use the exact exponential
	// value, pick randomly within [0, cappedValue] ("full jitter") so many
	// simultaneous retriers don't all retry at the exact same moment
	// (thundering herd).
	// TODO(stage 4): if a budget was provided, call budget.TryConsume()
	// before each retry (not before the first attempt). If it returns
	// false, stop retrying immediately (return ErrRetryExhausted) even if
	// maxAttempts hasn't been reached yet.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a2000000-0000-0000-0000-000000000004',
    1,
    '기본 재시도 동작',
    '실패 시 재시도, 성공하면 즉시 반환',
    $stage$// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.
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

func stage() {
	attempts := 0
	flaky := func() (any, error) {
		attempts++
		if attempts < 3 {
			return nil, errors.New("boom")
		}
		return "success", nil
	}

	policy := NewRetryPolicy(5, time.Millisecond, time.Second, nil, func(time.Duration) {})
	result, err := policy.Execute(flaky)
	if err != nil {
		panic(err)
	}
	expect(result == "success", "expected success, got %v", result)
	expect(attempts == 3, "expected exactly 3 attempts (2 failures + 1 success), got %d", attempts)
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000004',
    2,
    '재시도 소진',
    'maxAttempts를 넘기면 ErrRetryExhausted, 그 이상 시도하지 않음',
    $stage$// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 ErrRetryExhausted, 그 이상 시도하지 않음.
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

func stage() {
	attempts := 0
	alwaysFail := func() (any, error) {
		attempts++
		return nil, errors.New("boom")
	}

	policy := NewRetryPolicy(4, time.Millisecond, time.Second, nil, func(time.Duration) {})
	_, err := policy.Execute(alwaysFail)
	expect(errors.Is(err, ErrRetryExhausted), "expected ErrRetryExhausted once maxAttempts is exceeded, got %v", err)
	expect(attempts == 4, "expected exactly 4 attempts (maxAttempts), got %d", attempts)
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000004',
    3,
    'exponential backoff + jitter',
    '지수적으로 커지는 대기 시간과 thundering herd를 막는 지터',
    $stage$// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
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

func stage() {
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

	expect(len(recordedDelays) == 5, "expected 5 delays between 6 attempts, got %d", len(recordedDelays))
	distinct := map[time.Duration]bool{}
	for i, d := range recordedDelays {
		limit := min(10*time.Second, 10*time.Millisecond*time.Duration(1<<i))
		expect(0 <= d && d <= limit, "delay %d = %v should be within [0, %v] (exponential backoff cap)", i, d, limit)
		distinct[d] = true
	}
	expect(len(distinct) > 1, "jitter should make delays vary, not all be identical")
}
$stage$
),
(
    'a2000000-0000-0000-0000-000000000004',
    4,
    'retry budget',
    '여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제',
    $stage$// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.
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

func stage() {
	budget := NewRetryBudget(2)

	attemptsP1 := 0
	alwaysFailP1 := func() (any, error) {
		attemptsP1++
		return nil, errors.New("boom")
	}
	policy1 := NewRetryPolicy(10, time.Millisecond, time.Second, budget, func(time.Duration) {})
	if _, err := policy1.Execute(alwaysFailP1); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}
	expect(attemptsP1 < 10, "a shared retry budget should cut retries short before maxAttempts is reached")

	attemptsP2 := 0
	alwaysFailP2 := func() (any, error) {
		attemptsP2++
		return nil, errors.New("boom")
	}
	policy2 := NewRetryPolicy(10, time.Millisecond, time.Second, budget, func(time.Duration) {})
	if _, err := policy2.Execute(alwaysFailP2); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}
	expect(attemptsP2 <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all")
}
$stage$
);

-- event-bus-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a3000000-0000-0000-0000-000000000002',
    'event-bus-java',
    'Build your own Event Bus (Java)',
    'java',
    'EventBus.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Event Bus (Java)
 *
 * Implement `EventBus` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** What poll() hands back: the event id (pass it to ack()) and the published payload. */
record Event(String id, Object payload) {}

/**
 * A topic-based pub/sub bus with at-least-once delivery: publish(topic,
 * payload) fans out a copy of the event to every current subscriber of
 * that topic. Each subscriber pulls its own copy via poll() — like
 * Build your own Queue, a polled event stays invisible to that same
 * subscriber's later poll() calls until it's ack()'d or the visibility
 * timeout expires, at which point it's redelivered (up to maxRetries).
 */
public class EventBus {
    public EventBus() {
        this(5.0, 3);
    }

    public EventBus(double visibilityTimeoutSeconds, int maxRetries) {
        // TODO(stage 1): store config and set up whatever storage you need.
        throw new UnsupportedOperationException("not implemented");
    }

    public String subscribe(String topic) {
        // TODO(stage 1): register a new subscriber for `topic`, return a
        // subscriber id used by poll()/ack(). Only events published *after*
        // subscribe() need to reach this subscriber.
        throw new UnsupportedOperationException("not implemented");
    }

    public String publish(String topic, Object payload) {
        // TODO(stage 1): deliver a copy of this event to every subscriber
        // currently subscribed to `topic` (fan-out) — subscribers of other
        // topics must not receive it. Return an event id.
        throw new UnsupportedOperationException("not implemented");
    }

    public Event poll(String subscriberId) {
        // TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
        // per subscriber), or null if nothing is visible. Return
        // new Event(id, payload).
        // TODO(stage 2): once returned, the event must stay invisible to
        // this subscriber's other poll() calls until ack()'d or
        // visibilityTimeoutSeconds elapses (then it's redelivered).
        // TODO(stage 3): events for one subscriber must come out in the
        // same order they were published to its topic.
        // TODO(stage 4): make this safe when called concurrently from
        // multiple threads for the same subscriber — no event may be
        // delivered twice or lost.
        throw new UnsupportedOperationException("not implemented");
    }

    public void ack(String subscriberId, String eventId) {
        // TODO(stage 2): permanently remove the event so it's never redelivered.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a3000000-0000-0000-0000-000000000002',
    1,
    'pub/sub fan-out',
    '하나의 publish가 해당 topic의 모든 구독자에게 전달됨',
    $stage$// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.
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
        EventBus bus = new EventBus();
        String subA = bus.subscribe("orders");
        String subB = bus.subscribe("orders");
        String subC = bus.subscribe("payments");

        bus.publish("orders", "order-created");

        Event msgA = bus.poll(subA);
        Event msgB = bus.poll(subB);
        Event msgC = bus.poll(subC);

        check(msgA != null && "order-created".equals(msgA.payload()), "sub_a should receive the event");
        check(msgB != null && "order-created".equals(msgB.payload()), "sub_b should receive the event (fan-out)");
        check(msgC == null, "a subscriber to a different topic should not receive the event");
    }
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000002',
    2,
    'at-least-once delivery',
    'ack 없이 visibility timeout이 지나면 재전달',
    $stage$// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.
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
        EventBus bus = new EventBus(0.3, 3);
        String sub = bus.subscribe("orders");
        bus.publish("orders", "x");

        Event msg = bus.poll(sub);
        check(msg != null, "expected an event");
        check(bus.poll(sub) == null, "in-flight event should not be immediately re-deliverable");

        Thread.sleep(400);
        Event redelivered = bus.poll(sub);
        check(redelivered != null, "event should be redelivered after visibility timeout without ack");
        check("x".equals(redelivered.payload()), "redelivered event should carry the original payload");
        bus.ack(sub, redelivered.id());
        check(bus.poll(sub) == null, "acked event should not be redelivered");
    }
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000002',
    3,
    'ordering',
    '같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달',
    $stage$// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.
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

    static void run() {
        EventBus bus = new EventBus();
        String sub = bus.subscribe("orders");
        bus.publish("orders", "a");
        bus.publish("orders", "b");
        bus.publish("orders", "c");

        Event msg1 = bus.poll(sub);
        Event msg2 = bus.poll(sub);
        Event msg3 = bus.poll(sub);

        List<Object> got = List.of(msg1.payload(), msg2.payload(), msg3.payload());
        check(got.equals(List.of("a", "b", "c")), "expected FIFO order [a, b, c], got " + got);
    }
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000002',
    4,
    '동시성',
    '한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음',
    $stage$// Stage 4 — concurrency.
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
        for (int i = 0; i < 20; i++) bus.publish("orders", i);

        List<Object> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
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

        check(received.size() == 20, "expected 20 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>();
        for (Object p : received) sorted.add((Integer) p);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) expected.add(i);
        check(sorted.equals(expected), "each event should be delivered exactly once across concurrent pollers");
    }
}
$stage$
);

-- event-bus-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a3000000-0000-0000-0000-000000000003',
    'event-bus-kotlin',
    'Build your own Event Bus (Kotlin)',
    'kotlin',
    'EventBus.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Event Bus (Kotlin)
 *
 * Implement `EventBus` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

/** What poll() hands back: the event id (pass it to ack()) and the published payload. */
data class Event(val id: String, val payload: Any?)

/**
 * A topic-based pub/sub bus with at-least-once delivery: publish(topic,
 * payload) fans out a copy of the event to every current subscriber of
 * that topic. Each subscriber pulls its own copy via poll() — like
 * Build your own Queue, a polled event stays invisible to that same
 * subscriber's later poll() calls until it's ack()'d or the visibility
 * timeout expires, at which point it's redelivered (up to maxRetries).
 */
class EventBus(
    private val visibilityTimeout: Double = 5.0,
    private val maxRetries: Int = 3,
) {
    init {
        // TODO(stage 1): set up whatever storage you need.
        TODO("not implemented")
    }

    fun subscribe(topic: String): String {
        // TODO(stage 1): register a new subscriber for `topic`, return a
        // subscriber id used by poll()/ack(). Only events published *after*
        // subscribe() need to reach this subscriber.
        TODO("not implemented")
    }

    fun publish(topic: String, payload: Any?): String {
        // TODO(stage 1): deliver a copy of this event to every subscriber
        // currently subscribed to `topic` (fan-out) — subscribers of other
        // topics must not receive it. Return an event id.
        TODO("not implemented")
    }

    fun poll(subscriberId: String): Event? {
        // TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
        // per subscriber), or null if nothing is visible. Return
        // Event(id, payload).
        // TODO(stage 2): once returned, the event must stay invisible to
        // this subscriber's other poll() calls until ack()'d or
        // visibilityTimeout seconds elapse (then it's redelivered).
        // TODO(stage 3): events for one subscriber must come out in the
        // same order they were published to its topic.
        // TODO(stage 4): make this safe when called concurrently from
        // multiple threads for the same subscriber — no event may be
        // delivered twice or lost.
        TODO("not implemented")
    }

    fun ack(subscriberId: String, eventId: String) {
        // TODO(stage 2): permanently remove the event so it's never redelivered.
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a3000000-0000-0000-0000-000000000003',
    1,
    'pub/sub fan-out',
    '하나의 publish가 해당 topic의 모든 구독자에게 전달됨',
    $stage$@file:JvmName("RunTest")

// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.

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
    val subA = bus.subscribe("orders")
    val subB = bus.subscribe("orders")
    val subC = bus.subscribe("payments")

    bus.publish("orders", "order-created")

    val msgA = bus.poll(subA)
    val msgB = bus.poll(subB)
    val msgC = bus.poll(subC)

    expect(msgA != null && msgA.payload == "order-created", "sub_a should receive the event")
    expect(msgB != null && msgB.payload == "order-created", "sub_b should receive the event (fan-out)")
    expect(msgC == null, "a subscriber to a different topic should not receive the event")
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000003',
    2,
    'at-least-once delivery',
    'ack 없이 visibility timeout이 지나면 재전달',
    $stage$@file:JvmName("RunTest")

// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.

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
    val bus = EventBus(visibilityTimeout = 0.3, maxRetries = 3)
    val sub = bus.subscribe("orders")
    bus.publish("orders", "x")

    val msg = bus.poll(sub)
    expect(msg != null, "expected an event")
    expect(bus.poll(sub) == null, "in-flight event should not be immediately re-deliverable")

    Thread.sleep(400)
    val redelivered = bus.poll(sub)
    expect(redelivered != null, "event should be redelivered after visibility timeout without ack")
    expect(redelivered!!.payload == "x", "redelivered event should carry the original payload")
    bus.ack(sub, redelivered.id)
    expect(bus.poll(sub) == null, "acked event should not be redelivered")
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000003',
    3,
    'ordering',
    '같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달',
    $stage$@file:JvmName("RunTest")

// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.

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
    bus.publish("orders", "a")
    bus.publish("orders", "b")
    bus.publish("orders", "c")

    val msg1 = bus.poll(sub)
    val msg2 = bus.poll(sub)
    val msg3 = bus.poll(sub)

    val got = listOf(msg1!!.payload, msg2!!.payload, msg3!!.payload)
    expect(got == listOf("a", "b", "c"), "expected FIFO order [a, b, c], got $got")
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000003',
    4,
    '동시성',
    '한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음',
    $stage$@file:JvmName("RunTest")

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
    for (i in 0 until 20) bus.publish("orders", i)

    val received = Collections.synchronizedList(mutableListOf<Any?>())
    val failure = AtomicReference<Throwable>()
    val start = CountDownLatch(1)
    val threads = (1..5).map {
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

    expect(received.size == 20, "expected 20 deliveries, got ${received.size}")
    expect(received.map { it as Int }.sorted() == (0 until 20).toList(), "each event should be delivered exactly once across concurrent pollers")
}
$stage$
);

-- event-bus-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a3000000-0000-0000-0000-000000000004',
    'event-bus-go',
    'Build your own Event Bus (Go)',
    'go',
    'event_bus.go',
    $stub$// SysDrill Build Mode — Build your own Event Bus (Go)
//
// Implement EventBus below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Event is what Poll hands back: the event ID (pass it to Ack) and the published payload.
type Event struct {
	ID      string
	Payload any
}

// EventBus is a topic-based pub/sub bus with at-least-once delivery:
// Publish(topic, payload) fans out a copy of the event to every current
// subscriber of that topic. Each subscriber pulls its own copy via Poll —
// like Build your own Queue, a polled event stays invisible to that same
// subscriber's later Poll calls until it's Ack'd or the visibility timeout
// expires, at which point it's redelivered (up to maxRetries).
type EventBus struct {
	// TODO(stage 1): add whatever fields you need.
}

func NewEventBus(visibilityTimeout time.Duration, maxRetries int) *EventBus {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

func (b *EventBus) Subscribe(topic string) string {
	// TODO(stage 1): register a new subscriber for topic, return a
	// subscriber ID used by Poll/Ack. Only events published *after*
	// Subscribe need to reach this subscriber.
	panic("not implemented")
}

func (b *EventBus) Publish(topic string, payload any) string {
	// TODO(stage 1): deliver a copy of this event to every subscriber
	// currently subscribed to topic (fan-out) — subscribers of other
	// topics must not receive it. Return an event ID.
	panic("not implemented")
}

func (b *EventBus) Poll(subscriberID string) (Event, bool) {
	// TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
	// per subscriber) and return it with true, or Event{}, false if
	// nothing is visible.
	// TODO(stage 2): once returned, the event must stay invisible to
	// this subscriber's other Poll calls until Ack'd or
	// visibilityTimeout elapses (then it's redelivered).
	// TODO(stage 3): events for one subscriber must come out in the
	// same order they were published to its topic.
	// TODO(stage 4): make this safe when called concurrently from
	// multiple goroutines for the same subscriber — no event may be
	// delivered twice or lost.
	panic("not implemented")
}

func (b *EventBus) Ack(subscriberID, eventID string) {
	// TODO(stage 2): permanently remove the event so it's never redelivered.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a3000000-0000-0000-0000-000000000004',
    1,
    'pub/sub fan-out',
    '하나의 publish가 해당 topic의 모든 구독자에게 전달됨',
    $stage$// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.
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
	bus := NewEventBus(5*time.Second, 3)
	subA := bus.Subscribe("orders")
	subB := bus.Subscribe("orders")
	subC := bus.Subscribe("payments")

	bus.Publish("orders", "order-created")

	msgA, okA := bus.Poll(subA)
	msgB, okB := bus.Poll(subB)
	_, okC := bus.Poll(subC)

	expect(okA && msgA.Payload == "order-created", "sub_a should receive the event")
	expect(okB && msgB.Payload == "order-created", "sub_b should receive the event (fan-out)")
	expect(!okC, "a subscriber to a different topic should not receive the event")
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000004',
    2,
    'at-least-once delivery',
    'ack 없이 visibility timeout이 지나면 재전달',
    $stage$// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.
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
	bus := NewEventBus(300*time.Millisecond, 3)
	sub := bus.Subscribe("orders")
	bus.Publish("orders", "x")

	_, ok := bus.Poll(sub)
	expect(ok, "expected an event")
	_, ok = bus.Poll(sub)
	expect(!ok, "in-flight event should not be immediately re-deliverable")

	time.Sleep(400 * time.Millisecond)
	redelivered, ok := bus.Poll(sub)
	expect(ok, "event should be redelivered after visibility timeout without ack")
	expect(redelivered.Payload == "x", "redelivered event should carry the original payload")
	bus.Ack(sub, redelivered.ID)
	_, ok = bus.Poll(sub)
	expect(!ok, "acked event should not be redelivered")
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000004',
    3,
    'ordering',
    '같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달',
    $stage$// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.
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
	bus := NewEventBus(5*time.Second, 3)
	sub := bus.Subscribe("orders")
	bus.Publish("orders", "a")
	bus.Publish("orders", "b")
	bus.Publish("orders", "c")

	msg1, _ := bus.Poll(sub)
	msg2, _ := bus.Poll(sub)
	msg3, _ := bus.Poll(sub)

	got := []any{msg1.Payload, msg2.Payload, msg3.Payload}
	expect(got[0] == "a" && got[1] == "b" && got[2] == "c", "expected FIFO order [a, b, c], got %v", got)
}
$stage$
),
(
    'a3000000-0000-0000-0000-000000000004',
    4,
    '동시성',
    '한 구독자에 대해 여러 고루틴이 동시에 Poll해도 중복/유실 없음',
    $stage$// Stage 4 — concurrency.
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
	for i := 0; i < 20; i++ {
		bus.Publish("orders", i)
	}

	var mu sync.Mutex
	var received []int
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	start := make(chan struct{})
	for g := 0; g < 5; g++ {
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

	expect(len(received) == 20, "expected 20 deliveries, got %d", len(received))
	slices.Sort(received)
	expected := make([]int, 20)
	for i := range expected {
		expected[i] = i
	}
	expect(slices.Equal(received, expected), "each event should be delivered exactly once across concurrent pollers")
}
$stage$
);
