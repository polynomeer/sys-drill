@file:JvmName("RunTest")

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
