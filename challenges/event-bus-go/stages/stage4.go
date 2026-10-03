// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 고루틴이 동시에 poll해도 중복/유실 없음.
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
	bus := NewEventBus(5*time.Second, 3)
	sub := bus.Subscribe("orders")
	for i := 0; i < 20; i++ {
		bus.Publish("orders", i)
	}

	var mu sync.Mutex
	var received []int
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	start := make(chan struct{})
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
			<-start
			for {
				msg, ok := bus.Poll(sub)
				if !ok {
					return
				}
				mu.Lock()
				received = append(received, msg.Payload.(int))
				mu.Unlock()
			}
		}()
	}
	close(start)
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
	expect(slices.Equal(received, expected), "each event should be delivered exactly once across concurrent pollers")
}
