package main

import (
	"slices"
	"strconv"
	"sync"
	"time"
)

type Event struct {
	ID      string
	Payload any
}

type delivery struct {
	event     Event
	seq       int // publish order, used to keep redeliveries in order
	attempts  int
	visibleAt time.Time
}

type subscriber struct {
	ready    []*delivery
	inFlight map[string]*delivery
}

type EventBus struct {
	mu                sync.Mutex
	visibilityTimeout time.Duration
	maxRetries        int
	nextID            int
	topics            map[string][]*subscriber
	subscribers       map[string]*subscriber
}

func NewEventBus(visibilityTimeout time.Duration, maxRetries int) *EventBus {
	return &EventBus{
		visibilityTimeout: visibilityTimeout,
		maxRetries:        maxRetries,
		topics:            map[string][]*subscriber{},
		subscribers:       map[string]*subscriber{},
	}
}

func (b *EventBus) newID(prefix string) string {
	b.nextID++
	return prefix + "-" + strconv.Itoa(b.nextID)
}

func (b *EventBus) Subscribe(topic string) string {
	b.mu.Lock()
	defer b.mu.Unlock()
	id := b.newID("sub")
	sub := &subscriber{inFlight: map[string]*delivery{}}
	b.subscribers[id] = sub
	b.topics[topic] = append(b.topics[topic], sub)
	return id
}

func (b *EventBus) Publish(topic string, payload any) string {
	b.mu.Lock()
	defer b.mu.Unlock()
	event := Event{ID: b.newID("evt"), Payload: payload}
	for _, sub := range b.topics[topic] {
		sub.ready = append(sub.ready, &delivery{event: event, seq: b.nextID})
	}
	return event.ID
}

func (b *EventBus) Poll(subscriberID string) (Event, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	sub, ok := b.subscribers[subscriberID]
	if !ok {
		return Event{}, false
	}
	b.requeueExpired(sub)
	if len(sub.ready) == 0 {
		return Event{}, false
	}
	next := sub.ready[0]
	sub.ready = sub.ready[1:]
	next.attempts++
	next.visibleAt = time.Now().Add(b.visibilityTimeout)
	sub.inFlight[next.event.ID] = next
	return next.event, true
}

func (b *EventBus) Ack(subscriberID, eventID string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if sub, ok := b.subscribers[subscriberID]; ok {
		delete(sub.inFlight, eventID)
	}
}

// requeueExpired moves timed-out deliveries back to the ready queue until
// they've used up maxRetries attempts. Map iteration order is random, so
// they're sorted back into publish order first.
func (b *EventBus) requeueExpired(sub *subscriber) {
	now := time.Now()
	var expired []*delivery
	for id, d := range sub.inFlight {
		if !now.Before(d.visibleAt) {
			delete(sub.inFlight, id)
			if d.attempts < b.maxRetries {
				expired = append(expired, d)
			}
		}
	}
	slices.SortFunc(expired, func(x, y *delivery) int { return x.seq - y.seq })
	sub.ready = append(sub.ready, expired...)
}
