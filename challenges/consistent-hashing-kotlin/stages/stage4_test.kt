@file:JvmName("RunTest")

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
