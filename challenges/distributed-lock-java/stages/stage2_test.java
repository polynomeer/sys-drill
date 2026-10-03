// Stage 2 — lease/TTL expiry.
// 학습 포인트: release 없이도 lease가 지나면 락이 풀려야 하는 이유.
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
        DistributedLock lockB = new DistributedLock("resource-1", store, 0.3);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        check(lockB.acquire("owner-b") == null, "owner-b should not acquire before the lease expires");

        Thread.sleep(400);
        check(!lockA.isLocked(), "the lock should report unlocked once the lease has expired");

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB != null, "owner-b should acquire once owner-a's lease has expired without release");
    }
}
