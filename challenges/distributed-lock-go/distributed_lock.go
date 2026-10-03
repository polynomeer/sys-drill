// SysDrill Build Mode — Build your own Distributed Lock (Go)
//
// Implement LockStore and DistributedLock below across 4 stages (see
// README.md). Keep the type, function and method names as-is — the stage
// tests call them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import "time"

// LockStore is a minimal shared key -> (owner, expiry, fencing token) store,
// shared by every DistributedLock constructed with the same *LockStore.
// Passing the same store to two DistributedLocks is how the stages simulate
// "multiple processes/instances talking to the same external lock service
// (e.g. Redis)" without needing a real one.
type LockStore struct {
	// TODO(stage 1): add whatever storage you need.
}

func NewLockStore() *LockStore {
	// TODO(stage 1): initialize that storage.
	return &LockStore{}
}

// TryAcquire returns a fencing token and true, or (0, false) if the lock
// wasn't acquired.
func (s *LockStore) TryAcquire(key, ownerID string, lease time.Duration) (int64, bool) {
	// TODO(stage 1): if key is free, claim it for ownerID and return a
	// fencing token. If it's already held by someone whose lease hasn't
	// expired, return (0, false).
	// TODO(stage 2): a lease expires `lease` after it was acquired — after
	// that, the key is free again even without TryRelease.
	// TODO(stage 3): each successful acquisition must get a fencing token
	// strictly greater than every token issued before it, even across
	// different owners and even after the key was released/expired.
	panic("not implemented")
}

func (s *LockStore) TryRelease(key, ownerID string, token int64) bool {
	// TODO(stage 1): release key only if ownerID+token match the current
	// holder; return whether it actually released anything.
	// TODO(stage 3): a stale owner/token (e.g. from an owner that woke up
	// after its lease already expired and someone else acquired the lock)
	// must NOT be able to release the current holder's lock.
	panic("not implemented")
}

func (s *LockStore) IsLocked(key string) bool {
	// TODO(stage 1): whether key is currently held by an unexpired lease.
	panic("not implemented")
}

type DistributedLock struct {
	key   string
	store *LockStore
	lease time.Duration
}

// NewDistributedLock builds a lock handle for key. Stage 4: callers may pass
// a *shared* store; nil means a fresh LockStore of its own.
func NewDistributedLock(key string, store *LockStore, lease time.Duration) *DistributedLock {
	if store == nil {
		store = NewLockStore()
	}
	return &DistributedLock{key: key, store: store, lease: lease}
}

// Acquire returns a fencing token and true, or (0, false) if the lock
// wasn't acquired.
func (l *DistributedLock) Acquire(ownerID string) (int64, bool) {
	// TODO(stage 1): delegate to l.store.TryAcquire(...).
	// TODO(stage 4): make this safe when called concurrently — only one of
	// many simultaneous callers for the same key may succeed.
	panic("not implemented")
}

func (l *DistributedLock) Release(ownerID string, fencingToken int64) bool {
	// TODO(stage 1): delegate to l.store.TryRelease(...).
	panic("not implemented")
}

func (l *DistributedLock) IsLocked() bool {
	// TODO(stage 1): delegate to l.store.IsLocked(...).
	panic("not implemented")
}
