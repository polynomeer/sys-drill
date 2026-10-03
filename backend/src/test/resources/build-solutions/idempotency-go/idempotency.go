// SysDrill Build Mode — Build your own Idempotency Layer (Go)
//
// Implement IdempotencyLayer below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"fmt"
	"reflect"
	"sync"
	"time"
)

var ErrIdempotencyConflict = errors.New("idempotency key reused with a different request")

var ErrIdempotencyInProgress = errors.New("a request with this idempotency key is still running")

// entry is a key's record; done == false means the operation is still
// running (the key is claimed, no result yet).
type entry struct {
	request   map[string]any
	result    any
	done      bool
	expiresAt time.Time
}

type IdempotencyLayer struct {
	ttl     time.Duration
	mu      sync.Mutex
	entries map[string]*entry
}

func NewIdempotencyLayer(ttl time.Duration) *IdempotencyLayer {
	return &IdempotencyLayer{ttl: ttl, entries: make(map[string]*entry)}
}

func (l *IdempotencyLayer) Execute(key string, request map[string]any, operation func() (any, error)) (any, error) {
	if result, replayed, err := l.claim(key, request); replayed || err != nil {
		return result, err
	}

	succeeded := false
	defer func() {
		if !succeeded { // an error or a panic: nothing stored — a retry runs the operation again
			l.mu.Lock()
			delete(l.entries, key)
			l.mu.Unlock()
		}
	}()
	result, err := operation() // no lock held: a duplicate is rejected, not blocked
	if err != nil {
		return nil, err
	}
	l.mu.Lock()
	l.entries[key] = &entry{request: request, result: result, done: true, expiresAt: time.Now().Add(l.ttl)}
	l.mu.Unlock()
	succeeded = true
	return result, nil
}

// claim either replays the stored result for key (replayed == true), rejects
// the call, or marks key as in flight so the caller may run the operation.
func (l *IdempotencyLayer) claim(key string, request map[string]any) (result any, replayed bool, err error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	e, ok := l.entries[key]
	if ok && e.done && !time.Now().Before(e.expiresAt) {
		delete(l.entries, key)
		ok = false
	}
	if ok {
		if !reflect.DeepEqual(e.request, request) {
			return nil, false, fmt.Errorf("%w: key %q", ErrIdempotencyConflict, key)
		}
		if !e.done {
			return nil, false, fmt.Errorf("%w: key %q", ErrIdempotencyInProgress, key)
		}
		return e.result, true, nil
	}
	// claim the key before running the operation, so a concurrent duplicate sees it in flight
	l.entries[key] = &entry{request: request}
	return nil, false, nil
}
