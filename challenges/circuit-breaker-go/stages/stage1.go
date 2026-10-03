// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).
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
	cb := NewCircuitBreaker(3, time.Second)
	result, err := cb.Call(func() (any, error) { return 42, nil })
	if err != nil {
		panic(err)
	}
	expect(result == 42, "expected 42, got %v", result)
	expect(cb.State() == StateClosed, "expected CLOSED, got %s", cb.State())
	for i := 0; i < 5; i++ {
		ok, err := cb.Call(func() (any, error) { return "ok", nil })
		if err != nil {
			panic(err)
		}
		expect(ok == "ok", "expected \"ok\", got %v", ok)
	}
	expect(cb.State() == StateClosed, "expected CLOSED after 5 successful calls, got %s", cb.State())
}
