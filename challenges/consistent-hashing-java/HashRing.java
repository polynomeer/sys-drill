/*
 * SysDrill Build Mode — Build your own Consistent Hashing (Java)
 *
 * Implement `HashRing` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Maps keys (e.g. cache keys) to nodes (e.g. cache servers) so that
 * adding or removing a node only moves the keys that have to move —
 * unlike `hash(key) % nodes.size()`, which reshuffles almost everything
 * and turns one scale-out into a cache-wide miss storm.
 */
public class HashRing {
    /**
     * Provided — a well-mixed 32-bit hash (the first 4 bytes of MD5), as an
     * unsigned value in [0, 2^32).
     *
     * Use this for every position on the ring, both keys and nodes. Don't
     * use String.hashCode(): it clusters similar strings like "node-1#7"
     * and "node-1#8" right next to each other.
     */
    static long ringHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFFL) << 24) | ((digest[1] & 0xFFL) << 16) | ((digest[2] & 0xFFL) << 8) | (digest[3] & 0xFFL);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public HashRing() {
        this(100);
    }

    public HashRing(int virtualNodes) {
        // TODO(stage 1): store config and set up whatever storage you need.
        // TODO(stage 3): each node should occupy `virtualNodes` positions on
        // the ring (hash "node#0", "node#1", ...), not just one.
        throw new UnsupportedOperationException("not implemented");
    }

    public void addNode(String node) {
        // TODO(stage 1): place `node` on the ring.
        throw new UnsupportedOperationException("not implemented");
    }

    public void removeNode(String node) {
        // TODO(stage 2): take `node` (all of its positions) off the ring.
        throw new UnsupportedOperationException("not implemented");
    }

    public String getNode(String key) {
        // TODO(stage 1): the node owning `key` — the first node position at
        // or clockwise after ringHash(key), wrapping around past the top.
        // null when the ring is empty.
        throw new UnsupportedOperationException("not implemented");
    }

    public List<String> getNodes(String key, int n) {
        // TODO(stage 4): `n` DISTINCT nodes for replicating `key`: keep
        // walking clockwise from the key, skipping positions of nodes you
        // already picked. The first one is getNode(key). If there are fewer
        // than `n` nodes, return all of them.
        throw new UnsupportedOperationException("not implemented");
    }
}
