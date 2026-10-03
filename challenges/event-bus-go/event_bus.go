// SysDrill Build Mode — Build your own Event Bus (Go)
//
// Implement EventBus below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Event is what Poll hands back: the event ID (pass it to Ack) and the published payload.
type Event struct {
	ID      string
	Payload any
}

// EventBus is a topic-based pub/sub bus with at-least-once delivery:
// Publish(topic, payload) fans out a copy of the event to every current
// subscriber of that topic. Each subscriber pulls its own copy via Poll —
// like Build your own Queue, a polled event stays invisible to that same
// subscriber's later Poll calls until it's Ack'd or the visibility timeout
// expires, at which point it's redelivered (up to maxRetries).
type EventBus struct {
	// TODO(stage 1): add whatever fields you need.
}

func NewEventBus(visibilityTimeout time.Duration, maxRetries int) *EventBus {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

func (b *EventBus) Subscribe(topic string) string {
	// TODO(stage 1): register a new subscriber for topic, return a
	// subscriber ID used by Poll/Ack. Only events published *after*
	// Subscribe need to reach this subscriber.
	panic("not implemented")
}

func (b *EventBus) Publish(topic string, payload any) string {
	// TODO(stage 1): deliver a copy of this event to every subscriber
	// currently subscribed to topic (fan-out) — subscribers of other
	// topics must not receive it. Return an event ID.
	panic("not implemented")
}

func (b *EventBus) Poll(subscriberID string) (Event, bool) {
	// TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
	// per subscriber) and return it with true, or Event{}, false if
	// nothing is visible.
	// TODO(stage 2): once returned, the event must stay invisible to
	// this subscriber's other Poll calls until Ack'd or
	// visibilityTimeout elapses (then it's redelivered).
	// TODO(stage 3): events for one subscriber must come out in the
	// same order they were published to its topic.
	// TODO(stage 4): make this safe when called concurrently from
	// multiple goroutines for the same subscriber — no event may be
	// delivered twice or lost.
	panic("not implemented")
}

func (b *EventBus) Ack(subscriberID, eventID string) {
	// TODO(stage 2): permanently remove the event so it's never redelivered.
	panic("not implemented")
}
