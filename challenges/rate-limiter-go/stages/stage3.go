// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 고루틴이 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.Incr는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — Allow에 동시성 제어가 없으면 capacity를 넘긴다.
package main

import (
	"fmt"
	"os"
	"sync"
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
	rl := NewRateLimiter(50, 5*time.Second, nil, FailOpen)
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	for g := 0; g < 10; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			for i := 0; i < 20; i++ {
				rl.Allow("shared-key")
			}
		}()
	}
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}
	allowed := rl.Metrics().Allowed
	expect(allowed <= 50, "concurrent access let %d requests through, expected <= 50", allowed)
}
