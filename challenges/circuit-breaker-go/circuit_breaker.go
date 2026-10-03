// SysDrill Build Mode — Build your own Circuit Breaker (Go)
//
// Implement CircuitBreaker below across 4 stages (see README.md).
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

type State string

const (
	StateClosed   State = "CLOSED"
	StateOpen     State = "OPEN"
	StateHalfOpen State = "HALF_OPEN"
)

// ErrCircuitOpen is what Call returns when the breaker is OPEN — the wrapped
// function must not run.
var ErrCircuitOpen = errors.New("circuit open")

// CircuitBreaker wraps calls to a possibly-failing function (e.g. an external
// API) and stops calling it once it's clearly broken, instead of letting every
// caller wait out its own timeout.
type CircuitBreaker struct {
	// TODO(stage 1): fields for the config and the current state.
}

func NewCircuitBreaker(failureThreshold int, recoveryTimeout time.Duration) *CircuitBreaker {
	// TODO(stage 1): store config and start StateClosed.
	panic("not implemented")
}

func (cb *CircuitBreaker) State() State {
	// TODO(stage 1): StateClosed | StateOpen | StateHalfOpen.
	// TODO(stage 3): once OPEN and recoveryTimeout has elapsed since the
	// trip, reading the state should report StateHalfOpen (a real trial call
	// hasn't necessarily happened yet — this is a state *transition*,
	// not just a label).
	panic("not implemented")
}

func (cb *CircuitBreaker) Call(fn func() (any, error)) (any, error) {
	// TODO(stage 1): while CLOSED, call fn and return its result and error.
	// TODO(stage 2): count consecutive failures (fn returning a non-nil error);
	// once failureThreshold is reached, trip to OPEN. While OPEN, return
	// ErrCircuitOpen immediately WITHOUT calling fn — that's the whole point
	// (fail fast).
	// TODO(stage 3): once the state has moved to HALF_OPEN (see State), the
	// next Call is a *trial*: run fn for real, and if it succeeds, recover
	// to CLOSED (reset the failure count too).
	// TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN and
	// restart the recoveryTimeout countdown from now.
	panic("not implemented")
}
