// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
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
	var recordedDelays []time.Duration
	alwaysFail := func() (any, error) {
		return nil, errors.New("boom")
	}

	policy := NewRetryPolicy(6, 10*time.Millisecond, 10*time.Second, nil, func(d time.Duration) {
		recordedDelays = append(recordedDelays, d)
	})
	if _, err := policy.Execute(alwaysFail); err != nil && !errors.Is(err, ErrRetryExhausted) {
		panic(err)
	}

	expect(len(recordedDelays) == 5, "expected 5 delays between 6 attempts, got %d", len(recordedDelays))
	distinct := map[time.Duration]bool{}
	for i, d := range recordedDelays {
		limit := min(10*time.Second, 10*time.Millisecond*time.Duration(1<<i))
		expect(0 <= d && d <= limit, "delay %d = %v should be within [0, %v] (exponential backoff cap)", i, d, limit)
		distinct[d] = true
	}
	expect(len(distinct) > 1, "jitter should make delays vary, not all be identical")
}
