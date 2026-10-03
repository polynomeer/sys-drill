// Stage 3 — fencing token.
// 학습 포인트: 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유.
class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() throws InterruptedException {
        LockStore store = new LockStore();
        DistributedLock lockA = new DistributedLock("resource-1", store, 0.3);
        DistributedLock lockB = new DistributedLock("resource-1", store, 5.0);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        Thread.sleep(400); // owner-a stalls (e.g. GC pause) past its own lease

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB != null, "owner-b should acquire after owner-a's lease expired");
        check(tokenB > tokenA, "fencing tokens must increase monotonically across acquisitions");

        // owner-a wakes up late and tries to release with its now-stale token
        boolean released = lockA.release("owner-a", tokenA);
        check(!released, "a stale owner/token pair must not be able to release the current holder's lock");
        check(lockB.isLocked(), "owner-b's lock must remain held despite owner-a's stale release attempt");
    }
}
