"""Stage 4 — replicas.
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
