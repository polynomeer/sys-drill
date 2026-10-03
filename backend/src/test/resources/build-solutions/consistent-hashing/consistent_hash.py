"""Model answer for Build your own Consistent Hashing (Python) — used by BuildLanguageVariantsIntegrationTest."""
import bisect
import hashlib


def ring_hash(value: str) -> int:
    return int.from_bytes(hashlib.md5(value.encode("utf-8")).digest()[:4], "big")


class HashRing:
    def __init__(self, virtual_nodes: int = 100):
        self.virtual_nodes = virtual_nodes
        self._points = []  # sorted ring positions
        self._owner = {}  # position -> node

    def add_node(self, node):
        for i in range(self.virtual_nodes):
            point = ring_hash(f"{node}#{i}")
            if point not in self._owner:  # on a (rare) collision the first node keeps the spot
                self._owner[point] = node
                bisect.insort(self._points, point)

    def remove_node(self, node):
        self._points = [p for p in self._points if self._owner[p] != node]
        self._owner = {p: n for p, n in self._owner.items() if n != node}

    def get_node(self, key):
        nodes = self.get_nodes(key, 1)
        return nodes[0] if nodes else None

    def get_nodes(self, key, n):
        if not self._points:
            return []
        start = bisect.bisect_left(self._points, ring_hash(key))
        picked = []
        for step in range(len(self._points)):
            node = self._owner[self._points[(start + step) % len(self._points)]]
            if node not in picked:
                picked.append(node)
                if len(picked) == n:
                    break
        return picked
