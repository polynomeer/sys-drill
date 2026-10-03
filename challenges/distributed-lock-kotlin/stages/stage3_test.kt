@file:JvmName("RunTest")

// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.

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
    val lockB = DistributedLock("resource-1", store = store, leaseSeconds = 5.0)

    val tokenA = lockA.acquire("owner-a")
    expect(tokenA != null, "owner-a should acquire the free lock")
    Thread.sleep(400) // owner-a stalls (e.g. GC pause) past its own lease

    val tokenB = lockB.acquire("owner-b")
    expect(tokenB != null, "owner-b should acquire after owner-a's lease expired")
    expect(tokenB!! > tokenA!!, "fencing tokens must increase monotonically across acquisitions")

    // owner-a wakes up late and tries to release with its now-stale token
    val released = lockA.release("owner-a", tokenA)
    expect(!released, "a stale owner/token pair must not be able to release the current holder's lock")
    expect(lockB.isLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt")
}
