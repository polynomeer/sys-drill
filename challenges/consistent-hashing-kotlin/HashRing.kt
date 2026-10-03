/*
 * SysDrill Build Mode — Build your own Consistent Hashing (Kotlin)
 *
 * Implement `HashRing` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.security.MessageDigest

/**
 * Provided — a well-mixed 32-bit hash (the first 4 bytes of MD5), as an
 * unsigned value in [0, 2^32).
 *
 * Use this for every position on the ring, both keys and nodes. Don't
 * use String.hashCode(): it clusters similar strings like "node-1#7" and
 * "node-1#8" right next to each other.
 */
fun ringHash(value: String): Long {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (digest[i].toLong() and 0xFF) }
}

/**
 * Maps keys (e.g. cache keys) to nodes (e.g. cache servers) so that
 * adding or removing a node only moves the keys that have to move —
 * unlike `hash(key) % nodes.size`, which reshuffles almost everything and
 * turns one scale-out into a cache-wide miss storm.
 */
class HashRing(private val virtualNodes: Int = 100) {
    // TODO(stage 1): set up whatever storage you need.
    // TODO(stage 3): each node should occupy `virtualNodes` positions on
    // the ring (hash "node#0", "node#1", ...), not just one.

    fun addNode(node: String) {
        // TODO(stage 1): place `node` on the ring.
        TODO("not implemented")
    }

    fun removeNode(node: String) {
        // TODO(stage 2): take `node` (all of its positions) off the ring.
        TODO("not implemented")
    }

    fun getNode(key: String): String? {
        // TODO(stage 1): the node owning `key` — the first node position at
        // or clockwise after ringHash(key), wrapping around past the top.
        // null when the ring is empty.
        TODO("not implemented")
    }

    fun getNodes(key: String, n: Int): List<String> {
        // TODO(stage 4): `n` DISTINCT nodes for replicating `key`: keep
        // walking clockwise from the key, skipping positions of nodes you
        // already picked. The first one is getNode(key). If there are fewer
        // than `n` nodes, return all of them.
        TODO("not implemented")
    }
}
