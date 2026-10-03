// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 ErrRetryExhausted, 그 이상 시도하지 않음.
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

func stage() {
	attempts := 0
	alwaysFail := func() (any, error) {
		attempts++
		return nil, errors.New("boom")
	}

	policy := NewRetryPolicy(4, time.Millisecond, time.Second, nil, func(time.Duration) {})
	_, err := policy.Execute(alwaysFail)
	expect(errors.Is(err, ErrRetryExhausted), "expected ErrRetryExhausted once maxAttempts is exceeded, got %v", err)
	expect(attempts == 4, "expected exactly 4 attempts (maxAttempts), got %d", attempts)
}
