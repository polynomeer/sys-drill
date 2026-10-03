// SysDrill Build Mode — Build your own Rate Limiter (Go)
//
// Implement RateLimiter below across 6 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"sync"
	"time"
)

// Store is a key -> counter store, standing in for something like Redis.
type Store interface {
	Incr(key string) (int64, error)
	Expire(key string, ttl time.Duration) error
}

// ErrStoreUnavailable is what a store that can't be reached returns — see FaultyStore.
var ErrStoreUnavailable = errors.New("store unavailable")

// InMemoryStore is shared by every RateLimiter constructed with the same
// *InMemoryStore — passing one store to two limiters is how stage 4
// simulates "multiple instances behind a shared rate-limit store".
//
// Each map access is guarded on its own, but Incr is a read, a round trip,
// then a write — like a GET and a SET against Redis — so two concurrent
// Incr calls on the same key can race. That's intentional: making Allow
// safe under concurrent calls is RateLimiter's job (stage 3).
type InMemoryStore struct {
	mu     sync.Mutex
	counts map[string]int64
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{counts: map[string]int64{}}
}

func (s *InMemoryStore) Incr(key string) (int64, error) {
	current := s.load(key)
	time.Sleep(time.Millisecond) // simulated network latency between the read and the write
	next := current + 1
	s.store(key, next)
	return next, nil
}

func (s *InMemoryStore) Expire(key string, ttl time.Duration) error {
	// TODO(stage 2): make the counter for key reset to 0 after ttl.
	// Until you do, this does nothing — so a window never ends.
	return nil
}

func (s *InMemoryStore) load(key string) int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.counts[key]
}

func (s *InMemoryStore) store(key string, value int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.counts[key] = value
}

// FaultyStore always fails — stage 5 uses it to simulate the store (e.g. Redis)
// being down, so you can test the fail mode.
type FaultyStore struct{}

func (FaultyStore) Incr(key string) (int64, error) { return 0, ErrStoreUnavailable }

func (FaultyStore) Expire(key string, ttl time.Duration) error { return ErrStoreUnavailable }

type FailMode int

const (
	FailOpen FailMode = iota
	FailClosed
)

type Metrics struct {
	Allowed    int64
	Rejected   int64
	RejectRate float64
}

type RateLimiter struct {
	capacity int
	window   time.Duration
	store    Store
	failMode FailMode
}

// NewRateLimiter builds a limiter. Stage 4: callers may pass a *shared*
// store; nil means a fresh InMemoryStore of its own.
func NewRateLimiter(capacity int, window time.Duration, store Store, failMode FailMode) *RateLimiter {
	if store == nil {
		store = NewInMemoryStore()
	}
	return &RateLimiter{capacity: capacity, window: window, store: store, failMode: failMode}
}

func (r *RateLimiter) Allow(key string) bool {
	// Stage 1 — uncomment the three lines below, delete the `panic(...)` line, and submit.
	// count, _ := r.store.Incr(key)
	// if count == 1 { r.store.Expire(key, r.window) }
	// return count <= int64(r.capacity)
	// TODO(stage 3): make this safe under concurrent calls.
	// TODO(stage 5): when the store returns an error, admit if failMode == FailOpen,
	// reject if failMode == FailClosed.
	// TODO(stage 6): track allowed/rejected counts for Metrics.
	panic("not implemented")
}

func (r *RateLimiter) Metrics() Metrics {
	// TODO(stage 6): return Metrics{Allowed: ..., Rejected: ..., RejectRate: ...}.
	panic("not implemented")
}
