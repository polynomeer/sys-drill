// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.
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
	bus := NewEventBus(300*time.Millisecond, 3)
	sub := bus.Subscribe("orders")
	bus.Publish("orders", "x")

	_, ok := bus.Poll(sub)
	expect(ok, "expected an event")
	_, ok = bus.Poll(sub)
	expect(!ok, "in-flight event should not be immediately re-deliverable")

	time.Sleep(400 * time.Millisecond)
	redelivered, ok := bus.Poll(sub)
	expect(ok, "event should be redelivered after visibility timeout without ack")
	expect(redelivered.Payload == "x", "redelivered event should carry the original payload")
	bus.Ack(sub, redelivered.ID)
	_, ok = bus.Poll(sub)
	expect(!ok, "acked event should not be redelivered")
}
