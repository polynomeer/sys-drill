import java.util.UUID

data class Event(val id: String, val payload: Any?)

class EventBus(
    visibilityTimeout: Double = 5.0,
    private val maxRetries: Int = 3,
) {
    private class Delivery(val event: Event) {
        var attempts = 0
        var visibleAtNanos = 0L
    }

    private class Subscriber {
        val ready = ArrayDeque<Delivery>()
        val inFlight = LinkedHashMap<String, Delivery>()
    }

    private val visibilityTimeoutNanos = (visibilityTimeout * 1_000_000_000).toLong()
    private val subscribersByTopic = mutableMapOf<String, MutableList<String>>()
    private val subscribers = mutableMapOf<String, Subscriber>()
    private val lock = Any()

    fun subscribe(topic: String): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        subscribers[id] = Subscriber()
        subscribersByTopic.getOrPut(topic) { mutableListOf() }.add(id)
        id
    }

    fun publish(topic: String, payload: Any?): String = synchronized(lock) {
        val event = Event(UUID.randomUUID().toString(), payload)
        for (subId in subscribersByTopic[topic].orEmpty()) {
            subscribers.getValue(subId).ready.addLast(Delivery(event))
        }
        event.id
    }

    fun poll(subscriberId: String): Event? = synchronized(lock) {
        val sub = subscribers[subscriberId] ?: return null
        requeueExpired(sub)
        val next = sub.ready.removeFirstOrNull() ?: return null
        next.attempts++
        next.visibleAtNanos = System.nanoTime() + visibilityTimeoutNanos
        sub.inFlight[next.event.id] = next
        next.event
    }

    fun ack(subscriberId: String, eventId: String) {
        synchronized(lock) { subscribers[subscriberId]?.inFlight?.remove(eventId) }
    }

    // Timed-out deliveries go back to the ready queue until they've used up maxRetries attempts.
    private fun requeueExpired(sub: Subscriber) {
        val now = System.nanoTime()
        val it = sub.inFlight.values.iterator()
        while (it.hasNext()) {
            val d = it.next()
            if (d.visibleAtNanos - now <= 0) {
                it.remove()
                if (d.attempts < maxRetries) sub.ready.addLast(d)
            }
        }
    }
}
