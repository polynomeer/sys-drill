// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
package main

import (
	"fmt"
	"os"
	"reflect"
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
		charges = append(charges, 1000)
		return map[string]any{"charge_id": fmt.Sprintf("ch_%d", len(charges)), "amount": 1000}, nil
	}

	first := must(layer.Execute("order-1", map[string]any{"amount": 1000}, charge))
	retry := must(layer.Execute("order-1", map[string]any{"amount": 1000}, charge))
	expect(len(charges) == 1, "a retry with the same key must not charge again, got %d charges", len(charges))
	expect(reflect.DeepEqual(retry, first), "the retry should get the original result %v, got %v", first, retry)

	other := must(layer.Execute("order-2", map[string]any{"amount": 1000}, charge))
	expect(len(charges) == 2, "a different key is a different request and should charge")
	otherMap, _ := other.(map[string]any)
	expect(otherMap["charge_id"] == "ch_2", "expected ch_2 for the new key, got %v", other)
}
