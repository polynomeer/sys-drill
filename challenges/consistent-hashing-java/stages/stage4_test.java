// Stage 4 — replicas.
// 학습 포인트: 복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 자연스럽게 주인이 된다.
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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
        check(ring.getNodes("k", 2).isEmpty(), "an empty ring has no replicas");
        for (String node : List.of("a", "b", "c", "d", "e")) ring.addNode(node);

        for (int i = 0; i < 200; i++) {
            String key = "key-" + i;
            List<String> replicas = ring.getNodes(key, 3);
            check(replicas.size() == 3, "expected 3 replicas for " + key + ", got " + replicas);
            check(new HashSet<>(replicas).size() == 3, "replicas must be distinct nodes, got " + replicas + " for " + key);
            check(Objects.equals(replicas.get(0), ring.getNode(key)),
                    "the first replica must be the owner " + ring.getNode(key) + ", got " + replicas);
        }

        List<String> all = new ArrayList<>(ring.getNodes("key-1", 10));
        all.sort(null);
        check(all.equals(List.of("a", "b", "c", "d", "e")), "asking for more replicas than nodes returns every node");

        String key = "key-42";
        List<String> pair = ring.getNodes(key, 2);
        String primary = pair.get(0), second = pair.get(1);
        ring.removeNode(primary);
        check(Objects.equals(ring.getNode(key), second),
                "when the owner " + primary + " leaves, the next replica " + second + " should take over, got " + ring.getNode(key));
    }
}
