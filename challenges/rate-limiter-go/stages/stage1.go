// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
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
	rl := NewRateLimiter(3, 10*time.Second, nil, FailOpen)
	allowed := 0
	for i := 0; i < 5; i++ {
		if rl.Allow("user-a") {
			allowed++
		}
	}
	expect(allowed == 3, "expected exactly 3 allowed within the window, got %d", allowed)
	expect(rl.Allow("user-b"), "a different key should not be affected by user-a's budget")
}
