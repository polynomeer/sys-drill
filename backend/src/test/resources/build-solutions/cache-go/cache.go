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

import (
	"container/list"
	"sync"
	"time"
)

// Stats is what Stats() hands out. HitRatio is 0.0 when there were no Gets yet.
type Stats struct {
	Hits     int
	Misses   int
	HitRatio float64
}

type entry struct {
	key       string
	value     any
	expiresAt time.Time
}

// call is one in-flight load; done is closed once value is set.
type call struct {
	done  chan struct{}
	value any
}

// Cache is an in-process read-through cache in front of a slow loader (e.g.
// the product DB): entries expire after a TTL, the least recently used entry
// is evicted once capacity is reached, and concurrent misses for the same
// key share a single load instead of all hitting the loader.
type Cache struct {
	capacity int

	mu      sync.Mutex
	order   *list.List // of *entry; front = most recently used
	entries map[string]*list.Element
	loading map[string]*call
	hits    int
	misses  int
}

func NewCache(capacity int) *Cache {
	return &Cache{
		capacity: capacity,
		order:    list.New(),
		entries:  make(map[string]*list.Element),
		loading:  make(map[string]*call),
	}
}

// Set stores value under key; it expires ttl from now.
func (c *Cache) Set(key string, value any, ttl time.Duration) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.put(key, value, ttl)
}

// Get returns the value for key, or ok == false if it's missing or expired.
func (c *Cache) Get(key string) (value any, ok bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	value, ok = c.lookup(key)
	if ok {
		c.hits++
	} else {
		c.misses++
	}
	return value, ok
}

// GetOrLoad returns the cached value for key, loading it with loader on a miss.
func (c *Cache) GetOrLoad(key string, loader func() any, ttl time.Duration) any {
	c.mu.Lock()
	if value, ok := c.lookup(key); ok {
		c.mu.Unlock()
		return value
	}
	if pending, ok := c.loading[key]; ok {
		c.mu.Unlock()
		<-pending.done // someone else is loading — share its result
		return pending.value
	}
	mine := &call{done: make(chan struct{})}
	c.loading[key] = mine
	c.mu.Unlock()

	defer func() {
		c.mu.Lock()
		delete(c.loading, key)
		c.mu.Unlock()
		close(mine.done)
	}()
	mine.value = loader()
	c.mu.Lock()
	c.put(key, mine.value, ttl)
	c.mu.Unlock()
	return mine.value
}

func (c *Cache) Invalidate(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if el, ok := c.entries[key]; ok {
		c.order.Remove(el)
		delete(c.entries, key)
	}
}

func (c *Cache) Stats() Stats {
	c.mu.Lock()
	defer c.mu.Unlock()
	s := Stats{Hits: c.hits, Misses: c.misses}
	if total := c.hits + c.misses; total > 0 {
		s.HitRatio = float64(c.hits) / float64(total)
	}
	return s
}

// lookup returns the live value for key and marks it most recently used.
// The caller holds c.mu.
func (c *Cache) lookup(key string) (any, bool) {
	el, ok := c.entries[key]
	if !ok {
		return nil, false
	}
	e := el.Value.(*entry)
	if !time.Now().Before(e.expiresAt) {
		c.order.Remove(el)
		delete(c.entries, key)
		return nil, false
	}
	c.order.MoveToFront(el)
	return e.value, true
}

// put stores key as the most recently used entry, evicting from the back
// while over capacity. The caller holds c.mu.
func (c *Cache) put(key string, value any, ttl time.Duration) {
	e := &entry{key: key, value: value, expiresAt: time.Now().Add(ttl)}
	if el, ok := c.entries[key]; ok {
		el.Value = e
		c.order.MoveToFront(el)
	} else {
		c.entries[key] = c.order.PushFront(e)
	}
	for c.order.Len() > c.capacity {
		oldest := c.order.Back()
		c.order.Remove(oldest)
		delete(c.entries, oldest.Value.(*entry).key)
	}
}
