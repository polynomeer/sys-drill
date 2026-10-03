// Stage 1 — ring lookup.
// 학습 포인트: 키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다.
package main

import (
	"fmt"
	"maps"
	"os"
	"slices"
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

// ownersOf returns the sorted set of nodes owning keys key-0 .. key-(count-1).
func ownersOf(ring *HashRing, count int) []string {
	owners := map[string]bool{}
	for i := 0; i < count; i++ {
		owner, _ := ring.GetNode(fmt.Sprintf("key-%d", i))
		owners[owner] = true
	}
	return slices.Sorted(maps.Keys(owners))
}

func stage() {
	ring := NewHashRing(100)
	_, ok := ring.GetNode("user-1")
	expect(!ok, "an empty ring has no owner for any key")

	ring.AddNode("cache-a")
	owners := ownersOf(ring, 100)
	expect(slices.Equal(owners, []string{"cache-a"}), "with a single node, it should own every key, got %q", owners)

	ring.AddNode("cache-b")
	ring.AddNode("cache-c")
	for i := 0; i < 1000; i++ {
		key := fmt.Sprintf("key-%d", i)
		owner, _ := ring.GetNode(key)
		expect(slices.Contains([]string{"cache-a", "cache-b", "cache-c"}, owner), "%s mapped to unknown node %q", key, owner)
		again, _ := ring.GetNode(key)
		expect(again == owner, "%s must map to the same node every time", key)
	}
	spread := ownersOf(ring, 1000)
	expect(slices.Equal(spread, []string{"cache-a", "cache-b", "cache-c"}), "1000 keys should land on all 3 nodes, got %q", spread)
}
