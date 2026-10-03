// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).
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
	time.Sleep(400 * time.Millisecond)
	expect(cb.State() == StateHalfOpen, "expected HALF_OPEN after the recovery timeout elapsed, got %s", cb.State())

	// The trial call's own error is expected; anything else is not.
	if _, err := cb.Call(fail); err != nil && !errors.Is(err, errBoom) {
		panic(err)
	}
	expect(cb.State() == StateOpen, "a failed HALF_OPEN trial should return to OPEN, got %s", cb.State())

	_, err := cb.Call(func() (any, error) { return "should not run", nil })
	expect(errors.Is(err, ErrCircuitOpen), "expected ErrCircuitOpen immediately after a failed trial (timeout must reset), got %v", err)
}
