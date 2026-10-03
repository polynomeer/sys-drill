// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
package main

import (
	"fmt"
	"math"
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
	c.Set("p1", "price=100", 60*time.Second)
	v, _ := c.Get("p1") // hit
	expect(v == "price=100", "expected price=100, got %v", v)
	c.Invalidate("p1")   // the price changed in the DB
	_, ok := c.Get("p1") // miss
	expect(!ok, "an invalidated key should miss")
	_, ok = c.Get("p2") // miss
	expect(!ok, "p2 was never set and should miss")
	c.Set("p1", "price=120", 60*time.Second)
	v, _ = c.Get("p1") // hit
	expect(v == "price=120", "after invalidation, the new value should be served")
	c.Invalidate("never-set") // must not panic

	s := c.Stats()
	expect(s.Hits == 2, "expected 2 hits, got %d", s.Hits)
	expect(s.Misses == 2, "expected 2 misses, got %d", s.Misses)
	expect(math.Abs(s.HitRatio-0.5) < 0.01, "expected HitRatio 0.5, got %v", s.HitRatio)
}
