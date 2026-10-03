// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).
package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func stage() {
	q := NewQueue(300*time.Millisecond, 3)
	q.Enqueue("x")
	_, ok := q.Dequeue()
	expect(ok, "expected a message")
	_, ok = q.Dequeue()
	expect(!ok, "in-flight message should not be immediately re-deliverable")
	time.Sleep(400 * time.Millisecond)
	redelivered, ok := q.Dequeue()
	expect(ok, "message should be redelivered after visibility timeout without ack")
	expect(redelivered.Payload == "x", "expected x, got %v", redelivered.Payload)
	q.Ack(redelivered.ID)
	_, ok = q.Dequeue()
	expect(!ok, "acked message should not be redelivered")
}
