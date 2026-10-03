// Stage 4 — replicas.
// 학습 포인트: 복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 자연스럽게 주인이 된다.
package main

import (
	"fmt"
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

func stage() {
	ring := NewHashRing(100)
	expect(len(ring.GetNodes("k", 2)) == 0, "an empty ring has no replicas")
	for _, node := range []string{"a", "b", "c", "d", "e"} {
		ring.AddNode(node)
	}

	for i := 0; i < 200; i++ {
		key := fmt.Sprintf("key-%d", i)
		replicas := ring.GetNodes(key, 3)
		expect(len(replicas) == 3, "expected 3 replicas for %s, got %q", key, replicas)
		distinct := map[string]bool{}
		for _, r := range replicas {
			distinct[r] = true
		}
		expect(len(distinct) == 3, "replicas must be distinct nodes, got %q for %s", replicas, key)
		owner, _ := ring.GetNode(key)
		expect(replicas[0] == owner, "the first replica must be the owner %s, got %q", owner, replicas)
	}

	all := slices.Sorted(slices.Values(ring.GetNodes("key-1", 10)))
	expect(slices.Equal(all, []string{"a", "b", "c", "d", "e"}), "asking for more replicas than nodes returns every node")

	key := "key-42"
	pair := ring.GetNodes(key, 2)
	primary, second := pair[0], pair[1]
	ring.RemoveNode(primary)
	owner, _ := ring.GetNode(key)
	expect(owner == second, "when the owner %s leaves, the next replica %s should take over, got %s", primary, second, owner)
}
