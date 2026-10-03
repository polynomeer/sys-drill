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
	// TODO(stage 1): store config and set up whatever storage you need.
}

func NewHashRing(virtualNodes int) *HashRing {
	// TODO(stage 1): store config and set up whatever storage you need.
	// TODO(stage 3): each node should occupy virtualNodes positions on the
	// ring (hash "node#0", "node#1", ...), not just one.
	panic("not implemented")
}

// AddNode places node on the ring.
func (r *HashRing) AddNode(node string) {
	// TODO(stage 1): place node on the ring.
	panic("not implemented")
}

// RemoveNode takes node (all of its positions) off the ring.
func (r *HashRing) RemoveNode(node string) {
	// TODO(stage 2): take node (all of its positions) off the ring.
	panic("not implemented")
}

// GetNode returns the node owning key, or ok == false if the ring is empty.
func (r *HashRing) GetNode(key string) (node string, ok bool) {
	// TODO(stage 1): the node owning key — the first node position at or
	// clockwise after ringHash(key), wrapping around past the top.
	// ("", false) when the ring is empty.
	panic("not implemented")
}

// GetNodes returns n distinct nodes for replicating key.
func (r *HashRing) GetNodes(key string, n int) []string {
	// TODO(stage 4): n DISTINCT nodes for replicating key: keep walking
	// clockwise from the key, skipping positions of nodes you already
	// picked. The first one is GetNode(key). If there are fewer than n
	// nodes, return all of them.
	panic("not implemented")
}
