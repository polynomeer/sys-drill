-- Build 과제 두 개를 더 연다 — Build your own Consistent Hashing(→ 상품 조회 Drill, Hot Key 분산
-- 개념)과 Build your own Transactional Outbox(→ 결제 Drill의 outbox backlog·dispatcher, 트랜잭션 경계
-- 분리 개념). V75처럼 Python·Java·Kotlin·Go 네 판을 함께 연다. 스텁과 스테이지 테스트는
-- challenges/<slug>/ 의 파일 그대로다(이 파일은 그 파일들에서 생성했다 — BuildStarterCodeTest가 스텁
-- 일치를, BuildLanguageVariantsIntegrationTest가 "스텁은 전부 실패, 모범 답안은 전부 통과"를 고정한다).
--
-- Consistent Hashing은 대응하는 워게임 액션이 없다(엔진의 hot key는 캐시 정책 분리로만 다루고, 노드 간
-- 키 분산은 모델링하지 않는다) —
-- 그래서 Drill 연결은 도메인(상품 조회)과 학습 개념으로만 한다.

-- consistent-hashing
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a6000000-0000-0000-0000-000000000001',
    'consistent-hashing',
    'Build your own Consistent Hashing',
    'python',
    'consistent_hash.py',
    $stub$"""
SysDrill Build Mode — Build your own Consistent Hashing

Implement the HashRing class below across 4 stages (see README.md). Keep
the class and method names as-is — the stage tests import this module
directly. Submit by running ./submit.sh once you're ready.
"""
import hashlib


def ring_hash(value: str) -> int:
    """Provided — a well-mixed 32-bit hash (the first 4 bytes of MD5).

    Use this for every position on the ring, both keys and nodes. Don't
    use Python's built-in hash(): it's randomized per process and clusters
    similar strings like "node-1#7" and "node-1#8".
    """
    return int.from_bytes(hashlib.md5(value.encode("utf-8")).digest()[:4], "big")


class HashRing:
    """Maps keys (e.g. cache keys) to nodes (e.g. cache servers) so that
    adding or removing a node only moves the keys that have to move —
    unlike `hash(key) % len(nodes)`, which reshuffles almost everything and
    turns one scale-out into a cache-wide miss storm.
    """

    def __init__(self, virtual_nodes: int = 100):
        # TODO(stage 1): store config and set up whatever storage you need.
        # TODO(stage 3): each node should occupy `virtual_nodes` positions on
        # the ring (hash "node#0", "node#1", ...), not just one.
        raise NotImplementedError

    def add_node(self, node: str) -> None:
        # TODO(stage 1): place `node` on the ring.
        raise NotImplementedError

    def remove_node(self, node: str) -> None:
        # TODO(stage 2): take `node` (all of its positions) off the ring.
        raise NotImplementedError

    def get_node(self, key: str) -> str | None:
        # TODO(stage 1): the node owning `key` — the first node position at
        # or clockwise after ring_hash(key), wrapping around past the top.
        # None when the ring is empty.
        raise NotImplementedError

    def get_nodes(self, key: str, n: int) -> list[str]:
        # TODO(stage 4): `n` DISTINCT nodes for replicating `key`: keep
        # walking clockwise from the key, skipping positions of nodes you
        # already picked. The first one is get_node(key). If there are fewer
        # than `n` nodes, return all of them.
        raise NotImplementedError
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a6000000-0000-0000-0000-000000000001',
    1,
    'ring lookup',
    '키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다',
    $stage$"""Stage 1 — ring lookup.
학습 포인트: 키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다.
"""
from consistent_hash import HashRing


def main():
    ring = HashRing()
    assert ring.get_node("user-1") is None, "an empty ring has no owner for any key"

    ring.add_node("cache-a")
    owners = {ring.get_node(f"key-{i}") for i in range(100)}
    assert owners == {"cache-a"}, f"with a single node, it should own every key, got {owners}"

    ring.add_node("cache-b")
    ring.add_node("cache-c")
    for i in range(1000):
        key = f"key-{i}"
        owner = ring.get_node(key)
        assert owner in ("cache-a", "cache-b", "cache-c"), f"{key} mapped to unknown node {owner}"
        assert ring.get_node(key) == owner, f"{key} must map to the same node every time"
    spread = {ring.get_node(f"key-{i}") for i in range(1000)}
    assert spread == {"cache-a", "cache-b", "cache-c"}, f"1000 keys should land on all 3 nodes, got {spread}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a6000000-0000-0000-0000-000000000001',
    2,
    'minimal remapping',
    'hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다',
    $stage$"""Stage 2 — minimal remapping.
학습 포인트: hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다.
"""
from consistent_hash import HashRing

KEYS = [f"key-{i}" for i in range(10000)]


def main():
    ring = HashRing()
    for node in ("a", "b", "c"):
        ring.add_node(node)
    before = {k: ring.get_node(k) for k in KEYS}

    ring.add_node("d")
    after = {k: ring.get_node(k) for k in KEYS}
    moved = [k for k in KEYS if before[k] != after[k]]
    wrong = [k for k in moved if after[k] != "d"]
    assert not wrong, f"adding d must only move keys TO d, but {len(wrong)} keys moved between old nodes (e.g. {wrong[0]}: {before[wrong[0]]} -> {after[wrong[0]]})"
    # How MANY keys move depends on how big d's slice of the ring is (that's stage 3's business);
    # what consistent hashing guarantees is WHERE they move.
    assert moved, "the new node d should take over some keys"

    ring.remove_node("b")
    final = {k: ring.get_node(k) for k in KEYS}
    disturbed = [k for k in KEYS if after[k] != "b" and final[k] != after[k]]
    assert not disturbed, f"removing b must only move b's keys, but {len(disturbed)} other keys moved"
    assert "b" not in set(final.values()), "no key should map to a removed node"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a6000000-0000-0000-0000-000000000001',
    3,
    'virtual nodes',
    '노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다',
    $stage$"""Stage 3 — virtual nodes.
학습 포인트: 노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다.
"""
from consistent_hash import HashRing


def main():
    ring = HashRing(virtual_nodes=200)
    nodes = [f"node-{i}" for i in range(8)]
    for node in nodes:
        ring.add_node(node)
    counts = {node: 0 for node in nodes}
    total = 20000
    for i in range(total):
        counts[ring.get_node(f"key-{i}")] += 1
    shares = {node: c / total for node, c in counts.items()}
    lo, hi = min(shares.values()), max(shares.values())
    assert lo >= 0.09 and hi <= 0.16, (
        f"with 200 virtual nodes each of 8 nodes should own 9%-16% of the keys (ideal 12.5%), got {lo:.1%} to {hi:.1%}"
    )


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a6000000-0000-0000-0000-000000000001',
    4,
    'replicas',
    '복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 주인이 된다',
    $stage$"""Stage 4 — replicas.
학습 포인트: 복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 자연스럽게 주인이 된다.
"""
from consistent_hash import HashRing


def main():
    ring = HashRing()
    assert ring.get_nodes("k", 2) == [], "an empty ring has no replicas"
    for node in ("a", "b", "c", "d", "e"):
        ring.add_node(node)

    for i in range(200):
        key = f"key-{i}"
        replicas = ring.get_nodes(key, 3)
        assert len(replicas) == 3, f"expected 3 replicas for {key}, got {replicas}"
        assert len(set(replicas)) == 3, f"replicas must be distinct nodes, got {replicas} for {key}"
        assert replicas[0] == ring.get_node(key), f"the first replica must be the owner {ring.get_node(key)}, got {replicas}"

    assert sorted(ring.get_nodes("key-1", 10)) == ["a", "b", "c", "d", "e"], "asking for more replicas than nodes returns every node"

    key = "key-42"
    primary, second = ring.get_nodes(key, 2)
    ring.remove_node(primary)
    assert ring.get_node(key) == second, f"when the owner {primary} leaves, the next replica {second} should take over, got {ring.get_node(key)}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
);

