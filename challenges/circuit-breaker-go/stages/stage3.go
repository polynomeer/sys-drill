// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).
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
	fail := func() (any, error) { return nil, errBoom }

	cb := NewCircuitBreaker(1, 300*time.Millisecond)
	if _, err := cb.Call(fail); err != nil && !errors.Is(err, errBoom) {
		panic(err)
	}
	expect(cb.State() == StateOpen, "expected OPEN after 1 failure, got %s", cb.State())

	time.Sleep(400 * time.Millisecond)
	expect(cb.State() == StateHalfOpen, "expected HALF_OPEN after the recovery timeout elapsed, got %s", cb.State())

	result, err := cb.Call(func() (any, error) { return "recovered", nil })
	if err != nil {
		panic(err)
	}
	expect(result == "recovered", "expected \"recovered\", got %v", result)
	expect(cb.State() == StateClosed, "a successful HALF_OPEN trial should recover to CLOSED, got %s", cb.State())
}
