"""
Model answer for Build your own Transactional Outbox (Python) — used by BuildLanguageVariantsIntegrationTest.

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
        tx = self.db.begin()
        try:
            tx.insert_order(order_id, amount)
            tx.insert_event("OrderPlaced", {"order_id": order_id, "amount": amount})
            tx.commit()
        except Exception:
            tx.rollback()
            raise


class OutboxRelay:
    def __init__(self, db: Database, broker: Broker):
        self.db = db
        self.broker = broker

    def run_once(self, batch_size: int = 100) -> int:
        published = 0
        for event in self.db.pending_events(batch_size):
            try:
                self.broker.publish(event)
            except BrokerError:
                break  # leave it (and everything after it) for the next run
            self.db.mark_published(event["id"])  # only after the broker has it
            published += 1
        return published


class InventoryConsumer:
    """Consumes OrderPlaced events and reserves stock for them."""

    def __init__(self, stock: int):
        self.stock = stock
        self._seen = set()

    def handle(self, event: dict) -> None:
        if event["id"] in self._seen:
            return  # a redelivery — already applied
        self._seen.add(event["id"])
        self.stock -= event["payload"]["amount"]