-- consistent-hashing-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a6000000-0000-0000-0000-000000000002',
    'consistent-hashing-java',
    'Build your own Consistent Hashing (Java)',
    'java',
    'HashRing.java',
    $stub$/*
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
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a6000000-0000-0000-0000-000000000002',
    1,
    'ring lookup',
    '키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다',
    $stage$// Stage 1 — ring lookup.
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
$stage$
),
(
    'a6000000-0000-0000-0000-000000000002',
    2,
    'minimal remapping',
    'hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다',
    $stage$// Stage 2 — minimal remapping.
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
$stage$
),
(
    'a6000000-0000-0000-0000-000000000002',
    3,
    'virtual nodes',
    '노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다',
    $stage$// Stage 3 — virtual nodes.
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
$stage$
),
(
    'a6000000-0000-0000-0000-000000000002',
    4,
    'replicas',
    '복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 주인이 된다',
    $stage$// Stage 4 — replicas.
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
$stage$
);

-- consistent-hashing-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a6000000-0000-0000-0000-000000000003',
    'consistent-hashing-kotlin',
    'Build your own Consistent Hashing (Kotlin)',
    'kotlin',
    'HashRing.kt',
    $stub$/*
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
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a6000000-0000-0000-0000-000000000003',
    1,
    'ring lookup',
    '키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — ring lookup.
// 학습 포인트: 키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val ring = HashRing()
    expect(ring.getNode("user-1") == null, "an empty ring has no owner for any key")

    ring.addNode("cache-a")
    val owners = (0 until 100).map { ring.getNode("key-$it") }.toSet()
    expect(owners == setOf("cache-a"), "with a single node, it should own every key, got $owners")

    ring.addNode("cache-b")
    ring.addNode("cache-c")
    for (i in 0 until 1000) {
        val key = "key-$i"
        val owner = ring.getNode(key)
        expect(owner in listOf("cache-a", "cache-b", "cache-c"), "$key mapped to unknown node $owner")
        expect(ring.getNode(key) == owner, "$key must map to the same node every time")
    }
    val spread = (0 until 1000).map { ring.getNode("key-$it") }.toSet()
    expect(spread == setOf("cache-a", "cache-b", "cache-c"), "1000 keys should land on all 3 nodes, got $spread")
}
$stage$
),
(
    'a6000000-0000-0000-0000-000000000003',
    2,
    'minimal remapping',
    'hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — minimal remapping.
// 학습 포인트: hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

val KEYS = (0 until 10000).map { "key-$it" }

fun stage() {
    val ring = HashRing()
    for (node in listOf("a", "b", "c")) ring.addNode(node)
    val before = KEYS.associateWith { ring.getNode(it) }

    ring.addNode("d")
    val after = KEYS.associateWith { ring.getNode(it) }
    val moved = KEYS.filter { before[it] != after[it] }
    val wrong = moved.filter { after[it] != "d" }
    if (wrong.isNotEmpty()) {
        val w = wrong.first()
        expect(false, "adding d must only move keys TO d, but ${wrong.size} keys moved between old nodes (e.g. $w: ${before[w]} -> ${after[w]})")
    }
    // How MANY keys move depends on how big d's slice of the ring is (that's stage 3's business);
    // what consistent hashing guarantees is WHERE they move.
    expect(moved.isNotEmpty(), "the new node d should take over some keys")

    ring.removeNode("b")
    val final = KEYS.associateWith { ring.getNode(it) }
    val disturbed = KEYS.filter { after[it] != "b" && final[it] != after[it] }
    expect(disturbed.isEmpty(), "removing b must only move b's keys, but ${disturbed.size} other keys moved")
    expect("b" !in final.values.toSet(), "no key should map to a removed node")
}
$stage$
),
(
    'a6000000-0000-0000-0000-000000000003',
    3,
    'virtual nodes',
    '노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다',
    $stage$@file:JvmName("RunTest")

// Stage 3 — virtual nodes.
// 학습 포인트: 노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val ring = HashRing(virtualNodes = 200)
    val nodes = (0 until 8).map { "node-$it" }
    for (node in nodes) ring.addNode(node)
    val counts = nodes.associateWith { 0 }.toMutableMap()
    val total = 20000
    for (i in 0 until total) {
        val owner = ring.getNode("key-$i")
        counts[owner!!] = counts.getValue(owner) + 1
    }
    val shares = counts.values.map { it.toDouble() / total }
    val lo = shares.min()
    val hi = shares.max()
    expect(
        lo >= 0.09 && hi <= 0.16,
        "with 200 virtual nodes each of 8 nodes should own 9%-16% of the keys (ideal 12.5%), got " +
            "%.1f%% to %.1f%%".format(java.util.Locale.ROOT, lo * 100, hi * 100),
    )
}
$stage$
),
(
    'a6000000-0000-0000-0000-000000000003',
    4,
    'replicas',
    '복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 주인이 된다',
    $stage$@file:JvmName("RunTest")

// Stage 4 — replicas.
// 학습 포인트: 복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 자연스럽게 주인이 된다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val ring = HashRing()
    expect(ring.getNodes("k", 2) == emptyList<String>(), "an empty ring has no replicas")
    for (node in listOf("a", "b", "c", "d", "e")) ring.addNode(node)

    for (i in 0 until 200) {
        val key = "key-$i"
        val replicas = ring.getNodes(key, 3)
        expect(replicas.size == 3, "expected 3 replicas for $key, got $replicas")
        expect(replicas.toSet().size == 3, "replicas must be distinct nodes, got $replicas for $key")
        expect(replicas[0] == ring.getNode(key), "the first replica must be the owner ${ring.getNode(key)}, got $replicas")
    }

    expect(ring.getNodes("key-1", 10).sorted() == listOf("a", "b", "c", "d", "e"), "asking for more replicas than nodes returns every node")

    val key = "key-42"
    val (primary, second) = ring.getNodes(key, 2)
    ring.removeNode(primary)
    expect(ring.getNode(key) == second, "when the owner $primary leaves, the next replica $second should take over, got ${ring.getNode(key)}")
}
$stage$
);

-- consistent-hashing-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a6000000-0000-0000-0000-000000000004',
    'consistent-hashing-go',
    'Build your own Consistent Hashing (Go)',
    'go',
    'consistent_hash.go',
    $stub$// SysDrill Build Mode — Build your own Consistent Hashing (Go)
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
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a6000000-0000-0000-0000-000000000004',
    1,
    'ring lookup',
    '키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다',
    $stage$// Stage 1 — ring lookup.
// 학습 포인트: 키와 노드를 같은 해시 공간(링)에 놓고, 키에서 시계 방향으로 처음 만나는 노드가 주인이다.
package main

import (
	"fmt"
	"maps"
	"os"
	"slices"
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

// ownersOf returns the sorted set of nodes owning keys key-0 .. key-(count-1).
func ownersOf(ring *HashRing, count int) []string {
	owners := map[string]bool{}
	for i := 0; i < count; i++ {
		owner, _ := ring.GetNode(fmt.Sprintf("key-%d", i))
		owners[owner] = true
	}
	return slices.Sorted(maps.Keys(owners))
}

func stage() {
	ring := NewHashRing(100)
	_, ok := ring.GetNode("user-1")
	expect(!ok, "an empty ring has no owner for any key")

	ring.AddNode("cache-a")
	owners := ownersOf(ring, 100)
	expect(slices.Equal(owners, []string{"cache-a"}), "with a single node, it should own every key, got %q", owners)

	ring.AddNode("cache-b")
	ring.AddNode("cache-c")
	for i := 0; i < 1000; i++ {
		key := fmt.Sprintf("key-%d", i)
		owner, _ := ring.GetNode(key)
		expect(slices.Contains([]string{"cache-a", "cache-b", "cache-c"}, owner), "%s mapped to unknown node %q", key, owner)
		again, _ := ring.GetNode(key)
		expect(again == owner, "%s must map to the same node every time", key)
	}
	spread := ownersOf(ring, 1000)
	expect(slices.Equal(spread, []string{"cache-a", "cache-b", "cache-c"}), "1000 keys should land on all 3 nodes, got %q", spread)
}
$stage$
),
(
    'a6000000-0000-0000-0000-000000000004',
    2,
    'minimal remapping',
    'hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다',
    $stage$// Stage 2 — minimal remapping.
// 학습 포인트: hash % N은 노드 하나만 늘어도 거의 모든 키가 옮겨 간다 — 링에서는 새 노드가 맡는 몫만 옮겨 간다.
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

var keys = func() []string {
	ks := make([]string, 10000)
	for i := range ks {
		ks[i] = fmt.Sprintf("key-%d", i)
	}
	return ks
}()

func snapshot(ring *HashRing) map[string]string {
	owners := make(map[string]string, len(keys))
	for _, k := range keys {
		owners[k], _ = ring.GetNode(k)
	}
	return owners
}

func stage() {
	ring := NewHashRing(100)
	for _, node := range []string{"a", "b", "c"} {
		ring.AddNode(node)
	}
	before := snapshot(ring)

	ring.AddNode("d")
	after := snapshot(ring)
	var moved, wrong []string
	for _, k := range keys {
		if before[k] != after[k] {
			moved = append(moved, k)
			if after[k] != "d" {
				wrong = append(wrong, k)
			}
		}
	}
	if len(wrong) > 0 {
		w := wrong[0]
		expect(false, "adding d must only move keys TO d, but %d keys moved between old nodes (e.g. %s: %s -> %s)", len(wrong), w, before[w], after[w])
	}
	// How MANY keys move depends on how big d's slice of the ring is (that's stage 3's business);
	// what consistent hashing guarantees is WHERE they move.
	expect(len(moved) > 0, "the new node d should take over some keys")

	ring.RemoveNode("b")
	final := snapshot(ring)
	disturbed := 0
	for _, k := range keys {
		if after[k] != "b" && final[k] != after[k] {
			disturbed++
		}
	}
	expect(disturbed == 0, "removing b must only move b's keys, but %d other keys moved", disturbed)
	for _, owner := range final {
		expect(owner != "b", "no key should map to a removed node")
	}
}
$stage$
),
(
    'a6000000-0000-0000-0000-000000000004',
    3,
    'virtual nodes',
    '노드당 점 하나면 링 구간이 들쭉날쭉해 어떤 서버는 몇 배의 키를 받는다 — 가상 노드로 고르게 편다',
    $stage$// Stage 3 — virtual nodes.
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
$stage$
),
(
    'a6000000-0000-0000-0000-000000000004',
    4,
    'replicas',
    '복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 주인이 된다',
    $stage$// Stage 4 — replicas.
// 학습 포인트: 복제본은 시계 방향으로 이어지는 서로 다른 노드에 둔다 — 주 노드가 빠지면 다음 복제본이 자연스럽게 주인이 된다.
package main

import (
	"fmt"
	"os"
	"slices"
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
	ring := NewHashRing(100)
	expect(len(ring.GetNodes("k", 2)) == 0, "an empty ring has no replicas")
	for _, node := range []string{"a", "b", "c", "d", "e"} {
		ring.AddNode(node)
	}

	for i := 0; i < 200; i++ {
		key := fmt.Sprintf("key-%d", i)
		replicas := ring.GetNodes(key, 3)
		expect(len(replicas) == 3, "expected 3 replicas for %s, got %q", key, replicas)
		distinct := map[string]bool{}
		for _, r := range replicas {
			distinct[r] = true
		}
		expect(len(distinct) == 3, "replicas must be distinct nodes, got %q for %s", replicas, key)
		owner, _ := ring.GetNode(key)
		expect(replicas[0] == owner, "the first replica must be the owner %s, got %q", owner, replicas)
	}

	all := slices.Sorted(slices.Values(ring.GetNodes("key-1", 10)))
	expect(slices.Equal(all, []string{"a", "b", "c", "d", "e"}), "asking for more replicas than nodes returns every node")

	key := "key-42"
	pair := ring.GetNodes(key, 2)
	primary, second := pair[0], pair[1]
	ring.RemoveNode(primary)
	owner, _ := ring.GetNode(key)
	expect(owner == second, "when the owner %s leaves, the next replica %s should take over, got %s", primary, second, owner)
}
$stage$
);

-- outbox
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a7000000-0000-0000-0000-000000000001',
    'outbox',
    'Build your own Transactional Outbox',
    'python',
    'outbox.py',
    $stub$"""
SysDrill Build Mode — Build your own Transactional Outbox

Implement OrderService, OutboxRelay and InventoryConsumer below across 4
stages (see README.md). Database and Broker are provided and complete —
don't change them; the stage tests drive their failure hooks. Keep the
class and method names as-is. Submit by running ./submit.sh once you're ready.
"""


class DatabaseError(Exception):
    pass


class BrokerError(Exception):
    pass


class Database:
    """Provided — a tiny in-memory database with all-or-nothing transactions.

    Tables: `orders` (order_id -> amount) and the outbox (a list of event
    rows, oldest first). Changes made through a Transaction only become
    visible on commit(); a failed commit applies nothing.
    """

    def __init__(self):
        self.orders = {}
        self.outbox = []  # [{"id": "evt-1", "type": ..., "payload": {...}, "published": False}]
        self.commits = 0
        self._next_event = 1
        self._fail_next_commit = False
        self._fail_next_mark = False

    def begin(self) -> "Transaction":
        return Transaction(self)

    def pending_events(self, limit: int) -> list:
        """Committed outbox events not yet marked published, oldest first (copies)."""
        return [dict(e) for e in self.outbox if not e["published"]][:limit]

    def mark_published(self, event_id: str) -> None:
        if self._fail_next_mark:
            self._fail_next_mark = False
            raise DatabaseError("connection lost while marking the event published")
        for e in self.outbox:
            if e["id"] == event_id:
                e["published"] = True
                return
        raise DatabaseError(f"no outbox event {event_id}")

    # test hooks
    def fail_next_commit(self) -> None:
        self._fail_next_commit = True

    def fail_next_mark_published(self) -> None:
        self._fail_next_mark = True


class Transaction:
    """Provided — stages writes and applies them all at once on commit()."""

    def __init__(self, db: Database):
        self._db = db
        self._orders = {}
        self._events = []
        self._done = False

    def insert_order(self, order_id: str, amount: int) -> None:
        self._orders[order_id] = amount

    def insert_event(self, event_type: str, payload: dict) -> str:
        """Stage an outbox event; returns the id it will have once committed."""
        event_id = f"evt-{self._db._next_event + len(self._events)}"
        self._events.append({"id": event_id, "type": event_type, "payload": dict(payload), "published": False})
        return event_id

    def commit(self) -> None:
        if self._done:
            raise DatabaseError("transaction already finished")
        self._done = True
        if self._db._fail_next_commit:
            self._db._fail_next_commit = False
            raise DatabaseError("commit failed")
        self._db.orders.update(self._orders)
        self._db.outbox.extend(self._events)
        self._db._next_event += len(self._events)
        self._db.commits += 1

    def rollback(self) -> None:
        self._done = True


class Broker:
    """Provided — a message broker (think Kafka) that consumers read from."""

    def __init__(self):
        self.published = []  # every event delivered, in order — duplicates included
        self._fail_next = False

    def publish(self, event: dict) -> None:
        if self._fail_next:
            self._fail_next = False
            raise BrokerError("broker unavailable")
        self.published.append(dict(event))

    # test hook
    def fail_next_publish(self) -> None:
        self._fail_next = True


class OrderService:
    def __init__(self, db: Database, broker: Broker):
        self.db = db
        self.broker = broker

    def place_order(self, order_id: str, amount: int) -> None:
        # TODO(stage 1): save the order AND an "OrderPlaced" outbox event
        # (payload {"order_id": ..., "amount": ...}) in ONE transaction, so
        # either both exist or neither does. Don't publish to the broker
        # here — that's the dual write the outbox exists to avoid (the order
        # commits, the publish fails, and nobody ever hears about the order).
        raise NotImplementedError


class OutboxRelay:
    def __init__(self, db: Database, broker: Broker):
        self.db = db
        self.broker = broker

    def run_once(self, batch_size: int = 100) -> int:
        # TODO(stage 2): publish pending outbox events to the broker oldest
        # first, marking each one published; return how many you published.
        # TODO(stage 3): if the broker fails, stop right there and return —
        # leave that event (and everything after it) pending for the next
        # run. Never skip ahead (order matters) and never mark an event
        # published before the broker has it (that loses it).
        raise NotImplementedError


class InventoryConsumer:
    """Consumes OrderPlaced events and reserves stock for them."""

    def __init__(self, stock: int):
        self.stock = stock

    def handle(self, event: dict) -> None:
        # TODO(stage 4): reserve stock for the order (event["payload"]
        # ["amount"] units). The relay is at-least-once — after a crash it
        # republishes events the broker already has — so the same event can
        # arrive twice: apply each event id only once.
        raise NotImplementedError
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a7000000-0000-0000-0000-000000000001',
    1,
    'one transaction',
    '주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다',
    $stage$"""Stage 1 — one transaction, no dual write.
학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
"""
from outbox import Broker, BrokerError, Database, DatabaseError, OrderService


def main():
    db, broker = Database(), Broker()
    service = OrderService(db, broker)

    broker.fail_next_publish()  # the broker being down must not matter when placing an order
    try:
        service.place_order("o-1", 3)
    except BrokerError:
        assert False, "place_order must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed"
    assert db.orders == {"o-1": 3}, f"the order should be saved, orders are {db.orders}"
    assert len(db.outbox) == 1, f"expected exactly 1 outbox event, got {db.outbox}"
    event = db.outbox[0]
    assert event["type"] == "OrderPlaced", f"expected an OrderPlaced event, got {event['type']}"
    assert event["payload"] == {"order_id": "o-1", "amount": 3}, f"unexpected payload {event['payload']}"
    assert db.commits == 1, f"the order and its event must be written in ONE transaction, saw {db.commits} commits"
    assert broker.published == [], "place_order must not publish to the broker itself (that's the dual write)"
    # With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
    healthy_db, healthy_broker = Database(), Broker()
    OrderService(healthy_db, healthy_broker).place_order("o-9", 1)
    assert healthy_broker.published == [], "place_order must not publish to the broker itself (that's the dual write)"

    db.fail_next_commit()
    try:
        service.place_order("o-2", 5)
        assert False, "a failed commit should surface to the caller as DatabaseError"
    except DatabaseError:
        pass
    assert "o-2" not in db.orders, "after a failed commit the order must not exist"
    assert all(e["payload"]["order_id"] != "o-2" for e in db.outbox), "after a failed commit its event must not exist either"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a7000000-0000-0000-0000-000000000001',
    2,
    'relay',
    '별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다',
    $stage$"""Stage 2 — the relay.
학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
"""
from outbox import Broker, Database, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    for i, amount in enumerate((3, 1, 4), start=1):
        service.place_order(f"o-{i}", amount)

    published = relay.run_once()
    assert published == 3, f"run_once should report 3 published events, got {published}"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-2", "evt-3"], f"events should be published oldest first, got {ids}"
    assert [e["payload"]["order_id"] for e in broker.published] == ["o-1", "o-2", "o-3"]
    assert db.pending_events(100) == [], "published events should be marked, nothing left pending"

    assert relay.run_once() == 0, "a second run with nothing pending should publish nothing"
    assert len(broker.published) == 3, "already-published events must not be sent again"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a7000000-0000-0000-0000-000000000001',
    3,
    'broker failure',
    '브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다',
    $stage$"""Stage 3 — broker failure.
학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
"""
from outbox import Broker, BrokerError, Database, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    service.place_order("o-1", 3)
    assert relay.run_once() == 1

    service.place_order("o-2", 1)
    service.place_order("o-3", 4)
    broker.fail_next_publish()  # evt-2 hits a broker hiccup
    try:
        published = relay.run_once()
    except BrokerError:
        assert False, "run_once should stop and return when the broker fails, not raise"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1"], f"the relay must stop at the failed event, not skip ahead — broker has {ids}"
    assert published == 0, f"nothing was published in that run, got {published}"
    pending = [e["id"] for e in db.pending_events(100)]
    assert pending == ["evt-2", "evt-3"], f"the failed event must stay pending (never mark before publishing), pending is {pending}"

    assert relay.run_once() == 2, "once the broker recovers, the next run should publish the rest"
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-2", "evt-3"], f"order must survive the failure, broker has {ids}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
),
(
    'a7000000-0000-0000-0000-000000000001',
    4,
    'idempotent consumer',
    '보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다',
    $stage$"""Stage 4 — at-least-once, idempotent consumer.
학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
"""
from outbox import Broker, Database, DatabaseError, InventoryConsumer, OrderService, OutboxRelay


def main():
    db, broker = Database(), Broker()
    service, relay = OrderService(db, broker), OutboxRelay(db, broker)
    service.place_order("o-1", 3)

    db.fail_next_mark_published()  # the relay crashes right after the broker took evt-1
    try:
        relay.run_once()
    except DatabaseError:
        pass
    relay.run_once()  # the restarted relay sends evt-1 again — it was never marked
    ids = [e["id"] for e in broker.published]
    assert ids == ["evt-1", "evt-1"], f"an event published but not marked should be sent again (at-least-once), broker has {ids}"

    consumer = InventoryConsumer(stock=10)
    for event in broker.published:
        consumer.handle(event)
    assert consumer.stock == 7, f"the duplicate evt-1 must reserve stock only once: expected 7 left, got {consumer.stock}"

    service.place_order("o-2", 2)
    relay.run_once()
    consumer.handle(broker.published[-1])
    assert consumer.stock == 5, f"a new event should still be applied: expected 5 left, got {consumer.stock}"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
$stage$
);

-- outbox-java
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a7000000-0000-0000-0000-000000000002',
    'outbox-java',
    'Build your own Transactional Outbox (Java)',
    'java',
    'Outbox.java',
    $stub$/*
 * SysDrill Build Mode — Build your own Transactional Outbox (Java)
 *
 * Implement OrderService, OutboxRelay and InventoryConsumer below across 4
 * stages (see README.md). Database, Transaction and Broker are provided and
 * complete — don't change them; the stage tests drive their failure hooks.
 * Keep the class and method names as-is. Submit by running ./submit.sh once
 * you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Names shared by the whole outbox (the file's public type). */
