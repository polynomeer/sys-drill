/*
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
