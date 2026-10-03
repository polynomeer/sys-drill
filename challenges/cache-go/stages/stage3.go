// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
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

func stage() {
	c := NewCache(10)
	var loads atomic.Int32
	slowLoader := func() any {
		loads.Add(1)
		time.Sleep(200 * time.Millisecond) // a slow DB query
		return "product-42"
	}

	var mu sync.Mutex
	var results []any
	var wg sync.WaitGroup
	var once sync.Once
	var crashed any
	startGate := make(chan struct{})
	for g := 0; g < 8; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// A panic in a goroutine would kill the process before main can report it.
			defer func() {
				if r := recover(); r != nil {
					once.Do(func() { crashed = r })
				}
			}()
			<-startGate
			v := c.GetOrLoad("hot", slowLoader, 5*time.Second)
			mu.Lock()
			results = append(results, v)
			mu.Unlock()
		}()
	}
	close(startGate)
	wg.Wait()
	if crashed != nil {
		panic(crashed)
	}

	expect(loads.Load() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got %d", loads.Load())
	allLoaded := len(results) == 8
	for _, v := range results {
		allLoaded = allLoaded && v == "product-42"
	}
	expect(allLoaded, "every caller should get the loaded value, got %v", results)
	v := c.GetOrLoad("hot", slowLoader, 5*time.Second)
	expect(v == "product-42", "expected product-42 from the cache, got %v", v)
	expect(loads.Load() == 1, "once loaded, the value should come from the cache, not the loader")
}
