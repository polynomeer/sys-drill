"""
SysDrill Build Mode — Build your own Transactional Outbox

Implement OrderService, OutboxRelay and InventoryConsumer below across 4
stages (see README.md). Database and Broker are provided and complete —
don't change them; the stage tests drive their failure hooks. Keep the
class and method names as-is. Submit by running ./submit.sh once you're ready.
"""


class DatabaseError(Exception):
    pass


class BrokerError(Exception):
    pass


class Database:
    """Provided — a tiny in-memory database with all-or-nothing transactions.

    Tables: `orders` (order_id -> amount) and the outbox (a list of event
    rows, oldest first). Changes made through a Transaction only become
    visible on commit(); a failed commit applies nothing.
    """

    def __init__(self):
        self.orders = {}
        self.outbox = []  # [{"id": "evt-1", "type": ..., "payload": {...}, "published": False}]
        self.commits = 0
        self._next_event = 1
        self._fail_next_commit = False
        self._fail_next_mark = False

    def begin(self) -> "Transaction":
        return Transaction(self)

    def pending_events(self, limit: int) -> list:
        """Committed outbox events not yet marked published, oldest first (copies)."""
        return [dict(e) for e in self.outbox if not e["published"]][:limit]

    def mark_published(self, event_id: str) -> None:
        if self._fail_next_mark:
            self._fail_next_mark = False
            raise DatabaseError("connection lost while marking the event published")
        for e in self.outbox:
            if e["id"] == event_id:
                e["published"] = True
                return
        raise DatabaseError(f"no outbox event {event_id}")

    # test hooks
    def fail_next_commit(self) -> None:
        self._fail_next_commit = True

    def fail_next_mark_published(self) -> None:
        self._fail_next_mark = True


class Transaction:
    """Provided — stages writes and applies them all at once on commit()."""

    def __init__(self, db: Database):
        self._db = db
        self._orders = {}
        self._events = []
        self._done = False

    def insert_order(self, order_id: str, amount: int) -> None:
        self._orders[order_id] = amount

    def insert_event(self, event_type: str, payload: dict) -> str:
        """Stage an outbox event; returns the id it will have once committed."""
        event_id = f"evt-{self._db._next_event + len(self._events)}"
        self._events.append({"id": event_id, "type": event_type, "payload": dict(payload), "published": False})
        return event_id

    def commit(self) -> None:
        if self._done:
            raise DatabaseError("transaction already finished")
        self._done = True
        if self._db._fail_next_commit:
            self._db._fail_next_commit = False
            raise DatabaseError("commit failed")
        self._db.orders.update(self._orders)
        self._db.outbox.extend(self._events)
        self._db._next_event += len(self._events)
        self._db.commits += 1

    def rollback(self) -> None:
        self._done = True


class Broker:
    """Provided — a message broker (think Kafka) that consumers read from."""

    def __init__(self):
        self.published = []  # every event delivered, in order — duplicates included
        self._fail_next = False

    def publish(self, event: dict) -> None:
        if self._fail_next:
            self._fail_next = False
            raise BrokerError("broker unavailable")
        self.published.append(dict(event))

    # test hook
    def fail_next_publish(self) -> None:
        self._fail_next = True


class OrderService:
    def __init__(self, db: Database, broker: Broker):
        self.db = db
        self.broker = broker

    def place_order(self, order_id: str, amount: int) -> None:
        # TODO(stage 1): save the order AND an "OrderPlaced" outbox event
        # (payload {"order_id": ..., "amount": ...}) in ONE transaction, so
        # either both exist or neither does. Don't publish to the broker
        # here — that's the dual write the outbox exists to avoid (the order
        # commits, the publish fails, and nobody ever hears about the order).
        raise NotImplementedError


class OutboxRelay:
    def __init__(self, db: Database, broker: Broker):
        self.db = db
        self.broker = broker

    def run_once(self, batch_size: int = 100) -> int:
        # TODO(stage 2): publish pending outbox events to the broker oldest
        # first, marking each one published; return how many you published.
        # TODO(stage 3): if the broker fails, stop right there and return —
        # leave that event (and everything after it) pending for the next
        # run. Never skip ahead (order matters) and never mark an event
        # published before the broker has it (that loses it).
        raise NotImplementedError


class InventoryConsumer:
    """Consumes OrderPlaced events and reserves stock for them."""

    def __init__(self, stock: int):
        self.stock = stock

    def handle(self, event: dict) -> None:
        # TODO(stage 4): reserve stock for the order (event["payload"]
        # ["amount"] units). The relay is at-least-once — after a crash it
        # republishes events the broker already has — so the same event can
        # arrive twice: apply each event id only once.
        raise NotImplementedError
