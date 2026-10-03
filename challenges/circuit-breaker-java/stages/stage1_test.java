// Stage 1 — normal operation (CLOSED).
// 학습 포인트: pass-through 기본 동작 (CLOSED 상태에서는 감싼 함수를 그대로 부르고 결과를 돌려준다).
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

    static void run() throws Exception {
        CircuitBreaker cb = new CircuitBreaker(3, 1.0);
        Object result = cb.call(() -> 42);
        check(Integer.valueOf(42).equals(result), "expected 42, got " + result);
        check(cb.state() == State.CLOSED, "expected CLOSED, got " + cb.state());
        for (int i = 0; i < 5; i++) {
            Object ok = cb.call(() -> "ok");
            check("ok".equals(ok), "expected \"ok\", got " + ok);
        }
        check(cb.state() == State.CLOSED, "expected CLOSED after 5 successful calls, got " + cb.state());
    }
}
