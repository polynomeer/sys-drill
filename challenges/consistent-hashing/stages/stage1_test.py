"""Stage 1 — ring lookup.
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
