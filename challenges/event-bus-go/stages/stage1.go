// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.
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
	bus := NewEventBus(5*time.Second, 3)
	subA := bus.Subscribe("orders")
	subB := bus.Subscribe("orders")
	subC := bus.Subscribe("payments")

	bus.Publish("orders", "order-created")

	msgA, okA := bus.Poll(subA)
	msgB, okB := bus.Poll(subB)
	_, okC := bus.Poll(subC)

	expect(okA && msgA.Payload == "order-created", "sub_a should receive the event")
	expect(okB && msgB.Payload == "order-created", "sub_b should receive the event (fan-out)")
	expect(!okC, "a subscriber to a different topic should not receive the event")
}