public final class Outbox {
    /** The event type placeOrder() writes to the outbox. */
    public static final String ORDER_PLACED = "OrderPlaced";

    private Outbox() {}
}

class DatabaseException extends RuntimeException {
    DatabaseException(String message) {
        super(message);
    }
}

class BrokerException extends RuntimeException {
    BrokerException(String message) {
        super(message);
    }
}

/**
 * An outbox event — what the relay hands to the broker and the broker hands
 * to consumers. Immutable; the payload is copied on the way in. Whether it's
 * been published is a column of the outbox row, kept inside Database (see
 * pendingEvents()), not part of the event itself.
 */
record Event(String id, String type, Map<String, Object> payload) {
    Event {
        payload = Map.copyOf(payload);
    }
}

/**
 * Provided — a tiny in-memory database with all-or-nothing transactions.
 *
 * Tables: orders (orderId -> amount) and the outbox (event rows with a
 * published flag, oldest first). Changes made through a Transaction only
 * become visible on commit(); a failed commit applies nothing.
 */
class Database {
    private static final class OutboxRow {
        final Event event;
        boolean published;

        OutboxRow(Event event) {
            this.event = event;
        }
    }

    private final Map<String, Integer> orders = new LinkedHashMap<>();
    private final List<OutboxRow> outbox = new ArrayList<>();
    private int commits = 0;
    private int nextEvent = 1;
    private boolean failNextCommit = false;
    private boolean failNextMark = false;

