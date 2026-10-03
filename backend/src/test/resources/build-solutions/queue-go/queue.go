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

import (
	"fmt"
	"sync"
	"time"
)

// Message is what Dequeue hands out: the ID to Ack it with, plus the payload.
type Message struct {
	ID      string
	Payload any
}

type entry struct {
	msg            Message
	attempts       int
	invisibleUntil time.Time
}

// Queue is an at-least-once message queue with visibility timeouts (like
// SQS), not a plain FIFO: a dequeued message stays invisible to other
// Dequeue calls until it's Ack'd or the visibility timeout expires, at which
// point it's redelivered — up to maxRetries times before it moves to the
// dead-letter queue.
type Queue struct {
	visibilityTimeout time.Duration
	maxRetries        int

	mu          sync.Mutex
	messages    []*entry // FIFO order
	deadLetters []Message
	nextID      int
}

func NewQueue(visibilityTimeout time.Duration, maxRetries int) *Queue {
	return &Queue{visibilityTimeout: visibilityTimeout, maxRetries: maxRetries}
}

// Enqueue adds a message and returns its message ID.
func (q *Queue) Enqueue(payload any) string {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.nextID++
	id := fmt.Sprintf("msg-%d", q.nextID)
	q.messages = append(q.messages, &entry{msg: Message{ID: id, Payload: payload}})
	return id
}

// Dequeue returns the oldest visible message, or ok == false if none is visible.
func (q *Queue) Dequeue() (msg Message, ok bool) {
	q.mu.Lock()
	defer q.mu.Unlock()
	now := time.Now()
	for i := 0; i < len(q.messages); i++ {
		e := q.messages[i]
		if e.attempts > 0 && now.Before(e.invisibleUntil) {
			continue // in flight
		}
		if e.attempts >= q.maxRetries {
			q.deadLetters = append(q.deadLetters, e.msg)
			q.messages = append(q.messages[:i], q.messages[i+1:]...)
			i--
			continue
		}
		e.attempts++
		e.invisibleUntil = now.Add(q.visibilityTimeout)
		return e.msg, true
	}
	return Message{}, false
}

func (q *Queue) Ack(messageID string) {
	q.mu.Lock()
	defer q.mu.Unlock()
	for i, e := range q.messages {
		if e.msg.ID == messageID {
			q.messages = append(q.messages[:i], q.messages[i+1:]...)
			return
		}
	}
}

// DeadLetterQueue returns the messages that exceeded maxRetries without being Ack'd.
func (q *Queue) DeadLetterQueue() []Message {
	q.mu.Lock()
	defer q.mu.Unlock()
	return append([]Message(nil), q.deadLetters...)
}
