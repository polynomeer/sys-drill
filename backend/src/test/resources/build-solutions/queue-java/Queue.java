/*
 * SysDrill Build Mode — Build your own Queue (Java)
 *
 * Implement `Queue` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final class Entry {
        final String id;
        final Object payload;
        int attempts;
        long invisibleUntilNanos;

        Entry(String id, Object payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private final long visibilityTimeoutNanos;
    private final int maxRetries;
    // Insertion order = FIFO order; keyed by id so ack() is O(1).
    private final Map<String, Entry> messages = new LinkedHashMap<>();
    private final List<Message> deadLetters = new ArrayList<>();
    private long nextId = 1;

    public Queue() {
        this(5.0, 3);
    }

    public Queue(double visibilityTimeoutSeconds, int maxRetries) {
        this.visibilityTimeoutNanos = (long) (visibilityTimeoutSeconds * 1_000_000_000L);
        this.maxRetries = maxRetries;
    }

    public synchronized String enqueue(Object payload) {
        String id = "msg-" + nextId++;
        messages.put(id, new Entry(id, payload));
        return id;
    }

    public synchronized Message dequeue() {
        long now = System.nanoTime();
        Iterator<Entry> it = messages.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.attempts > 0 && now < e.invisibleUntilNanos) continue; // in flight
            if (e.attempts >= maxRetries) {
                it.remove();
                deadLetters.add(new Message(e.id, e.payload));
                continue;
            }
            e.attempts++;
            e.invisibleUntilNanos = now + visibilityTimeoutNanos;
            return new Message(e.id, e.payload);
        }
        return null;
    }

    public synchronized void ack(String messageId) {
        messages.remove(messageId);
    }

    public synchronized List<Message> deadLetterQueue() {
        return List.copyOf(deadLetters);
    }
}
