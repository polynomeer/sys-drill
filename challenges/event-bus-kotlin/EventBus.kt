/*
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
