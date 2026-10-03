// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).
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
	q := NewQueue(200*time.Millisecond, 2)
	q.Enqueue("y")
	for i := 0; i < 2; i++ {
		_, ok := q.Dequeue()
		expect(ok, "expected a message")
		time.Sleep(300 * time.Millisecond)
	}
	_, ok := q.Dequeue()
	expect(!ok, "message should no longer be deliverable after exceeding maxRetries")
	dlq := q.DeadLetterQueue()
	expect(len(dlq) == 1, "expected 1 message in DLQ, got %d", len(dlq))
	expect(dlq[0].Payload == "y", "expected y in DLQ, got %v", dlq[0].Payload)
}
