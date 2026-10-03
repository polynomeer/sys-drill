// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.
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
	flaky := func() (any, error) {
		attempts++
		if attempts < 3 {
			return nil, errors.New("boom")
		}
		return "success", nil
	}

	policy := NewRetryPolicy(5, time.Millisecond, time.Second, nil, func(time.Duration) {})
	result, err := policy.Execute(flaky)
	if err != nil {
		panic(err)
	}
	expect(result == "success", "expected success, got %v", result)
	expect(attempts == 3, "expected exactly 3 attempts (2 failures + 1 success), got %d", attempts)
}
