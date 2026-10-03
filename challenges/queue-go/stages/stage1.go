// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).
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
	q := NewQueue(5*time.Second, 3)
	q.Enqueue("a")
	q.Enqueue("b")
	q.Enqueue("c")
	msg1, _ := q.Dequeue()
	msg2, _ := q.Dequeue()
	msg3, _ := q.Dequeue()
	expect(msg1.Payload == "a", "expected a, got %v", msg1.Payload)
	expect(msg2.Payload == "b", "expected b, got %v", msg2.Payload)
	expect(msg3.Payload == "c", "expected c, got %v", msg3.Payload)
	_, ok := q.Dequeue()
	expect(!ok, "queue should be empty")
}
