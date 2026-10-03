// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
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
	rl := NewRateLimiter(2, 500*time.Millisecond, nil, FailOpen)
	expect(rl.Allow("k"), "expected the first request to be allowed")
	expect(rl.Allow("k"), "expected the second request to be allowed")
	expect(!rl.Allow("k"), "third request within the window should be rejected")
	time.Sleep(700 * time.Millisecond)
	expect(rl.Allow("k"), "after the window elapses, capacity should replenish")
}