    public Transaction begin() {
        return new Transaction(this);
    }

    /** The orders table (a copy). */
    public Map<String, Integer> orders() {
        return new LinkedHashMap<>(orders);
    }

    /** Every committed outbox event, published or not, oldest first. */
    public List<Event> outbox() {
        List<Event> events = new ArrayList<>();
        for (OutboxRow row : outbox) events.add(row.event);
        return events;
    }

    /** How many transactions have committed. */
    public int commits() {
        return commits;
    }

    /** Committed outbox events not yet marked published, oldest first. */
    public List<Event> pendingEvents(int limit) {
        List<Event> pending = new ArrayList<>();
        for (OutboxRow row : outbox) {
            if (pending.size() >= limit) break;
            if (!row.published) pending.add(row.event);
        }
        return pending;
    }

    public void markPublished(String eventId) {
        if (failNextMark) {
            failNextMark = false;
            throw new DatabaseException("connection lost while marking the event published");
        }
        for (OutboxRow row : outbox) {
            if (row.event.id().equals(eventId)) {
                row.published = true;
                return;
            }
        }
        throw new DatabaseException("no outbox event " + eventId);
    }

    // test hooks
    public void failNextCommit() {
        failNextCommit = true;
    }

    public void failNextMarkPublished() {
        failNextMark = true;
    }

    // used by Transaction
    int nextEventNumber() {
        return nextEvent;
    }

    void apply(Map<String, Integer> newOrders, List<Event> newEvents) {
        if (failNextCommit) {
            failNextCommit = false;
            throw new DatabaseException("commit failed");
        }
        orders.putAll(newOrders);
        for (Event event : newEvents) outbox.add(new OutboxRow(event));
        nextEvent += newEvents.size();
        commits++;
    }
}

/** Provided — stages writes and applies them all at once on commit(). */
class Transaction {
    private final Database db;
    private final Map<String, Integer> orders = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private boolean done = false;

    Transaction(Database db) {
        this.db = db;
    }

    public void insertOrder(String orderId, int amount) {
        orders.put(orderId, amount);
    }

    /** Stage an outbox event; returns the id it will have once committed. */
    public String insertEvent(String eventType, Map<String, Object> payload) {
        String eventId = "evt-" + (db.nextEventNumber() + events.size());
        events.add(new Event(eventId, eventType, payload));
        return eventId;
    }

