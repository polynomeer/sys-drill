// Stage 1 — mutual exclusion.
// 학습 포인트: 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다.
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

    static void run() {
        LockStore store = new LockStore();
        DistributedLock lockA = new DistributedLock("resource-1", store, 5.0);
        DistributedLock lockB = new DistributedLock("resource-1", store, 5.0);

        Long tokenA = lockA.acquire("owner-a");
        check(tokenA != null, "owner-a should acquire the free lock");
        check(lockA.isLocked(), "the lock should report locked while owner-a holds it");

        Long tokenB = lockB.acquire("owner-b");
        check(tokenB == null, "owner-b should not acquire while owner-a holds the lock");

        boolean released = lockA.release("owner-a", tokenA);
        check(released, "owner-a should be able to release its own lock");

        Long tokenB2 = lockB.acquire("owner-b");
        check(tokenB2 != null, "owner-b should acquire after owner-a releases");
    }
}
