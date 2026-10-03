// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.
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
	budget := NewRetryBudget(2)

	attemptsP1 := 0
	alwaysFailP1 := func() (any, error) {
		attemptsP1++
		return nil, errors.New("boom")
	}
	policy1 := NewRetryPolicy(10, time.Millisecond, time.Second, budget, func(time.Duration) {})
	if _, err := policy1.Execute(alwaysFailP1); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}
	expect(attemptsP1 < 10, "a shared retry budget should cut retries short before maxAttempts is reached")

	attemptsP2 := 0
	alwaysFailP2 := func() (any, error) {
		attemptsP2++
		return nil, errors.New("boom")
	}
	policy2 := NewRetryPolicy(10, time.Millisecond, time.Second, budget, func(time.Duration) {})
	if _, err := policy2.Execute(alwaysFailP2); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}
	expect(attemptsP2 <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all")
}
