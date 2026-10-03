// Stage 2 — minimal remapping.
// 학습 포인트: hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static final List<String> KEYS = new ArrayList<>();
    static {
        for (int i = 0; i < 10000; i++) KEYS.add("key-" + i);
    }

    static Map<String, String> snapshot(HashRing ring) {
        Map<String, String> owners = new HashMap<>();
        for (String k : KEYS) owners.put(k, ring.getNode(k));
        return owners;
    }

    static void run() {
        HashRing ring = new HashRing();
        for (String node : List.of("a", "b", "c")) ring.addNode(node);
        Map<String, String> before = snapshot(ring);

        ring.addNode("d");
        Map<String, String> after = snapshot(ring);
        List<String> moved = new ArrayList<>();
        for (String k : KEYS) if (!Objects.equals(before.get(k), after.get(k))) moved.add(k);
        List<String> wrong = new ArrayList<>();
        for (String k : moved) if (!"d".equals(after.get(k))) wrong.add(k);
        if (!wrong.isEmpty()) {
            String w = wrong.get(0);
            check(false, "adding d must only move keys TO d, but " + wrong.size() + " keys moved between old nodes (e.g. "
                    + w + ": " + before.get(w) + " -> " + after.get(w) + ")");
        }
        // How MANY keys move depends on how big d's slice of the ring is (that's stage 3's business);
        // what consistent hashing guarantees is WHERE they move.
        check(!moved.isEmpty(), "the new node d should take over some keys");

        ring.removeNode("b");
        Map<String, String> fin = snapshot(ring);
        int disturbed = 0;
        for (String k : KEYS) if (!"b".equals(after.get(k)) && !Objects.equals(fin.get(k), after.get(k))) disturbed++;
        check(disturbed == 0, "removing b must only move b's keys, but " + disturbed + " other keys moved");
        check(!fin.containsValue("b"), "no key should map to a removed node");
    }
}
