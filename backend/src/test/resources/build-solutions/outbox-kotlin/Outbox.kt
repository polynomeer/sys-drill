/*
 * Model answer for Build your own Transactional Outbox (Kotlin) — used by BuildLanguageVariantsIntegrationTest.
 *
 * SysDrill Build Mode — Build your own Transactional Outbox (Kotlin)
 *
 * Implement OrderService, OutboxRelay and InventoryConsumer below across 4
 * stages (see README.md). Database, Transaction and Broker are provided and
 * complete — don't change them; the stage tests drive their failure hooks.
 * Keep the class and member names as-is. Submit by running ./submit.sh once
 * you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

class DatabaseException(message: String) : RuntimeException(message)

class BrokerException(message: String) : RuntimeException(message)

/**
 * An outbox event — what the relay hands to the broker and the broker hands
 * to consumers. Immutable; the payload is copied on the way in. Whether it's
 * been published is a column of the outbox row, kept inside Database (see
 * pendingEvents()), not part of the event itself.
 */
data class Event(val id: String, val type: String, val payload: Map<String, Any>)

/**
 * Provided — a tiny in-memory database with all-or-nothing transactions.
 *
 * Tables: orders (orderId -> amount) and the outbox (event rows with a
 * published flag, oldest first). Changes made through a Transaction only
 * become visible on commit(); a failed commit applies nothing.
 */
class Database {
    private class OutboxRow(val event: Event, var published: Boolean = false)

    private val orderRows = LinkedHashMap<String, Int>()
    private val outboxRows = mutableListOf<OutboxRow>()
    private var nextEvent = 1
    private var failNextCommit = false
    private var failNextMark = false

    /** The orders table (a copy). */
    val orders: Map<String, Int>
        get() = LinkedHashMap(orderRows)

    /** Every committed outbox event, published or not, oldest first. */
    val outbox: List<Event>
        get() = outboxRows.map { it.event }

    /** How many transactions have committed. */
    var commits = 0
        private set

    fun begin(): Transaction = Transaction(this)

    /** Committed outbox events not yet marked published, oldest first. */
    fun pendingEvents(limit: Int): List<Event> =
        outboxRows.filter { !it.published }.take(limit).map { it.event }

    fun markPublished(eventId: String) {
        if (failNextMark) {
            failNextMark = false
            throw DatabaseException("connection lost while marking the event published")
        }
        val row = outboxRows.find { it.event.id == eventId }
            ?: throw DatabaseException("no outbox event $eventId")
        row.published = true
    }

    // test hooks
    fun failNextCommit() {
        failNextCommit = true
    }

    fun failNextMarkPublished() {
        failNextMark = true
    }

    // used by Transaction
    internal fun nextEventNumber(): Int = nextEvent

    internal fun apply(newOrders: Map<String, Int>, newEvents: List<Event>) {
        if (failNextCommit) {
            failNextCommit = false
            throw DatabaseException("commit failed")
        }
        orderRows.putAll(newOrders)
        newEvents.forEach { outboxRows.add(OutboxRow(it)) }
        nextEvent += newEvents.size
        commits++
    }
}

/** Provided — stages writes and applies them all at once on commit(). */
class Transaction internal constructor(private val db: Database) {
    private val orders = HashMap<String, Int>()
    private val events = mutableListOf<Event>()
    private var done = false

    fun insertOrder(orderId: String, amount: Int) {
        orders[orderId] = amount
    }

    /** Stage an outbox event; returns the id it will have once committed. */
    fun insertEvent(eventType: String, payload: Map<String, Any>): String {
        val eventId = "evt-${db.nextEventNumber() + events.size}"
        events.add(Event(eventId, eventType, payload.toMap()))
        return eventId
    }

    fun commit() {
        if (done) throw DatabaseException("transaction already finished")
        done = true
        db.apply(orders, events)
    }

    fun rollback() {
        done = true
    }
}

/** Provided — a message broker (think Kafka) that consumers read from. */
class Broker {
    private val delivered = mutableListOf<Event>()
    private var failNext = false

    /** Every event delivered, in order — duplicates included (a copy). */
    val published: List<Event>
        get() = delivered.toList()

    fun publish(event: Event) {
        if (failNext) {
            failNext = false
            throw BrokerException("broker unavailable")
        }
        delivered.add(event)
    }

    // test hook
    fun failNextPublish() {
        failNext = true
    }
}

class OrderService(private val db: Database, private val broker: Broker) {
    fun placeOrder(orderId: String, amount: Int) {
        val tx = db.begin()
        try {
            tx.insertOrder(orderId, amount)
            tx.insertEvent("OrderPlaced", mapOf("order_id" to orderId, "amount" to amount))
            tx.commit()
        } catch (e: Exception) {
            tx.rollback()
            throw e
        }
    }
}

class OutboxRelay(private val db: Database, private val broker: Broker) {
    fun runOnce(batchSize: Int = 100): Int {
        var published = 0
        for (event in db.pendingEvents(batchSize)) {
            try {
                broker.publish(event)
            } catch (e: BrokerException) {
                break // leave it (and everything after it) for the next run
            }
            db.markPublished(event.id) // only after the broker has it
            published++
        }
        return published
    }
}

/** Consumes OrderPlaced events and reserves stock for them. */
class InventoryConsumer(stock: Int) {
    var stock = stock
        private set
    private val seen = HashSet<String>()

    fun handle(event: Event) {
        if (!seen.add(event.id)) return // a redelivery — already applied
        stock -= event.payload["amount"] as Int
    }
}
