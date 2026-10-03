// SysDrill Build Mode — Build your own Circuit Breaker (Go)
//
// Reference solution — passes all 4 stages.
package main

import (
	"errors"
	"sync"
	"time"
)

type State string

const (
	StateClosed   State = "CLOSED"
	StateOpen     State = "OPEN"
	StateHalfOpen State = "HALF_OPEN"
)

// ErrCircuitOpen is what Call returns when the breaker is OPEN — the wrapped
// function must not run.
var ErrCircuitOpen = errors.New("circuit open")

type CircuitBreaker struct {
	failureThreshold int
	recoveryTimeout  time.Duration

	mu                  sync.Mutex
	state               State
	consecutiveFailures int
	openedAt            time.Time
}

func NewCircuitBreaker(failureThreshold int, recoveryTimeout time.Duration) *CircuitBreaker {
	return &CircuitBreaker{
		failureThreshold: failureThreshold,
		recoveryTimeout:  recoveryTimeout,
		state:            StateClosed,
	}
}

func (cb *CircuitBreaker) State() State {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	return cb.currentState()
}

// currentState applies the OPEN -> HALF_OPEN transition; cb.mu must be held.
func (cb *CircuitBreaker) currentState() State {
	if cb.state == StateOpen && time.Since(cb.openedAt) >= cb.recoveryTimeout {
		cb.state = StateHalfOpen
	}
	return cb.state
}

func (cb *CircuitBreaker) Call(fn func() (any, error)) (any, error) {
	if cb.State() == StateOpen {
		return nil, ErrCircuitOpen
	}
	result, err := fn()

	cb.mu.Lock()
	defer cb.mu.Unlock()
	if err != nil {
		cb.consecutiveFailures++
		// A failed HALF_OPEN trial re-trips immediately, regardless of the threshold.
		if cb.state == StateHalfOpen || cb.consecutiveFailures >= cb.failureThreshold {
			cb.state = StateOpen
			cb.openedAt = time.Now()
		}
		return result, err
	}
	cb.consecutiveFailures = 0
	cb.state = StateClosed
	return result, nil
}
