@file:JvmName("RunTest")

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
