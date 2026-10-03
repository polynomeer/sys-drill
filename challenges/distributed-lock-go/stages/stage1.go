// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.
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
	lockA := NewDistributedLock("resource-1", store, 5*time.Second)
	lockB := NewDistributedLock("resource-1", store, 5*time.Second)

	tokenA, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	expect(lockA.IsLocked(), "the lock should report locked while owner-a holds it")

	_, ok = lockB.Acquire("owner-b")
	expect(!ok, "owner-b should not acquire while owner-a holds the lock")

	released := lockA.Release("owner-a", tokenA)
	expect(released, "owner-a should be able to release its own lock")

	_, ok = lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire after owner-a releases")
}
