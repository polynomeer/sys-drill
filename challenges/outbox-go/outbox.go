// SysDrill Build Mode — Build your own Transactional Outbox (Go)
//
// Implement OrderService, OutboxRelay and InventoryConsumer below across 4
// stages (see README.md). Database, Transaction and Broker are provided and
// complete — don't change them; the stage tests drive their failure hooks.
// Keep the type, function and method names as-is. Submit by running
// ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"fmt"
	"maps"
)

// ErrDatabase is wrapped by every error the Database or a Transaction returns
// (check with errors.Is).
var ErrDatabase = errors.New("database error")

// ErrBroker is wrapped by every error the Broker returns (check with errors.Is).
var ErrBroker = errors.New("broker error")

// Event is an outbox event — what the relay hands to the broker and the
// broker hands to consumers. Whether it's been published is a column of the
// outbox row, kept inside Database (see PendingEvents), not part of the event.
type Event struct {
	ID      string
	Type    string
	Payload map[string]any
}

func (e Event) clone() Event {
	e.Payload = maps.Clone(e.Payload)
	return e
}

type outboxRow struct {
	event     Event
	published bool
}

// Database is provided — a tiny in-memory database with all-or-nothing
// transactions.
//
// Tables: orders (orderID -> amount) and the outbox (event rows with a
// published flag, oldest first). Changes made through a Transaction only
// become visible on Commit; a failed commit applies nothing.
type Database struct {
	orders         map[string]int
	outbox         []*outboxRow
	commits        int
	nextEvent      int
	failNextCommit bool
	failNextMark   bool
}

func NewDatabase() *Database {
	return &Database{orders: map[string]int{}, nextEvent: 1}
}

func (db *Database) Begin() *Transaction {
	return &Transaction{db: db, orders: map[string]int{}}
}

// Orders returns the orders table (a copy).
func (db *Database) Orders() map[string]int {
	return maps.Clone(db.orders)
}

// Outbox returns every committed outbox event, published or not, oldest first (copies).
func (db *Database) Outbox() []Event {
	events := []Event{}
	for _, row := range db.outbox {
		events = append(events, row.event.clone())
	}
	return events
}

// Commits returns how many transactions have committed.
func (db *Database) Commits() int {
	return db.commits
}

// PendingEvents returns committed outbox events not yet marked published,
// oldest first (copies).
func (db *Database) PendingEvents(limit int) []Event {
	pending := []Event{}
	for _, row := range db.outbox {
		if len(pending) >= limit {
			break
		}
		if !row.published {
			pending = append(pending, row.event.clone())
		}
	}
	return pending
}

func (db *Database) MarkPublished(eventID string) error {
	if db.failNextMark {
		db.failNextMark = false
		return fmt.Errorf("%w: connection lost while marking the event published", ErrDatabase)
	}
	for _, row := range db.outbox {
		if row.event.ID == eventID {
			row.published = true
			return nil
		}
	}
	return fmt.Errorf("%w: no outbox event %s", ErrDatabase, eventID)
}

// FailNextCommit is a test hook: the next Commit fails and applies nothing.
func (db *Database) FailNextCommit() {
	db.failNextCommit = true
}

// FailNextMarkPublished is a test hook: the next MarkPublished fails.
func (db *Database) FailNextMarkPublished() {
	db.failNextMark = true
}

// Transaction is provided — it stages writes and applies them all at once on Commit.
type Transaction struct {
	db     *Database
	orders map[string]int
	events []Event
	done   bool
}

func (tx *Transaction) InsertOrder(orderID string, amount int) {
	tx.orders[orderID] = amount
}

// InsertEvent stages an outbox event and returns the ID it will have once committed.
func (tx *Transaction) InsertEvent(eventType string, payload map[string]any) string {
	eventID := fmt.Sprintf("evt-%d", tx.db.nextEvent+len(tx.events))
	tx.events = append(tx.events, Event{ID: eventID, Type: eventType, Payload: maps.Clone(payload)})
	return eventID
}

func (tx *Transaction) Commit() error {
	if tx.done {
		return fmt.Errorf("%w: transaction already finished", ErrDatabase)
	}
	tx.done = true
	db := tx.db
	if db.failNextCommit {
		db.failNextCommit = false
		return fmt.Errorf("%w: commit failed", ErrDatabase)
	}
	maps.Copy(db.orders, tx.orders)
	for _, e := range tx.events {
		db.outbox = append(db.outbox, &outboxRow{event: e})
	}
	db.nextEvent += len(tx.events)
	db.commits++
	return nil
}

func (tx *Transaction) Rollback() {
	tx.done = true
}

// Broker is provided — a message broker (think Kafka) that consumers read from.
type Broker struct {
	published []Event
	failNext  bool
}

func NewBroker() *Broker {
	return &Broker{}
}

// Published returns every event delivered, in order — duplicates included (copies).
func (b *Broker) Published() []Event {
	events := []Event{}
	for _, e := range b.published {
		events = append(events, e.clone())
	}
	return events
}

func (b *Broker) Publish(event Event) error {
	if b.failNext {
		b.failNext = false
		return fmt.Errorf("%w: broker unavailable", ErrBroker)
	}
	b.published = append(b.published, event.clone())
	return nil
}

// FailNextPublish is a test hook: the next Publish fails.
func (b *Broker) FailNextPublish() {
	b.failNext = true
}

type OrderService struct {
	db     *Database
	broker *Broker
}

func NewOrderService(db *Database, broker *Broker) *OrderService {
	return &OrderService{db: db, broker: broker}
}

func (s *OrderService) PlaceOrder(orderID string, amount int) error {
	// TODO(stage 1): save the order AND an "OrderPlaced" outbox event
	// (payload map[string]any{"order_id": ..., "amount": ...}) in ONE
	// transaction, so either both exist or neither does, and return the
	// commit's error. Don't publish to the broker here — that's the dual
	// write the outbox exists to avoid (the order commits, the publish fails,
	// and nobody ever hears about the order).
	panic("not implemented")
}

type OutboxRelay struct {
	db     *Database
	broker *Broker
}

func NewOutboxRelay(db *Database, broker *Broker) *OutboxRelay {
	return &OutboxRelay{db: db, broker: broker}
}

// RunOnce publishes up to batchSize pending events and returns how many it published.
func (r *OutboxRelay) RunOnce(batchSize int) (int, error) {
	// TODO(stage 2): publish pending outbox events to the broker oldest
	// first, marking each one published; return how many you published.
	// TODO(stage 3): if the broker fails (Publish returns an error), stop
	// right there and return the count with a nil error — leave that event
	// (and everything after it) pending for the next run. Never skip ahead
	// (order matters) and never mark an event published before the broker
	// has it (that loses it).
	panic("not implemented")
}

// InventoryConsumer consumes OrderPlaced events and reserves stock for them.
type InventoryConsumer struct {
	stock int
}

func NewInventoryConsumer(stock int) *InventoryConsumer {
	return &InventoryConsumer{stock: stock}
}

func (c *InventoryConsumer) Stock() int {
	return c.stock
}

func (c *InventoryConsumer) Handle(event Event) {
	// TODO(stage 4): reserve stock for the order (event.Payload["amount"]
	// units, an int). The relay is at-least-once — after a crash it
	// republishes events the broker already has — so the same event can
	// arrive twice: apply each event ID only once.
	panic("not implemented")
}
