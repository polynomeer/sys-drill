/*
 * SysDrill Build Mode — Build your own Transactional Outbox (Java)
 *
 * Implement OrderService, OutboxRelay and InventoryConsumer below across 4
 * stages (see README.md). Database, Transaction and Broker are provided and
 * complete — don't change them; the stage tests drive their failure hooks.
 * Keep the class and method names as-is. Submit by running ./submit.sh once
 * you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Names shared by the whole outbox (the file's public type). */
public final class Outbox {
    /** The event type placeOrder() writes to the outbox. */
    public static final String ORDER_PLACED = "OrderPlaced";

    private Outbox() {}
}

class DatabaseException extends RuntimeException {
    DatabaseException(String message) {
        super(message);
    }
}

class BrokerException extends RuntimeException {
    BrokerException(String message) {
        super(message);
    }
}

/**
 * An outbox event — what the relay hands to the broker and the broker hands
 * to consumers. Immutable; the payload is copied on the way in. Whether it's
 * been published is a column of the outbox row, kept inside Database (see
 * pendingEvents()), not part of the event itself.
 */
record Event(String id, String type, Map<String, Object> payload) {
    Event {
        payload = Map.copyOf(payload);
    }
}

/**
 * Provided — a tiny in-memory database with all-or-nothing transactions.
 *
 * Tables: orders (orderId -> amount) and the outbox (event rows with a
 * published flag, oldest first). Changes made through a Transaction only
 * become visible on commit(); a failed commit applies nothing.
 */
class Database {
    private static final class OutboxRow {
        final Event event;
        boolean published;

        OutboxRow(Event event) {
            this.event = event;
        }
    }

    private final Map<String, Integer> orders = new LinkedHashMap<>();
    private final List<OutboxRow> outbox = new ArrayList<>();
    private int commits = 0;
    private int nextEvent = 1;
    private boolean failNextCommit = false;
    private boolean failNextMark = false;

    public Transaction begin() {
        return new Transaction(this);
    }

    /** The orders table (a copy). */
    public Map<String, Integer> orders() {
        return new LinkedHashMap<>(orders);
    }

    /** Every committed outbox event, published or not, oldest first. */
    public List<Event> outbox() {
        List<Event> events = new ArrayList<>();
        for (OutboxRow row : outbox) events.add(row.event);
        return events;
    }

    /** How many transactions have committed. */
    public int commits() {
        return commits;
    }

    /** Committed outbox events not yet marked published, oldest first. */
    public List<Event> pendingEvents(int limit) {
        List<Event> pending = new ArrayList<>();
        for (OutboxRow row : outbox) {
            if (pending.size() >= limit) break;
            if (!row.published) pending.add(row.event);
        }
        return pending;
    }

    public void markPublished(String eventId) {
        if (failNextMark) {
            failNextMark = false;
            throw new DatabaseException("connection lost while marking the event published");
        }
        for (OutboxRow row : outbox) {
            if (row.event.id().equals(eventId)) {
                row.published = true;
                return;
            }
        }
        throw new DatabaseException("no outbox event " + eventId);
    }

    // test hooks
    public void failNextCommit() {
        failNextCommit = true;
    }

    public void failNextMarkPublished() {
        failNextMark = true;
    }

    // used by Transaction
    int nextEventNumber() {
        return nextEvent;
    }

    void apply(Map<String, Integer> newOrders, List<Event> newEvents) {
        if (failNextCommit) {
            failNextCommit = false;
            throw new DatabaseException("commit failed");
        }
        orders.putAll(newOrders);
        for (Event event : newEvents) outbox.add(new OutboxRow(event));
        nextEvent += newEvents.size();
        commits++;
    }
}

/** Provided — stages writes and applies them all at once on commit(). */
class Transaction {
    private final Database db;
    private final Map<String, Integer> orders = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private boolean done = false;

    Transaction(Database db) {
        this.db = db;
    }

    public void insertOrder(String orderId, int amount) {
        orders.put(orderId, amount);
    }

    /** Stage an outbox event; returns the id it will have once committed. */
    public String insertEvent(String eventType, Map<String, Object> payload) {
        String eventId = "evt-" + (db.nextEventNumber() + events.size());
        events.add(new Event(eventId, eventType, payload));
        return eventId;
    }

    public void commit() {
        if (done) throw new DatabaseException("transaction already finished");
        done = true;
        db.apply(orders, events);
    }

    public void rollback() {
        done = true;
    }
}

/** Provided — a message broker (think Kafka) that consumers read from. */
class Broker {
    private final List<Event> published = new ArrayList<>();
    private boolean failNext = false;

    /** Every event delivered, in order — duplicates included (a copy). */
    public List<Event> published() {
        return new ArrayList<>(published);
    }

    public void publish(Event event) {
        if (failNext) {
            failNext = false;
            throw new BrokerException("broker unavailable");
        }
        published.add(event);
    }

    // test hook
    public void failNextPublish() {
        failNext = true;
    }
}

class OrderService {
    private final Database db;
    private final Broker broker;

    OrderService(Database db, Broker broker) {
        this.db = db;
        this.broker = broker;
    }

    public void placeOrder(String orderId, int amount) {
        // TODO(stage 1): save the order AND an "OrderPlaced" outbox event
        // (payload Map.of("order_id", ..., "amount", ...)) in ONE transaction,
        // so either both exist or neither does. Don't publish to the broker
        // here — that's the dual write the outbox exists to avoid (the order
        // commits, the publish fails, and nobody ever hears about the order).
        throw new UnsupportedOperationException("not implemented");
    }
}

class OutboxRelay {
    private final Database db;
    private final Broker broker;

    OutboxRelay(Database db, Broker broker) {
        this.db = db;
        this.broker = broker;
    }

    public int runOnce() {
        return runOnce(100);
    }

    public int runOnce(int batchSize) {
        // TODO(stage 2): publish pending outbox events to the broker oldest
        // first, marking each one published; return how many you published.
        // TODO(stage 3): if the broker fails (BrokerException), stop right
        // there and return — leave that event (and everything after it)
        // pending for the next run. Never skip ahead (order matters) and
        // never mark an event published before the broker has it (that loses it).
        throw new UnsupportedOperationException("not implemented");
    }
}

/** Consumes OrderPlaced events and reserves stock for them. */
class InventoryConsumer {
    private int stock;

    InventoryConsumer(int stock) {
        this.stock = stock;
    }

    public int stock() {
        return stock;
    }

    public void handle(Event event) {
        // TODO(stage 4): reserve stock for the order (event.payload()
        // .get("amount") units). The relay is at-least-once — after a crash it
        // republishes events the broker already has — so the same event can
        // arrive twice: apply each event id only once.
        throw new UnsupportedOperationException("not implemented");
    }
}
