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
	"time"
)

// ErrIdempotencyConflict is returned by Execute when a key is reused with a different request.
var ErrIdempotencyConflict = errors.New("idempotency key reused with a different request")

// ErrIdempotencyInProgress is returned by Execute when a request with the same key is still running.
var ErrIdempotencyInProgress = errors.New("a request with this idempotency key is still running")

// IdempotencyLayer wraps a side-effecting operation (e.g. charging a card
// through a payment gateway) so that retries carrying the same idempotency
// key — a client timing out and resending, a double-clicked button — perform
// it at most once and get the original result back.
type IdempotencyLayer struct {
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewIdempotencyLayer(ttl time.Duration) *IdempotencyLayer {
	// TODO(stage 1): store config and set up whatever storage you need.
	// TODO(stage 4): keys are kept for ttl, then forgotten.
	panic("not implemented")
}

// Execute runs operation at most once per key and returns its result; a
// non-nil error from operation means it failed.
func (l *IdempotencyLayer) Execute(key string, request map[string]any, operation func() (any, error)) (any, error) {
	// TODO(stage 1): the first call for key runs operation() and stores its
	// result; every later call with the same key returns that stored result
	// WITHOUT calling operation again.
	// TODO(stage 2): remember the request each key was first used with. The
	// same key with a *different* request (compare by value with
	// reflect.DeepEqual) is a client bug — return ErrIdempotencyConflict
	// instead of replaying.
	// TODO(stage 3): while operation for a key is still running, another call
	// with that key must neither run it nor wait — return
	// ErrIdempotencyInProgress right away (the client retries later). Don't
	// hold a lock while operation runs.
	// TODO(stage 4): if operation returns an error, store nothing for the key
	// (return that error) so a retry can try again. Stored keys expire ttl
	// after they were stored.
	panic("not implemented")
}
