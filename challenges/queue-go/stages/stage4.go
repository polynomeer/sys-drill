// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 고루틴이
// 동시에 Dequeue해도 각 메시지는 정확히 한 번만 전달돼야 한다).
package main

import (
	"fmt"
	"os"
	"slices"
	"sync"
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
	for i := 0; i < 20; i++ {
		q.Enqueue(i)
	}

	var mu sync.Mutex
	var received []int
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	startGate := make(chan struct{})
	for g := 0; g < 5; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			<-startGate
			for {
				msg, ok := q.Dequeue()
				if !ok {
					break
				}
				mu.Lock()
				received = append(received, msg.Payload.(int))
				mu.Unlock()
			}
		}()
	}
	close(startGate)
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}

	expect(len(received) == 20, "expected 20 deliveries, got %d", len(received))
	slices.Sort(received)
	expected := make([]int, 20)
	for i := range expected {
		expected[i] = i
	}
	expect(slices.Equal(received, expected), "each message should be delivered exactly once across concurrent consumers")
}
