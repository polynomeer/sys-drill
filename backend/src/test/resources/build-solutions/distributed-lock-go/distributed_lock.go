package main

import (
	"sync"
	"time"
)

type lease struct {
	ownerID   string
	token     int64
	expiresAt time.Time
}

type LockStore struct {
	mu     sync.Mutex
	leases map[string]lease
	// Shared across keys and never reset, so a token is always greater than every earlier one.
	lastToken int64
}

func NewLockStore() *LockStore {
	return &LockStore{leases: map[string]lease{}}
}

// holder returns the unexpired lease on key, if any. Callers must hold s.mu.
func (s *LockStore) holder(key string) (lease, bool) {
	l, ok := s.leases[key]
	if !ok || !time.Now().Before(l.expiresAt) {
		return lease{}, false
	}
	return l, true
}

func (s *LockStore) TryAcquire(key, ownerID string, ttl time.Duration) (int64, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, held := s.holder(key); held {
		return 0, false
	}
	s.lastToken++
	s.leases[key] = lease{ownerID: ownerID, token: s.lastToken, expiresAt: time.Now().Add(ttl)}
	return s.lastToken, true
}

func (s *LockStore) TryRelease(key, ownerID string, token int64) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	l, held := s.holder(key)
	if !held || l.ownerID != ownerID || l.token != token {
		return false
	}
	delete(s.leases, key)
	return true
}

func (s *LockStore) IsLocked(key string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	_, held := s.holder(key)
	return held
}

type DistributedLock struct {
	key   string
	store *LockStore
	lease time.Duration
}

func NewDistributedLock(key string, store *LockStore, lease time.Duration) *DistributedLock {
	if store == nil {
		store = NewLockStore()
	}
	return &DistributedLock{key: key, store: store, lease: lease}
}

// Acquire relies on TryAcquire's check-and-set being atomic, like SET NX PX in Redis.
func (l *DistributedLock) Acquire(ownerID string) (int64, bool) {
	return l.store.TryAcquire(l.key, ownerID, l.lease)
}

func (l *DistributedLock) Release(ownerID string, fencingToken int64) bool {
	return l.store.TryRelease(l.key, ownerID, fencingToken)
}

func (l *DistributedLock) IsLocked() bool {
	return l.store.IsLocked(l.key)
}