    public void commit() {
        if (done) throw new DatabaseException("transaction already finished");
        done = true;
        db.apply(orders, events);
    }

    public void rollback() {
        done = true;
    }
}

/** Provided — a message broker (think Kafka) that consumers read from. */
class Broker {
    private final List<Event> published = new ArrayList<>();
    private boolean failNext = false;

    /** Every event delivered, in order — duplicates included (a copy). */
    public List<Event> published() {
        return new ArrayList<>(published);
    }

    public void publish(Event event) {
        if (failNext) {
            failNext = false;
            throw new BrokerException("broker unavailable");
        }
        published.add(event);
    }

    // test hook
    public void failNextPublish() {
        failNext = true;
    }
}

class OrderService {
    private final Database db;
    private final Broker broker;

    OrderService(Database db, Broker broker) {
        this.db = db;
        this.broker = broker;
    }

    public void placeOrder(String orderId, int amount) {
        // TODO(stage 1): save the order AND an "OrderPlaced" outbox event
        // (payload Map.of("order_id", ..., "amount", ...)) in ONE transaction,
        // so either both exist or neither does. Don't publish to the broker
        // here — that's the dual write the outbox exists to avoid (the order
        // commits, the publish fails, and nobody ever hears about the order).
        throw new UnsupportedOperationException("not implemented");
    }
}

class OutboxRelay {
    private final Database db;
    private final Broker broker;

    OutboxRelay(Database db, Broker broker) {
        this.db = db;
        this.broker = broker;
    }

    public int runOnce() {
        return runOnce(100);
    }

    public int runOnce(int batchSize) {
        // TODO(stage 2): publish pending outbox events to the broker oldest
        // first, marking each one published; return how many you published.
        // TODO(stage 3): if the broker fails (BrokerException), stop right
        // there and return — leave that event (and everything after it)
        // pending for the next run. Never skip ahead (order matters) and
        // never mark an event published before the broker has it (that loses it).
        throw new UnsupportedOperationException("not implemented");
    }
}

/** Consumes OrderPlaced events and reserves stock for them. */
class InventoryConsumer {
    private int stock;

    InventoryConsumer(int stock) {
        this.stock = stock;
    }

    public int stock() {
        return stock;
    }

    public void handle(Event event) {
        // TODO(stage 4): reserve stock for the order (event.payload()
        // .get("amount") units). The relay is at-least-once — after a crash it
        // republishes events the broker already has — so the same event can
        // arrive twice: apply each event id only once.
        throw new UnsupportedOperationException("not implemented");
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a7000000-0000-0000-0000-000000000002',
    1,
    'one transaction',
    '주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다',
    $stage$// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
import java.util.List;
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
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);

        broker.failNextPublish(); // the broker being down must not matter when placing an order
        try {
            service.placeOrder("o-1", 3);
        } catch (BrokerException e) {
            throw new AssertionError("placeOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed");
        }
        check(db.orders().equals(Map.of("o-1", 3)), "the order should be saved, orders are " + db.orders());
        List<Event> outbox = db.outbox();
        check(outbox.size() == 1, "expected exactly 1 outbox event, got " + outbox);
        Event event = outbox.get(0);
        check("OrderPlaced".equals(event.type()), "expected an OrderPlaced event, got " + event.type());
        check(event.payload().equals(Map.of("order_id", "o-1", "amount", 3)), "unexpected payload " + event.payload());
        check(db.commits() == 1, "the order and its event must be written in ONE transaction, saw " + db.commits() + " commits");
        check(broker.published().isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)");
        // With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
        Database healthyDb = new Database();
        Broker healthyBroker = new Broker();
        new OrderService(healthyDb, healthyBroker).placeOrder("o-9", 1);
        check(healthyBroker.published().isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)");

        db.failNextCommit();
        try {
            service.placeOrder("o-2", 5);
            check(false, "a failed commit should surface to the caller as DatabaseException");
        } catch (DatabaseException e) {
            // expected
        }
        check(!db.orders().containsKey("o-2"), "after a failed commit the order must not exist");
        for (Event e : db.outbox()) {
            check(!"o-2".equals(e.payload().get("order_id")), "after a failed commit its event must not exist either");
        }
    }
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000002',
    2,
    'relay',
    '별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다',
    $stage$// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
import java.util.ArrayList;
import java.util.List;

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
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);
        OutboxRelay relay = new OutboxRelay(db, broker);
        int[] amounts = {3, 1, 4};
        for (int i = 0; i < amounts.length; i++) service.placeOrder("o-" + (i + 1), amounts[i]);

        int published = relay.runOnce();
        check(published == 3, "runOnce should report 3 published events, got " + published);
        List<String> ids = new ArrayList<>();
        List<Object> orderIds = new ArrayList<>();
        for (Event e : broker.published()) {
            ids.add(e.id());
            orderIds.add(e.payload().get("order_id"));
        }
        check(ids.equals(List.of("evt-1", "evt-2", "evt-3")), "events should be published oldest first, got " + ids);
        check(orderIds.equals(List.of("o-1", "o-2", "o-3")), "events should carry their orders oldest first, got " + orderIds);
        check(db.pendingEvents(100).isEmpty(), "published events should be marked, nothing left pending");

        check(relay.runOnce() == 0, "a second run with nothing pending should publish nothing");
        check(broker.published().size() == 3, "already-published events must not be sent again");
    }
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000002',
    3,
    'broker failure',
    '브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다',
    $stage$// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
import java.util.ArrayList;
import java.util.List;

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

    static List<String> ids(List<Event> events) {
        List<String> ids = new ArrayList<>();
        for (Event e : events) ids.add(e.id());
        return ids;
    }

    static void run() {
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);
        OutboxRelay relay = new OutboxRelay(db, broker);
        service.placeOrder("o-1", 3);
        check(relay.runOnce() == 1, "the first run should publish 1 event");

        service.placeOrder("o-2", 1);
        service.placeOrder("o-3", 4);
        broker.failNextPublish(); // evt-2 hits a broker hiccup
        int published;
        try {
            published = relay.runOnce();
        } catch (BrokerException e) {
            throw new AssertionError("runOnce should stop and return when the broker fails, not throw");
        }
        List<String> ids = ids(broker.published());
        check(ids.equals(List.of("evt-1")), "the relay must stop at the failed event, not skip ahead — broker has " + ids);
        check(published == 0, "nothing was published in that run, got " + published);
        List<String> pending = ids(db.pendingEvents(100));
        check(pending.equals(List.of("evt-2", "evt-3")), "the failed event must stay pending (never mark before publishing), pending is " + pending);

        check(relay.runOnce() == 2, "once the broker recovers, the next run should publish the rest");
        ids = ids(broker.published());
        check(ids.equals(List.of("evt-1", "evt-2", "evt-3")), "order must survive the failure, broker has " + ids);
    }
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000002',
    4,
    'idempotent consumer',
    '보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다',
    $stage$// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
import java.util.ArrayList;
import java.util.List;

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
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);
        OutboxRelay relay = new OutboxRelay(db, broker);
        service.placeOrder("o-1", 3);

        db.failNextMarkPublished(); // the relay crashes right after the broker took evt-1
        try {
            relay.runOnce();
        } catch (DatabaseException e) {
            // the crash
        }
        relay.runOnce(); // the restarted relay sends evt-1 again — it was never marked
        List<String> ids = new ArrayList<>();
        for (Event e : broker.published()) ids.add(e.id());
        check(ids.equals(List.of("evt-1", "evt-1")), "an event published but not marked should be sent again (at-least-once), broker has " + ids);

        InventoryConsumer consumer = new InventoryConsumer(10);
        for (Event event : broker.published()) consumer.handle(event);
        check(consumer.stock() == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got " + consumer.stock());

        service.placeOrder("o-2", 2);
        relay.runOnce();
        List<Event> published = broker.published();
        consumer.handle(published.get(published.size() - 1));
        check(consumer.stock() == 5, "a new event should still be applied: expected 5 left, got " + consumer.stock());
    }
}
$stage$
);

