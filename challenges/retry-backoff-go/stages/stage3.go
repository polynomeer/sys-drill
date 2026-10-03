// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
package main

import (
	"errors"
	"fmt"
	"os"
	"slices"
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

// recordDelays is one full run of a policy that always fails: the 5 delays it
// asked to sleep between 6 attempts.
func recordDelays() []time.Duration {
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
	return recordedDelays
}

func stage() {
	first := recordDelays()
	second := recordDelays()
	for _, recordedDelays := range [][]time.Duration{first, second} {
		expect(len(recordedDelays) == 5, "expected 5 delays between 6 attempts, got %d", len(recordedDelays))
		for i, d := range recordedDelays {
			limit := min(10*time.Second, 10*time.Millisecond*time.Duration(1<<i))
			expect(0 <= d && d <= limit, "delay %d = %v should be within [0, %v] (exponential backoff cap)", i, d, limit)
		}
	}
	// Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
	// "they vary" proves nothing — jitter means two runs don't wait the same amounts.
	expect(!slices.Equal(first, second), "jitter should randomize the delays, but two runs waited exactly the same: %v", first)
}
