// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
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
	sharedStore := NewInMemoryStore()
	instanceA := NewRateLimiter(5, 5*time.Second, sharedStore, FailOpen)
	instanceB := NewRateLimiter(5, 5*time.Second, sharedStore, FailOpen)
	totalAllowed := 0
	for i := 0; i < 10; i++ {
		limiter := instanceB
		if i%2 == 0 {
			limiter = instanceA
		}
		if limiter.Allow("shared-key") {
			totalAllowed++
		}
	}
	expect(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got %d", totalAllowed)
}