-- outbox-kotlin
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a7000000-0000-0000-0000-000000000003',
    'outbox-kotlin',
    'Build your own Transactional Outbox (Kotlin)',
    'kotlin',
    'Outbox.kt',
    $stub$/*
 * SysDrill Build Mode — Build your own Transactional Outbox (Kotlin)
 *
 * Implement OrderService, OutboxRelay and InventoryConsumer below across 4
 * stages (see README.md). Database, Transaction and Broker are provided and
 * complete — don't change them; the stage tests drive their failure hooks.
 * Keep the class and member names as-is. Submit by running ./submit.sh once
 * you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

class DatabaseException(message: String) : RuntimeException(message)

class BrokerException(message: String) : RuntimeException(message)

/**
 * An outbox event — what the relay hands to the broker and the broker hands
 * to consumers. Immutable; the payload is copied on the way in. Whether it's
 * been published is a column of the outbox row, kept inside Database (see
 * pendingEvents()), not part of the event itself.
 */
data class Event(val id: String, val type: String, val payload: Map<String, Any>)

/**
 * Provided — a tiny in-memory database with all-or-nothing transactions.
 *
 * Tables: orders (orderId -> amount) and the outbox (event rows with a
 * published flag, oldest first). Changes made through a Transaction only
 * become visible on commit(); a failed commit applies nothing.
 */
class Database {
    private class OutboxRow(val event: Event, var published: Boolean = false)

    private val orderRows = LinkedHashMap<String, Int>()
    private val outboxRows = mutableListOf<OutboxRow>()
    private var nextEvent = 1
    private var failNextCommit = false
    private var failNextMark = false

    /** The orders table (a copy). */
    val orders: Map<String, Int>
        get() = LinkedHashMap(orderRows)

    /** Every committed outbox event, published or not, oldest first. */
    val outbox: List<Event>
        get() = outboxRows.map { it.event }

    /** How many transactions have committed. */
    var commits = 0
        private set

    fun begin(): Transaction = Transaction(this)

    /** Committed outbox events not yet marked published, oldest first. */
    fun pendingEvents(limit: Int): List<Event> =
        outboxRows.filter { !it.published }.take(limit).map { it.event }

    fun markPublished(eventId: String) {
        if (failNextMark) {
            failNextMark = false
            throw DatabaseException("connection lost while marking the event published")
        }
        val row = outboxRows.find { it.event.id == eventId }
            ?: throw DatabaseException("no outbox event $eventId")
        row.published = true
    }

    // test hooks
    fun failNextCommit() {
        failNextCommit = true
    }

    fun failNextMarkPublished() {
        failNextMark = true
    }

    // used by Transaction
    internal fun nextEventNumber(): Int = nextEvent

    internal fun apply(newOrders: Map<String, Int>, newEvents: List<Event>) {
        if (failNextCommit) {
            failNextCommit = false
            throw DatabaseException("commit failed")
        }
        orderRows.putAll(newOrders)
        newEvents.forEach { outboxRows.add(OutboxRow(it)) }
        nextEvent += newEvents.size
        commits++
    }
}

/** Provided — stages writes and applies them all at once on commit(). */
class Transaction internal constructor(private val db: Database) {
    private val orders = HashMap<String, Int>()
    private val events = mutableListOf<Event>()
    private var done = false

    fun insertOrder(orderId: String, amount: Int) {
        orders[orderId] = amount
    }

    /** Stage an outbox event; returns the id it will have once committed. */
    fun insertEvent(eventType: String, payload: Map<String, Any>): String {
        val eventId = "evt-${db.nextEventNumber() + events.size}"
        events.add(Event(eventId, eventType, payload.toMap()))
        return eventId
    }

    fun commit() {
        if (done) throw DatabaseException("transaction already finished")
        done = true
        db.apply(orders, events)
    }

    fun rollback() {
        done = true
    }
}

/** Provided — a message broker (think Kafka) that consumers read from. */
class Broker {
    private val delivered = mutableListOf<Event>()
    private var failNext = false

    /** Every event delivered, in order — duplicates included (a copy). */
    val published: List<Event>
        get() = delivered.toList()

    fun publish(event: Event) {
        if (failNext) {
            failNext = false
            throw BrokerException("broker unavailable")
        }
        delivered.add(event)
    }

    // test hook
    fun failNextPublish() {
        failNext = true
    }
}

class OrderService(private val db: Database, private val broker: Broker) {
    fun placeOrder(orderId: String, amount: Int) {
        // TODO(stage 1): save the order AND an "OrderPlaced" outbox event
        // (payload mapOf("order_id" to ..., "amount" to ...)) in ONE
        // transaction, so either both exist or neither does. Don't publish to
        // the broker here — that's the dual write the outbox exists to avoid
        // (the order commits, the publish fails, and nobody ever hears about
        // the order).
        TODO("not implemented")
    }
}

class OutboxRelay(private val db: Database, private val broker: Broker) {
    fun runOnce(batchSize: Int = 100): Int {
        // TODO(stage 2): publish pending outbox events to the broker oldest
        // first, marking each one published; return how many you published.
        // TODO(stage 3): if the broker fails (BrokerException), stop right
        // there and return — leave that event (and everything after it)
        // pending for the next run. Never skip ahead (order matters) and
        // never mark an event published before the broker has it (that loses it).
        TODO("not implemented")
    }
}

/** Consumes OrderPlaced events and reserves stock for them. */
class InventoryConsumer(stock: Int) {
    var stock = stock
        private set

    fun handle(event: Event) {
        // TODO(stage 4): reserve stock for the order (event.payload["amount"]
        // units). The relay is at-least-once — after a crash it republishes
        // events the broker already has — so the same event can arrive twice:
        // apply each event id only once.
        TODO("not implemented")
    }
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a7000000-0000-0000-0000-000000000003',
    1,
    'one transaction',
    '주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다',
    $stage$@file:JvmName("RunTest")

// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val db = Database()
    val broker = Broker()
    val service = OrderService(db, broker)

    broker.failNextPublish() // the broker being down must not matter when placing an order
    try {
        service.placeOrder("o-1", 3)
    } catch (e: BrokerException) {
        throw AssertionError("placeOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed")
    }
    expect(db.orders == mapOf("o-1" to 3), "the order should be saved, orders are ${db.orders}")
    expect(db.outbox.size == 1, "expected exactly 1 outbox event, got ${db.outbox}")
    val event = db.outbox[0]
    expect(event.type == "OrderPlaced", "expected an OrderPlaced event, got ${event.type}")
    expect(event.payload == mapOf("order_id" to "o-1", "amount" to 3), "unexpected payload ${event.payload}")
    expect(db.commits == 1, "the order and its event must be written in ONE transaction, saw ${db.commits} commits")
    expect(broker.published.isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)")
    // With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
    val healthyBroker = Broker()
    OrderService(Database(), healthyBroker).placeOrder("o-9", 1)
    expect(healthyBroker.published.isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)")

    db.failNextCommit()
    try {
        service.placeOrder("o-2", 5)
        expect(false, "a failed commit should surface to the caller as DatabaseException")
    } catch (e: DatabaseException) {
        // expected
    }
    expect("o-2" !in db.orders, "after a failed commit the order must not exist")
    expect(db.outbox.all { it.payload["order_id"] != "o-2" }, "after a failed commit its event must not exist either")
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000003',
    2,
    'relay',
    '별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다',
    $stage$@file:JvmName("RunTest")

// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val db = Database()
    val broker = Broker()
    val service = OrderService(db, broker)
    val relay = OutboxRelay(db, broker)
    listOf(3, 1, 4).forEachIndexed { i, amount -> service.placeOrder("o-${i + 1}", amount) }

    val published = relay.runOnce()
    expect(published == 3, "runOnce should report 3 published events, got $published")
    val ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-2", "evt-3"), "events should be published oldest first, got $ids")
    val orderIds = broker.published.map { it.payload["order_id"] }
    expect(orderIds == listOf("o-1", "o-2", "o-3"), "events should carry their orders oldest first, got $orderIds")
    expect(db.pendingEvents(100).isEmpty(), "published events should be marked, nothing left pending")

    expect(relay.runOnce() == 0, "a second run with nothing pending should publish nothing")
    expect(broker.published.size == 3, "already-published events must not be sent again")
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000003',
    3,
    'broker failure',
    '브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다',
    $stage$@file:JvmName("RunTest")

// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val db = Database()
    val broker = Broker()
    val service = OrderService(db, broker)
    val relay = OutboxRelay(db, broker)
    service.placeOrder("o-1", 3)
    expect(relay.runOnce() == 1, "the first run should publish 1 event")

