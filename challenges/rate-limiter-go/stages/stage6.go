// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
package main

import (
	"fmt"
	"math"
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
	rl := NewRateLimiter(2, 5*time.Second, nil, FailOpen)
	rl.Allow("k")
	rl.Allow("k")
	rl.Allow("k")
	m := rl.Metrics()
	expect(m.Allowed == 2, "expected 2 allowed, got %d", m.Allowed)
	expect(m.Rejected == 1, "expected 1 rejected, got %d", m.Rejected)
	expect(math.Abs(m.RejectRate-1.0/3) < 0.01, "expected RejectRate ~0.333, got %v", m.RejectRate)
}
