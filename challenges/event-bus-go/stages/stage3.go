// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.
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
	sub := bus.Subscribe("orders")
	bus.Publish("orders", "a")
	bus.Publish("orders", "b")
	bus.Publish("orders", "c")

	msg1, _ := bus.Poll(sub)
	msg2, _ := bus.Poll(sub)
	msg3, _ := bus.Poll(sub)

	got := []any{msg1.Payload, msg2.Payload, msg3.Payload}
	expect(got[0] == "a" && got[1] == "b" && got[2] == "c", "expected FIFO order [a, b, c], got %v", got)
}
