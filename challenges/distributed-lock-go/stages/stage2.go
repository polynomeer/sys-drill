// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.
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
	lockB := NewDistributedLock("resource-1", store, 300*time.Millisecond)

	_, ok := lockA.Acquire("owner-a")
	expect(ok, "owner-a should acquire the free lock")
	_, ok = lockB.Acquire("owner-b")
	expect(!ok, "owner-b should not acquire before the lease expires")

	time.Sleep(400 * time.Millisecond)
	expect(!lockA.IsLocked(), "the lock should report unlocked once the lease has expired")

	_, ok = lockB.Acquire("owner-b")
	expect(ok, "owner-b should acquire once owner-a's lease has expired without release")
}
