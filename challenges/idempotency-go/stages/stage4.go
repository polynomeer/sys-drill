// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
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

var errGatewayTimeout = errors.New("gateway timeout")

func stage() {
	layer := NewIdempotencyLayer(300 * time.Millisecond)
	attempts := 0
	flakyCharge := func() (any, error) {
		attempts++
		if attempts == 1 {
			return nil, errGatewayTimeout
		}
		return "ch_1", nil
	}

	_, err := layer.Execute("order-1", map[string]any{"amount": 1000}, flakyCharge)
	expect(errors.Is(err, errGatewayTimeout), "the operation's own error should propagate to the caller, got %v", err)
	result, err := layer.Execute("order-1", map[string]any{"amount": 1000}, flakyCharge)
	expect(err == nil && result == "ch_1", "a retry after a failure should run the operation again, got %v (error %v)", result, err)
	expect(attempts == 2, "expected 2 attempts (the failure is not stored), got %d", attempts)

	time.Sleep(400 * time.Millisecond)
	again, err := layer.Execute("order-1", map[string]any{"amount": 5000}, func() (any, error) { return "ch_new", nil })
	expect(err == nil && again == "ch_new", "after the ttl the key is forgotten and may be reused, got %v (error %v)", again, err)
}
