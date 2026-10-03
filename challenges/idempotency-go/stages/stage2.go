// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
package main

import (
	"errors"
	"fmt"
	"os"
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
	var charges []int
	charge := func() (any, error) {
		charges = append(charges, 1)
		return fmt.Sprintf("ch_%d", len(charges)), nil
	}

	must(layer.Execute("order-1", map[string]any{"amount": 1000, "currency": "KRW"}, charge))
	// a new map with the same contents is the same request
	replay := must(layer.Execute("order-1", map[string]any{"amount": 1000, "currency": "KRW"}, charge))
	expect(replay == "ch_1", "an equal request should be replayed, got %v", replay)

	_, err := layer.Execute("order-1", map[string]any{"amount": 2000, "currency": "KRW"}, charge)
	expect(errors.Is(err, ErrIdempotencyConflict), "reusing a key with a different request should return ErrIdempotencyConflict, got %v", err)
	expect(len(charges) == 1, "a conflicting request must not charge, got %d charges", len(charges))
}
