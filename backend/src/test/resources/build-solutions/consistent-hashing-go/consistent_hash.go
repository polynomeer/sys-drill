// SysDrill Build Mode — Build your own Consistent Hashing (Go)
//
// Implement HashRing below across 4 stages (see README.md).
// Keep the type, function and method names as-is — the stage tests call
// them directly. Submit by running ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"crypto/md5"
	"encoding/binary"
	"fmt"
	"maps"
	"slices"
)

// ringHash is provided — a well-mixed 32-bit hash (the first 4 bytes of MD5).
//
// Use this for every position on the ring, both keys and nodes. Don't use
// hash/maphash (randomly seeded per process) or a quick Java-style h*31+c
// string hash, which clusters similar strings like "node-1#7" and
// "node-1#8" right next to each other.
func ringHash(value string) uint32 {
	sum := md5.Sum([]byte(value))
	return binary.BigEndian.Uint32(sum[:4])
}

// HashRing maps keys (e.g. cache keys) to nodes (e.g. cache servers) so
// that adding or removing a node only moves the keys that have to move —
// unlike hash(key) % len(nodes), which reshuffles almost everything and
// turns one scale-out into a cache-wide miss storm.
type HashRing struct {
	virtualNodes int
	points       []uint32          // sorted ring positions
	owner        map[uint32]string // position -> node
}

func NewHashRing(virtualNodes int) *HashRing {
	return &HashRing{virtualNodes: virtualNodes, owner: map[uint32]string{}}
}

// AddNode places node on the ring.
func (r *HashRing) AddNode(node string) {
	for i := 0; i < r.virtualNodes; i++ {
		point := ringHash(fmt.Sprintf("%s#%d", node, i))
		if _, taken := r.owner[point]; taken {
			continue // on a (rare) collision the first node keeps the spot
		}
		r.owner[point] = node
		at, _ := slices.BinarySearch(r.points, point)
		r.points = slices.Insert(r.points, at, point)
	}
}

// RemoveNode takes node (all of its positions) off the ring.
func (r *HashRing) RemoveNode(node string) {
	r.points = slices.DeleteFunc(r.points, func(p uint32) bool { return r.owner[p] == node })
	maps.DeleteFunc(r.owner, func(_ uint32, n string) bool { return n == node })
}

// GetNode returns the node owning key, or ok == false if the ring is empty.
func (r *HashRing) GetNode(key string) (node string, ok bool) {
	nodes := r.GetNodes(key, 1)
	if len(nodes) == 0 {
		return "", false
	}
	return nodes[0], true
}

// GetNodes returns n distinct nodes for replicating key.
func (r *HashRing) GetNodes(key string, n int) []string {
	var picked []string
	start, _ := slices.BinarySearch(r.points, ringHash(key))
	for step := 0; step < len(r.points) && len(picked) < n; step++ {
		node := r.owner[r.points[(start+step)%len(r.points)]]
		if !slices.Contains(picked, node) {
			picked = append(picked, node)
		}
	}
	return picked
}
