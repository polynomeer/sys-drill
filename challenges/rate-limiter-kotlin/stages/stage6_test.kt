@file:JvmName("RunTest")

// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.

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
    val rl = RateLimiter(capacity = 2, windowSeconds = 5.0)
    repeat(3) { rl.allow("k") }
    val m = rl.metrics
    expect(m.allowed == 2L, "expected 2 allowed, got ${m.allowed}")
    expect(m.rejected == 1L, "expected 1 rejected, got ${m.rejected}")
    expect(Math.abs(m.rejectRate - 1.0 / 3) < 0.01, "expected rejectRate ~0.333, got ${m.rejectRate}")
}
