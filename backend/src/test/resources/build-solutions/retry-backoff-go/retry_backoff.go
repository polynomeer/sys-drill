// SysDrill Build Mode — Build your own Retry/Backoff Middleware (Go)
//
// Reference solution: passes all 4 stages.
package main

import (
	"errors"
	"fmt"
	"math"
	"math/rand/v2"
	"sync"
	"time"
)

// ErrRetryExhausted is what Execute returns when every attempt failed.
var ErrRetryExhausted = errors.New("retry exhausted")

// RetryBudget is a shared token bucket that caps the *total* number of
// retries across every RetryPolicy that shares it.
type RetryBudget struct {
	mu     sync.Mutex
	tokens int
}

func NewRetryBudget(capacity int) *RetryBudget {
	return &RetryBudget{tokens: capacity}
}

// TryConsume is called by every policy sharing the budget, so it must be
// safe for concurrent use.
func (b *RetryBudget) TryConsume() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.tokens <= 0 {
		return false
	}
	b.tokens--
	return true
}

type RetryPolicy struct {
	maxAttempts int
	baseDelay   time.Duration
	maxDelay    time.Duration
	budget      *RetryBudget
	sleep       func(time.Duration)
}

func NewRetryPolicy(maxAttempts int, baseDelay, maxDelay time.Duration, budget *RetryBudget, sleep func(time.Duration)) *RetryPolicy {
	if sleep == nil {
		sleep = time.Sleep
	}
	return &RetryPolicy{maxAttempts: maxAttempts, baseDelay: baseDelay, maxDelay: maxDelay, budget: budget, sleep: sleep}
}

func (p *RetryPolicy) Execute(fn func() (any, error)) (any, error) {
	var lastErr error
	for attempt := 0; attempt < p.maxAttempts; attempt++ {
		if attempt > 0 {
			if p.budget != nil && !p.budget.TryConsume() {
				return nil, fmt.Errorf("%w: budget exhausted after %d attempts: %w", ErrRetryExhausted, attempt, lastErr)
			}
			p.sleep(p.backoff(attempt - 1))
		}
		result, err := fn()
		if err == nil {
			return result, nil
		}
		lastErr = err
	}
	return nil, fmt.Errorf("%w: all %d attempts failed: %w", ErrRetryExhausted, p.maxAttempts, lastErr)
}

// backoff is full jitter: uniform in [0, min(maxDelay, baseDelay * 2^retry)].
func (p *RetryPolicy) backoff(retry int) time.Duration {
	capped := math.Min(float64(p.maxDelay), float64(p.baseDelay)*math.Pow(2, float64(retry)))
	return time.Duration(rand.Int64N(int64(capped) + 1))
}
