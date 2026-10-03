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
    visibilityTimeoutSeconds: Double = 5.0,
    private val maxRetries: Int = 3,
) {
    private class Entry(val id: String, val payload: Any) {
        var attempts = 0
        var invisibleUntilNanos = 0L
    }

    private val visibilityTimeoutNanos = (visibilityTimeoutSeconds * 1_000_000_000).toLong()
    private val lock = Any()

    // Insertion order = FIFO order; keyed by id so ack() is O(1).
    private val messages = LinkedHashMap<String, Entry>()
    private val deadLetters = mutableListOf<Message>()
    private var nextId = 1L

    fun enqueue(payload: Any): String = synchronized(lock) {
        val id = "msg-${nextId++}"
        messages[id] = Entry(id, payload)
        id
    }

    fun dequeue(): Message? = synchronized(lock) {
        val now = System.nanoTime()
        val it = messages.values.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.attempts > 0 && now < e.invisibleUntilNanos) continue // in flight
            if (e.attempts >= maxRetries) {
                it.remove()
                deadLetters += Message(e.id, e.payload)
                continue
            }
            e.attempts++
            e.invisibleUntilNanos = now + visibilityTimeoutNanos
            return Message(e.id, e.payload)
        }
        null
    }

    fun ack(messageId: String) {
        synchronized(lock) { messages.remove(messageId) }
    }

    val deadLetterQueue: List<Message>
        get() = synchronized(lock) { deadLetters.toList() }
}
