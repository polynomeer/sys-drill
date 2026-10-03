// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.
package main

import (
	"errors"
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

var errBoom = errors.New("boom")

func stage() {
	calls := 0
	flaky := func() (any, error) {
		calls++
		return nil, errBoom
	}

	cb := NewCircuitBreaker(3, 10*time.Second)
	for i := 0; i < 3; i++ {
		// The wrapped function's own error is expected; anything else is not.
		if _, err := cb.Call(flaky); err != nil && !errors.Is(err, errBoom) {
			panic(err)
		}
	}
	expect(cb.State() == StateOpen, "expected OPEN after 3 failures, got %s", cb.State())
	expect(calls == 3, "expected the function to run 3 times, got %d", calls)

	_, err := cb.Call(flaky)
	expect(errors.Is(err, ErrCircuitOpen), "expected ErrCircuitOpen while OPEN, got %v", err)
	expect(calls == 3, "the underlying function must not run while the circuit is OPEN (fail fast)")
}
