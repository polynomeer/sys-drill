// SysDrill Build Mode — Build your own Retry/Backoff Middleware (Go)
//
// Implement RetryPolicy and RetryBudget below across 4 stages (see README.md).
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

// ErrRetryExhausted is what Execute returns when every attempt failed
// (check it with errors.Is — you may wrap the last attempt's error too).
var ErrRetryExhausted = errors.New("retry exhausted")

// RetryBudget is a shared token bucket that caps the *total* number of
// retries across every RetryPolicy that shares it — protects a downstream
// dependency from a retry storm even when many independent callers are each
// individually retrying. Pass the same *RetryBudget to multiple RetryPolicy
// instances to share it (stage 4).
type RetryBudget struct {
	// TODO(stage 4): fields.
}

func NewRetryBudget(capacity int) *RetryBudget {
	// TODO(stage 4): store the starting capacity.
	panic("not implemented")
}

func (b *RetryBudget) TryConsume() bool {
	// TODO(stage 4): if a token is available, consume it and return true.
	// If the budget is exhausted, return false (and consume nothing).
	panic("not implemented")
}

type RetryPolicy struct {
	// TODO(stage 1): fields.
}

// NewRetryPolicy builds a policy. budget may be nil (no shared budget) —
// stage 4 passes a *shared* one. sleep receives each delay; nil means
// time.Sleep.
func NewRetryPolicy(maxAttempts int, baseDelay, maxDelay time.Duration, budget *RetryBudget, sleep func(time.Duration)) *RetryPolicy {
	// TODO(stage 1): store config. Default sleep to time.Sleep if nil
	// (tests pass their own sleep so they don't have to actually wait).
	panic("not implemented")
}

func (p *RetryPolicy) Execute(fn func() (any, error)) (any, error) {
	// TODO(stage 1): call fn(). On success (nil error), return its result
	// immediately. On failure, retry up to maxAttempts total calls, then
	// return an error that matches ErrRetryExhausted.
	// TODO(stage 3): between attempts, call p's sleep(delay) where delay
	// grows exponentially with the attempt number (baseDelay * 2^attempt,
	// capped at maxDelay) *with jitter* — don't use the exact exponential
	// value, pick randomly within [0, cappedValue] ("full jitter") so many
	// simultaneous retriers don't all retry at the exact same moment
	// (thundering herd).
	// TODO(stage 4): if a budget was provided, call budget.TryConsume()
	// before each retry (not before the first attempt). If it returns
	// false, stop retrying immediately (return ErrRetryExhausted) even if
	// maxAttempts hasn't been reached yet.
	panic("not implemented")
}
