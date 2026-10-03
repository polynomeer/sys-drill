@file:JvmName("RunTest")

// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.

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
    val store = LockStore()
    val lockA = DistributedLock("resource-1", store = store, leaseSeconds = 0.3)
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 0.3)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    expect(lockB.acquire("owner-b") == null, "owner-b should not acquire before the lease expires")

    Thread.sleep(400)
    expect(!lockA.isLocked(), "the lock should report unlocked once the lease has expired")

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB != null, "owner-b should acquire once owner-a's lease has expired without release")
}
