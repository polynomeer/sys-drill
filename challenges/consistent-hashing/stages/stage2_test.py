"""Stage 2 — minimal remapping.
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
