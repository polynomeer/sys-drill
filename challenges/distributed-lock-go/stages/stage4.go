// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
package main

import (
	"fmt"
	"os"
	"sync"
	"sync/atomic"
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

const (
	rounds     = 200
	contenders = 8
)

// One round of 8 simultaneous acquires can easily come out right by luck — the
// check-then-claim window is tiny — so the race is replayed on 200 fresh keys.
func stage() {
	store := NewLockStore()
	for round := 0; round < rounds; round++ {
		key := fmt.Sprintf("resource-%d", round)
		var successes atomic.Int64
		var wg sync.WaitGroup
		var once sync.Once
		var crashed any
		startGate := make(chan struct{})
		for i := 0; i < contenders; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				// A panic in a goroutine would kill the process before main can report it.
				defer func() {
					if r := recover(); r != nil {
						once.Do(func() { crashed = r })
					}
				}()
				lock := NewDistributedLock(key, store, 5*time.Second)
				<-startGate
				if _, ok := lock.Acquire(fmt.Sprintf("owner-%d", i)); ok {
					successes.Add(1)
				}
			}()
		}
		close(startGate)
		wg.Wait()
		if crashed != nil {
			panic(crashed)
		}
		expect(successes.Load() == 1, "round %d: expected exactly 1 successful acquire among %d concurrent attempts, got %d",
			round, contenders, successes.Load())
	}
}
