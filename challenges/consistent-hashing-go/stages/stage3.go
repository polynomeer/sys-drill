// Stage 3 — virtual nodes.
// 학습 포인트: 노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다.
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

func stage() {
	ring := NewHashRing(200)
	counts := map[string]int{}
	for i := 0; i < 8; i++ {
		node := fmt.Sprintf("node-%d", i)
		ring.AddNode(node)
		counts[node] = 0
	}
	total := 20000
	for i := 0; i < total; i++ {
		owner, _ := ring.GetNode(fmt.Sprintf("key-%d", i))
		if _, known := counts[owner]; !known {
			panic(fmt.Sprintf("key-%d mapped to unknown node %q", i, owner))
		}
		counts[owner]++
	}
	lo, hi := 1.0, 0.0
	for _, c := range counts {
		share := float64(c) / float64(total)
		lo, hi = min(lo, share), max(hi, share)
	}
	expect(lo >= 0.09 && hi <= 0.16,
		"with 200 virtual nodes each of 8 nodes should own 9%%-16%% of the keys (ideal 12.5%%), got %.1f%% to %.1f%%", lo*100, hi*100)
}
