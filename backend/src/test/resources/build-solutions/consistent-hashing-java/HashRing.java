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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

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

    private final int virtualNodes;
    private final TreeMap<Long, String> ring = new TreeMap<>(); // position -> node

    public HashRing() {
        this(100);
    }

    public HashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public void addNode(String node) {
        for (int i = 0; i < virtualNodes; i++) {
            // On a (rare) collision the first node keeps the spot.
            ring.putIfAbsent(ringHash(node + "#" + i), node);
        }
    }

    public void removeNode(String node) {
        ring.values().removeIf(node::equals);
    }

    public String getNode(String key) {
        List<String> nodes = getNodes(key, 1);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    public List<String> getNodes(String key, int n) {
        long point = ringHash(key);
        List<String> picked = new ArrayList<>();
        // Clockwise from the key to the top of the ring, then wrap around to the bottom.
        for (Collection<String> arc : List.of(ring.tailMap(point, true).values(), ring.headMap(point, false).values())) {
            for (String node : arc) {
                if (picked.size() == n) return picked;
                if (!picked.contains(node)) picked.add(node);
            }
        }
        return picked;
    }
}
