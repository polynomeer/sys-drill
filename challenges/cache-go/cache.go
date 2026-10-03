// SysDrill Build Mode — Build your own Cache (Go)
//
// Implement Cache below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// Stats is what Stats() hands out. HitRatio is 0.0 when there were no Gets yet.
type Stats struct {
	Hits     int
	Misses   int
	HitRatio float64
}

// Cache is an in-process read-through cache in front of a slow loader (e.g.
// the product DB): entries expire after a TTL, the least recently used entry
// is evicted once capacity is reached, and concurrent misses for the same
// key share a single load instead of all hitting the loader.
type Cache struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewCache(capacity int) *Cache {
	// TODO(stage 1): store config and set up whatever storage you need.
	panic("not implemented")
}

// Set stores value under key; it expires ttl from now.
func (c *Cache) Set(key string, value any, ttl time.Duration) {
	// TODO(stage 1): store value under key; it expires ttl from now.
	// TODO(stage 2): once more than capacity keys are stored, evict the
	// least recently used one (a Get or Set counts as a use).
	panic("not implemented")
}

// Get returns the value for key, or ok == false if it's missing or expired.
func (c *Cache) Get(key string) (value any, ok bool) {
	// TODO(stage 1): the value for key, or (nil, false) if it's missing or expired.
	// TODO(stage 4): count every Get as a hit or a miss for Stats().
	panic("not implemented")
}

// GetOrLoad returns the cached value for key, loading it with loader on a miss.
func (c *Cache) GetOrLoad(key string, loader func() any, ttl time.Duration) any {
	// TODO(stage 3): return the cached value if present. Otherwise call
	// loader() — a slow call such as a DB query — store its result with
	// ttl, and return it. When many goroutines miss the same key at the
	// same time, loader() must run only ONCE; the others wait for that one
	// load and get its result (single-flight — this is what stops a cache
	// stampede from flattening the DB when a hot key expires).
	panic("not implemented")
}

func (c *Cache) Invalidate(key string) {
	// TODO(stage 4): drop key so the next read misses (e.g. after the
	// product's price changed in the DB).
	panic("not implemented")
}

func (c *Cache) Stats() Stats {
	// TODO(stage 4): Stats{Hits, Misses, HitRatio}
	// (HitRatio is 0.0 when there were no Gets yet).
	panic("not implemented")
}
