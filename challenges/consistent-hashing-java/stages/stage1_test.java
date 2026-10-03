// Stage 1 — ring lookup.
// 학습 포인트: 키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다.
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        HashRing ring = new HashRing();
        check(ring.getNode("user-1") == null, "an empty ring has no owner for any key");

        ring.addNode("cache-a");
        Set<String> owners = new TreeSet<>();
        for (int i = 0; i < 100; i++) owners.add(String.valueOf(ring.getNode("key-" + i)));
        check(owners.equals(Set.of("cache-a")), "with a single node, it should own every key, got " + owners);

        ring.addNode("cache-b");
        ring.addNode("cache-c");
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            String owner = ring.getNode(key);
            check(List.of("cache-a", "cache-b", "cache-c").contains(owner), key + " mapped to unknown node " + owner);
            check(Objects.equals(ring.getNode(key), owner), key + " must map to the same node every time");
        }
        Set<String> spread = new TreeSet<>();
        for (int i = 0; i < 1000; i++) spread.add(ring.getNode("key-" + i));
        check(spread.equals(Set.of("cache-a", "cache-b", "cache-c")), "1000 keys should land on all 3 nodes, got " + spread);
    }
}
