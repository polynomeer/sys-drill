@file:JvmName("RunTest")

// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.

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
    val lockA = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    expect(lockA.isLocked(), "the lock should report locked while owner-a holds it")

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB == null, "owner-b should not acquire while owner-a holds the lock")

    val released = lockA.release("owner-a", tokenA!!)
    expect(released, "owner-a should be able to release its own lock")

    val tokenB2 = lockB.acquire("owner-b")
    expect(tokenB2 != null, "owner-b should acquire after owner-a releases")
}
