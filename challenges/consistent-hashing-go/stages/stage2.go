// Stage 2 — minimal remapping.
// 학습 포인트: hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다.
package main

import (
	"fmt"
	"os"
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

var keys = func() []string {
	ks := make([]string, 10000)
	for i := range ks {
		ks[i] = fmt.Sprintf("key-%d", i)
	}
	return ks
}()

func snapshot(ring *HashRing) map[string]string {
	owners := make(map[string]string, len(keys))
	for _, k := range keys {
		owners[k], _ = ring.GetNode(k)
	}
	return owners
}

func stage() {
	ring := NewHashRing(100)
	for _, node := range []string{"a", "b", "c"} {
		ring.AddNode(node)
	}
	before := snapshot(ring)

	ring.AddNode("d")
	after := snapshot(ring)
	var moved, wrong []string
	for _, k := range keys {
		if before[k] != after[k] {
			moved = append(moved, k)
			if after[k] != "d" {
				wrong = append(wrong, k)
			}
		}
	}
	if len(wrong) > 0 {
		w := wrong[0]
		expect(false, "adding d must only move keys TO d, but %d keys moved between old nodes (e.g. %s: %s -> %s)", len(wrong), w, before[w], after[w])
	}
	// How MANY keys move depends on how big d's slice of the ring is (that's stage 3's business);
	// what consistent hashing guarantees is WHERE they move.
	expect(len(moved) > 0, "the new node d should take over some keys")

	ring.RemoveNode("b")
	final := snapshot(ring)
	disturbed := 0
	for _, k := range keys {
		if after[k] != "b" && final[k] != after[k] {
			disturbed++
		}
	}
	expect(disturbed == 0, "removing b must only move b's keys, but %d other keys moved", disturbed)
	for _, owner := range final {
		expect(owner != "b", "no key should map to a removed node")
	}
}
