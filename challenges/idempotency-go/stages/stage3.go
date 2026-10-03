// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
package main

import (
	"errors"
	"fmt"
	"os"
	"slices"
	"sync"
	"time"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

// must fails the stage on an error the stage did not expect.
func must(result any, err error) any {
	if err != nil {
		panic(failure(fmt.Sprintf("unexpected error: %v", err)))
	}
	return result
}

func stage() {
	layer := NewIdempotencyLayer(24 * time.Hour)
	var mu sync.Mutex // guards charges, firstResult, outcome and crashed
	var charges []string
	started := make(chan struct{})
	release := make(chan struct{})

	slowCharge := func() (any, error) { // the payment gateway takes a while to answer
		mu.Lock()
		charges = append(charges, "slow")
		mu.Unlock()
		close(started)
		select {
		case <-release:
		case <-time.After(5 * time.Second):
		}
		return "ch_1", nil
	}
	fastCharge := func() (any, error) {
		mu.Lock()
		charges = append(charges, "fast")
		mu.Unlock()
		return "ch_dup", nil
	}

	var firstResult, outcome []any
	var crashed any
	// A panic in a goroutine would kill the process before main can report it.
	recordPanic := func() {
		if r := recover(); r != nil {
			mu.Lock()
			if crashed == nil {
				crashed = r
			}
			mu.Unlock()
		}
	}
	firstDone := make(chan struct{})
	go func() {
		defer close(firstDone)
		defer recordPanic()
		result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, slowCharge)
		mu.Lock()
		if err != nil {
			firstResult = append(firstResult, err)
		} else {
			firstResult = append(firstResult, result)
		}
		mu.Unlock()
	}()
	select {
	case <-started:
	case <-firstDone: // finished (or crashed) without ever running slowCharge
	case <-time.After(5 * time.Second):
		expect(false, "the first request never started running")
	}
	mu.Lock()
	firstCrash := crashed
	mu.Unlock()
	if firstCrash != nil {
		panic(firstCrash)
	}

	dupDone := make(chan struct{})
	go func() {
		defer close(dupDone)
		defer recordPanic()
		result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, fastCharge)
		mu.Lock()
		switch {
		case errors.Is(err, ErrIdempotencyInProgress):
			outcome = append(outcome, "in-progress")
		case err != nil:
			outcome = append(outcome, err)
		default:
			outcome = append(outcome, result)
		}
		mu.Unlock()
	}()
	blocked := false
	select {
	case <-dupDone:
	case <-time.After(2 * time.Second):
		blocked = true
	}
	close(release)
	for _, done := range []chan struct{}{firstDone, dupDone} {
		select {
		case <-done:
		case <-time.After(5 * time.Second):
		}
	}

	mu.Lock()
	gotCharges, gotOutcome, gotFirst, gotCrash := slices.Clone(charges), slices.Clone(outcome), slices.Clone(firstResult), crashed
	mu.Unlock()
	if gotCrash != nil {
		panic(gotCrash)
	}
	expect(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while operation runs; reject it instead")
	expect(slices.Equal(gotCharges, []string{"slow"}), "a duplicate arriving mid-flight must not charge again, charges were %v", gotCharges)
	expect(slices.Equal(gotOutcome, []any{"in-progress"}), "the duplicate should get ErrIdempotencyInProgress, got %v", gotOutcome)
	expect(slices.Equal(gotFirst, []any{"ch_1"}), "the first request should still finish normally, got %v", gotFirst)
	again := must(layer.Execute("order-1", map[string]any{"amount": 1000}, fastCharge))
	expect(again == "ch_1", "once finished, the key should replay ch_1")
}
