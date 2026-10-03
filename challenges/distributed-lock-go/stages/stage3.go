// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.
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
	store := NewLockStore()
	lockA := NewDistributedLock("resource-1", store, 300*time.Millisecond)
	lockB := NewDistributedLock("resource-1", store, 5*time.Second)

	tokenA, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	time.Sleep(400 * time.Millisecond) // owner-a stalls (e.g. GC pause) past its own lease

	tokenB, ok := lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire after owner-a's lease expired")
	expect(tokenB > tokenA, "fencing tokens must increase monotonically across acquisitions")

	// owner-a wakes up late and tries to release with its now-stale token
	released := lockA.Release("owner-a", tokenA)
	expect(!released, "a stale owner/token pair must not be able to release the current holder's lock")
	expect(lockB.IsLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt")
}
