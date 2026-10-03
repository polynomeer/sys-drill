@file:JvmName("RunTest")

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
