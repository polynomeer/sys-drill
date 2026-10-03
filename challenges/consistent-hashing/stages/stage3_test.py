"""Stage 3 — virtual nodes.
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
