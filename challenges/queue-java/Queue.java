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
