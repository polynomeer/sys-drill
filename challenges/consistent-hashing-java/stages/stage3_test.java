// Stage 3 — virtual nodes.
// 학습 포인트: 노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다.
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
        HashRing ring = new HashRing(200);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            String node = "node-" + i;
            ring.addNode(node);
            counts.put(node, 0);
        }
        int total = 20000;
        for (int i = 0; i < total; i++) {
            String owner = ring.getNode("key-" + i);
            counts.put(owner, counts.get(owner) + 1);
        }
        double lo = Collections.min(counts.values()) / (double) total;
        double hi = Collections.max(counts.values()) / (double) total;
        check(lo >= 0.09 && hi <= 0.16, String.format(Locale.ROOT,
                "with 200 virtual nodes each of 8 nodes should own 9%%-16%% of the keys (ideal 12.5%%), got %.1f%% to %.1f%%",
                lo * 100, hi * 100));
    }
}
