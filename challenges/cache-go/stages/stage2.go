// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
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
	c := NewCache(3)
	c.Set("a", 1, 60*time.Second)
	c.Set("b", 2, 60*time.Second)
	c.Set("c", 3, 60*time.Second)
	a, _ := c.Get("a")
	expect(a == 1, "a should still be cached (capacity is 3)") // a is now the most recently used
	c.Set("d", 4, 60*time.Second)                              // over capacity: b is the least recently used
	_, ok := c.Get("b")
	expect(!ok, "b was the least recently used key and should have been evicted")
	a, _ = c.Get("a")
	expect(a == 1, "a was read just before the insert, so it must survive")
	v, _ := c.Get("c")
	expect(v == 3, "c should survive — only one key needed to go")
	d, _ := c.Get("d")
	expect(d == 4, "the newly inserted key d should be cached")
}
