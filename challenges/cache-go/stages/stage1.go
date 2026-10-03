// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
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
	c := NewCache(10)
	_, ok := c.Get("missing")
	expect(!ok, "a key that was never set should be a miss (ok == false)")
	c.Set("p1", "price=100", 300*time.Millisecond)
	p1, _ := c.Get("p1")
	expect(p1 == "price=100", "expected price=100 before the TTL, got %v", p1)
	c.Set("p2", "price=200", 5*time.Second)
	time.Sleep(400 * time.Millisecond)
	_, ok = c.Get("p1")
	expect(!ok, "p1 should have expired after its 0.3s TTL")
	p2, _ := c.Get("p2")
	expect(p2 == "price=200", "p2 has a 5s TTL and should still be cached")
}
