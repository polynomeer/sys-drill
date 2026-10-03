// SysDrill Build Mode — Build your own Queue (Go)
//
// Implement Queue below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Message is what Dequeue hands out: the ID to Ack it with, plus the payload.
type Message struct {
	ID      string
	Payload any
}

// Queue is an at-least-once message queue with visibility timeouts (like
// SQS), not a plain FIFO: a dequeued message stays invisible to other
// Dequeue calls until it's Ack'd or the visibility timeout expires, at which
// point it's redelivered — up to maxRetries times before it moves to the
// dead-letter queue.
type Queue struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewQueue(visibilityTimeout time.Duration, maxRetries int) *Queue {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

// Enqueue adds a message and returns its message ID.
func (q *Queue) Enqueue(payload any) string {
	// TODO(stage 1): add a message, return its message ID.
	panic("not implemented")
}

// Dequeue returns the oldest visible message, or ok == false if none is visible.
func (q *Queue) Dequeue() (msg Message, ok bool) {
	// TODO(stage 1): pop the oldest *visible* message (FIFO), or return
	// (Message{}, false) if nothing is visible.
	// TODO(stage 2): once returned, the message must stay invisible to
	// other Dequeue calls until Ack'd or visibilityTimeout elapses.
	// TODO(stage 3): if a message's attempts reach maxRetries without
	// being Ack'd, move it to the dead-letter queue instead of redelivering.
	// TODO(stage 4): make this safe when called concurrently from
	// multiple goroutines — no two callers may receive the same message.
	panic("not implemented")
}

func (q *Queue) Ack(messageID string) {
	// TODO(stage 2): permanently remove the message so it's never redelivered.
	panic("not implemented")
}

// DeadLetterQueue returns the messages that exceeded maxRetries without being Ack'd.
func (q *Queue) DeadLetterQueue() []Message {
	// TODO(stage 3): messages that exceeded maxRetries without being Ack'd.
	panic("not implemented")
}
