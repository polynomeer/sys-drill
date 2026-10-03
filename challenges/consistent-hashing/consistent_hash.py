"""
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