    service.placeOrder("o-2", 1)
    service.placeOrder("o-3", 4)
    broker.failNextPublish() // evt-2 hits a broker hiccup
    val published = try {
        relay.runOnce()
    } catch (e: BrokerException) {
        throw AssertionError("runOnce should stop and return when the broker fails, not throw")
    }
    var ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1"), "the relay must stop at the failed event, not skip ahead — broker has $ids")
    expect(published == 0, "nothing was published in that run, got $published")
    val pending = db.pendingEvents(100).map { it.id }
    expect(pending == listOf("evt-2", "evt-3"), "the failed event must stay pending (never mark before publishing), pending is $pending")

    expect(relay.runOnce() == 2, "once the broker recovers, the next run should publish the rest")
    ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-2", "evt-3"), "order must survive the failure, broker has $ids")
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000003',
    4,
    'idempotent consumer',
    '보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다',
    $stage$@file:JvmName("RunTest")

// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.

fun main() {
    try {
        stage()
        println("RESULT:PASS")
    } catch (e: AssertionError) {
        println("RESULT:FAIL:${e.message}")
        System.exit(1)
    } catch (e: NotImplementedError) {
        println("RESULT:FAIL:not implemented")
        System.exit(1)
    } catch (e: Throwable) {
        println("RESULT:FAIL:unexpected error: $e")
        System.exit(1)
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun stage() {
    val db = Database()
    val broker = Broker()
    val service = OrderService(db, broker)
    val relay = OutboxRelay(db, broker)
    service.placeOrder("o-1", 3)

    db.failNextMarkPublished() // the relay crashes right after the broker took evt-1
    try {
        relay.runOnce()
    } catch (e: DatabaseException) {
        // the crash
    }
    relay.runOnce() // the restarted relay sends evt-1 again — it was never marked
    val ids = broker.published.map { it.id }
    expect(ids == listOf("evt-1", "evt-1"), "an event published but not marked should be sent again (at-least-once), broker has $ids")

    val consumer = InventoryConsumer(stock = 10)
    for (event in broker.published) consumer.handle(event)
    expect(consumer.stock == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got ${consumer.stock}")

    service.placeOrder("o-2", 2)
    relay.runOnce()
    consumer.handle(broker.published.last())
    expect(consumer.stock == 5, "a new event should still be applied: expected 5 left, got ${consumer.stock}")
}
$stage$
);

-- outbox-go
insert into build_challenges (id, slug, title, languages, source_file_name, starter_code)
values (
    'a7000000-0000-0000-0000-000000000004',
    'outbox-go',
    'Build your own Transactional Outbox (Go)',
    'go',
    'outbox.go',
    $stub$// SysDrill Build Mode — Build your own Transactional Outbox (Go)
//
// Implement OrderService, OutboxRelay and InventoryConsumer below across 4
// stages (see README.md). Database, Transaction and Broker are provided and
// complete — don't change them; the stage tests drive their failure hooks.
// Keep the type, function and method names as-is. Submit by running
// ./submit.sh once you're ready.
//
// 채점 샌드박스는 이 파일과 테스트 파일을 `go run`으로 함께 빌드합니다(Go 표준
// 라이브러리만, 모듈 다운로드 불가). main 함수는 테스트 파일에 있으니 여기에는
// 넣지 마세요.
package main

import (
	"errors"
	"fmt"
	"maps"
)

// ErrDatabase is wrapped by every error the Database or a Transaction returns
// (check with errors.Is).
var ErrDatabase = errors.New("database error")

// ErrBroker is wrapped by every error the Broker returns (check with errors.Is).
var ErrBroker = errors.New("broker error")

// Event is an outbox event — what the relay hands to the broker and the
// broker hands to consumers. Whether it's been published is a column of the
// outbox row, kept inside Database (see PendingEvents), not part of the event.
type Event struct {
	ID      string
	Type    string
	Payload map[string]any
}

func (e Event) clone() Event {
	e.Payload = maps.Clone(e.Payload)
	return e
}

type outboxRow struct {
	event     Event
	published bool
}

// Database is provided — a tiny in-memory database with all-or-nothing
// transactions.
//
// Tables: orders (orderID -> amount) and the outbox (event rows with a
// published flag, oldest first). Changes made through a Transaction only
// become visible on Commit; a failed commit applies nothing.
type Database struct {
	orders         map[string]int
	outbox         []*outboxRow
	commits        int
	nextEvent      int
	failNextCommit bool
	failNextMark   bool
}

func NewDatabase() *Database {
	return &Database{orders: map[string]int{}, nextEvent: 1}
}

func (db *Database) Begin() *Transaction {
	return &Transaction{db: db, orders: map[string]int{}}
}

// Orders returns the orders table (a copy).
func (db *Database) Orders() map[string]int {
	return maps.Clone(db.orders)
}

// Outbox returns every committed outbox event, published or not, oldest first (copies).
func (db *Database) Outbox() []Event {
	events := []Event{}
	for _, row := range db.outbox {
		events = append(events, row.event.clone())
	}
	return events
}

// Commits returns how many transactions have committed.
func (db *Database) Commits() int {
	return db.commits
}

// PendingEvents returns committed outbox events not yet marked published,
// oldest first (copies).
func (db *Database) PendingEvents(limit int) []Event {
	pending := []Event{}
	for _, row := range db.outbox {
		if len(pending) >= limit {
			break
		}
		if !row.published {
			pending = append(pending, row.event.clone())
		}
	}
	return pending
}

func (db *Database) MarkPublished(eventID string) error {
	if db.failNextMark {
		db.failNextMark = false
		return fmt.Errorf("%w: connection lost while marking the event published", ErrDatabase)
	}
	for _, row := range db.outbox {
		if row.event.ID == eventID {
			row.published = true
			return nil
		}
	}
	return fmt.Errorf("%w: no outbox event %s", ErrDatabase, eventID)
}

// FailNextCommit is a test hook: the next Commit fails and applies nothing.
func (db *Database) FailNextCommit() {
	db.failNextCommit = true
}

// FailNextMarkPublished is a test hook: the next MarkPublished fails.
func (db *Database) FailNextMarkPublished() {
	db.failNextMark = true
}

// Transaction is provided — it stages writes and applies them all at once on Commit.
type Transaction struct {
	db     *Database
	orders map[string]int
	events []Event
	done   bool
}

func (tx *Transaction) InsertOrder(orderID string, amount int) {
	tx.orders[orderID] = amount
}

// InsertEvent stages an outbox event and returns the ID it will have once committed.
func (tx *Transaction) InsertEvent(eventType string, payload map[string]any) string {
	eventID := fmt.Sprintf("evt-%d", tx.db.nextEvent+len(tx.events))
	tx.events = append(tx.events, Event{ID: eventID, Type: eventType, Payload: maps.Clone(payload)})
	return eventID
}

func (tx *Transaction) Commit() error {
	if tx.done {
		return fmt.Errorf("%w: transaction already finished", ErrDatabase)
	}
	tx.done = true
	db := tx.db
	if db.failNextCommit {
		db.failNextCommit = false
		return fmt.Errorf("%w: commit failed", ErrDatabase)
	}
	maps.Copy(db.orders, tx.orders)
	for _, e := range tx.events {
		db.outbox = append(db.outbox, &outboxRow{event: e})
	}
	db.nextEvent += len(tx.events)
	db.commits++
	return nil
}

func (tx *Transaction) Rollback() {
	tx.done = true
}

// Broker is provided — a message broker (think Kafka) that consumers read from.
type Broker struct {
	published []Event
	failNext  bool
}

func NewBroker() *Broker {
	return &Broker{}
}

// Published returns every event delivered, in order — duplicates included (copies).
func (b *Broker) Published() []Event {
	events := []Event{}
	for _, e := range b.published {
		events = append(events, e.clone())
	}
	return events
}

func (b *Broker) Publish(event Event) error {
	if b.failNext {
		b.failNext = false
		return fmt.Errorf("%w: broker unavailable", ErrBroker)
	}
	b.published = append(b.published, event.clone())
	return nil
}

// FailNextPublish is a test hook: the next Publish fails.
func (b *Broker) FailNextPublish() {
	b.failNext = true
}

type OrderService struct {
	db     *Database
	broker *Broker
}

func NewOrderService(db *Database, broker *Broker) *OrderService {
	return &OrderService{db: db, broker: broker}
}

func (s *OrderService) PlaceOrder(orderID string, amount int) error {
	// TODO(stage 1): save the order AND an "OrderPlaced" outbox event
	// (payload map[string]any{"order_id": ..., "amount": ...}) in ONE
	// transaction, so either both exist or neither does, and return the
	// commit's error. Don't publish to the broker here — that's the dual
	// write the outbox exists to avoid (the order commits, the publish fails,
	// and nobody ever hears about the order).
	panic("not implemented")
}

type OutboxRelay struct {
	db     *Database
	broker *Broker
}

func NewOutboxRelay(db *Database, broker *Broker) *OutboxRelay {
	return &OutboxRelay{db: db, broker: broker}
}

// RunOnce publishes up to batchSize pending events and returns how many it published.
func (r *OutboxRelay) RunOnce(batchSize int) (int, error) {
	// TODO(stage 2): publish pending outbox events to the broker oldest
	// first, marking each one published; return how many you published.
	// TODO(stage 3): if the broker fails (Publish returns an error), stop
	// right there and return the count with a nil error — leave that event
	// (and everything after it) pending for the next run. Never skip ahead
	// (order matters) and never mark an event published before the broker
	// has it (that loses it).
	panic("not implemented")
}

// InventoryConsumer consumes OrderPlaced events and reserves stock for them.
type InventoryConsumer struct {
	stock int
}

func NewInventoryConsumer(stock int) *InventoryConsumer {
	return &InventoryConsumer{stock: stock}
}

func (c *InventoryConsumer) Stock() int {
	return c.stock
}

func (c *InventoryConsumer) Handle(event Event) {
	// TODO(stage 4): reserve stock for the order (event.Payload["amount"]
	// units, an int). The relay is at-least-once — after a crash it
	// republishes events the broker already has — so the same event can
	// arrive twice: apply each event ID only once.
	panic("not implemented")
}
$stub$
);

insert into build_stages (challenge_id, stage_order, title, spec, test_script)
values
(
    'a7000000-0000-0000-0000-000000000004',
    1,
    'one transaction',
    '주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다',
    $stage$// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
package main

import (
	"errors"
	"fmt"
	"os"
	"reflect"
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

func ids(events []Event) []string {
	ids := []string{}
	for _, e := range events {
		ids = append(ids, e.ID)
	}
	return ids
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service := NewOrderService(db, broker)

	broker.FailNextPublish() // the broker being down must not matter when placing an order
	err := service.PlaceOrder("o-1", 3)
	expect(!errors.Is(err, ErrBroker), "PlaceOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed")
	if err != nil {
		panic(err)
	}
	expect(reflect.DeepEqual(db.Orders(), map[string]int{"o-1": 3}), "the order should be saved, orders are %v", db.Orders())
	outbox := db.Outbox()
	expect(len(outbox) == 1, "expected exactly 1 outbox event, got %v", outbox)
	event := outbox[0]
	expect(event.Type == "OrderPlaced", "expected an OrderPlaced event, got %s", event.Type)
	expect(reflect.DeepEqual(event.Payload, map[string]any{"order_id": "o-1", "amount": 3}), "unexpected payload %v", event.Payload)
	expect(db.Commits() == 1, "the order and its event must be written in ONE transaction, saw %d commits", db.Commits())
	expect(len(broker.Published()) == 0, "PlaceOrder must not publish to the broker itself (that's the dual write)")
	// With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
	healthyBroker := NewBroker()
	if err := NewOrderService(NewDatabase(), healthyBroker).PlaceOrder("o-9", 1); err != nil {
		panic(err)
	}
	expect(len(healthyBroker.Published()) == 0, "PlaceOrder must not publish to the broker itself (that's the dual write)")

	db.FailNextCommit()
	err = service.PlaceOrder("o-2", 5)
	expect(errors.Is(err, ErrDatabase), "a failed commit should surface to the caller as ErrDatabase, got %v", err)
	_, exists := db.Orders()["o-2"]
	expect(!exists, "after a failed commit the order must not exist")
	for _, e := range db.Outbox() {
		expect(e.Payload["order_id"] != "o-2", "after a failed commit its event must not exist either")
	}
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000004',
    2,
    'relay',
    '별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다',
    $stage$// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
package main

import (
	"fmt"
	"os"
	"reflect"
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

func ids(events []Event) []string {
	ids := []string{}
	for _, e := range events {
		ids = append(ids, e.ID)
	}
	return ids
}

func mustRun(relay *OutboxRelay) int {
	n, err := relay.RunOnce(100)
	if err != nil {
		panic(err)
	}
	return n
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service, relay := NewOrderService(db, broker), NewOutboxRelay(db, broker)
	for i, amount := range []int{3, 1, 4} {
		if err := service.PlaceOrder(fmt.Sprintf("o-%d", i+1), amount); err != nil {
			panic(err)
		}
	}

	published := mustRun(relay)
	expect(published == 3, "RunOnce should report 3 published events, got %d", published)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-2", "evt-3"}), "events should be published oldest first, got %v", got)
	orderIDs := []any{}
	for _, e := range broker.Published() {
		orderIDs = append(orderIDs, e.Payload["order_id"])
	}
	expect(reflect.DeepEqual(orderIDs, []any{"o-1", "o-2", "o-3"}), "events should carry their orders oldest first, got %v", orderIDs)
	expect(len(db.PendingEvents(100)) == 0, "published events should be marked, nothing left pending")

	expect(mustRun(relay) == 0, "a second run with nothing pending should publish nothing")
	expect(len(broker.Published()) == 3, "already-published events must not be sent again")
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000004',
    3,
    'broker failure',
    '브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다',
    $stage$// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
package main

import (
	"errors"
	"fmt"
	"os"
	"reflect"
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

func ids(events []Event) []string {
	ids := []string{}
	for _, e := range events {
		ids = append(ids, e.ID)
	}
	return ids
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service, relay := NewOrderService(db, broker), NewOutboxRelay(db, broker)
	must(service.PlaceOrder("o-1", 3))
	n, err := relay.RunOnce(100)
	must(err)
	expect(n == 1, "the first run should publish 1 event, got %d", n)

	must(service.PlaceOrder("o-2", 1))
	must(service.PlaceOrder("o-3", 4))
	broker.FailNextPublish() // evt-2 hits a broker hiccup
	published, err := relay.RunOnce(100)
	expect(!errors.Is(err, ErrBroker), "RunOnce should stop and return when the broker fails, not return an error")
	must(err)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1"}), "the relay must stop at the failed event, not skip ahead — broker has %v", got)
	expect(published == 0, "nothing was published in that run, got %d", published)
	pending := ids(db.PendingEvents(100))
	expect(reflect.DeepEqual(pending, []string{"evt-2", "evt-3"}), "the failed event must stay pending (never mark before publishing), pending is %v", pending)

	n, err = relay.RunOnce(100)
	must(err)
	expect(n == 2, "once the broker recovers, the next run should publish the rest")
	got = ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-2", "evt-3"}), "order must survive the failure, broker has %v", got)
}
$stage$
),
(
    'a7000000-0000-0000-0000-000000000004',
    4,
    'idempotent consumer',
    '보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다',
    $stage$// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
package main

import (
	"errors"
	"fmt"
	"os"
	"reflect"
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

func ids(events []Event) []string {
	ids := []string{}
	for _, e := range events {
		ids = append(ids, e.ID)
	}
	return ids
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service, relay := NewOrderService(db, broker), NewOutboxRelay(db, broker)
	must(service.PlaceOrder("o-1", 3))

	db.FailNextMarkPublished() // the relay crashes right after the broker took evt-1
	if _, err := relay.RunOnce(100); err != nil && !errors.Is(err, ErrDatabase) {
		panic(err)
	}
	_, err := relay.RunOnce(100) // the restarted relay sends evt-1 again — it was never marked
	must(err)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-1"}), "an event published but not marked should be sent again (at-least-once), broker has %v", got)

	consumer := NewInventoryConsumer(10)
	for _, event := range broker.Published() {
		consumer.Handle(event)
	}
	expect(consumer.Stock() == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got %d", consumer.Stock())

	must(service.PlaceOrder("o-2", 2))
	_, err = relay.RunOnce(100)
	must(err)
	published := broker.Published()
	consumer.Handle(published[len(published)-1])
	expect(consumer.Stock() == 5, "a new event should still be applied: expected 5 left, got %d", consumer.Stock())
}
$stage$
);

-- 학습 개념 → 새 Build 과제
update learning_concepts set related_challenges = $ct$["consistent-hashing"]$ct$::jsonb where risk_key = 'MISSING_KEY_DISTRIBUTION';
update learning_concepts set related_challenges = $ct$["outbox"]$ct$::jsonb where risk_key = 'MISSING_TRANSACTION_BOUNDARY';
update learning_concepts set related_challenges = $ct$["queue", "event-bus", "outbox"]$ct$::jsonb where risk_key = 'MISSING_IDEMPOTENT_CONSUMER';
