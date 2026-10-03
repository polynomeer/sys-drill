@file:JvmName("RunTest")

// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.

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
    val budget = RetryBudget(capacity = 2)

    var attemptsP1 = 0
    val policy1 = RetryPolicy(maxAttempts = 10, baseDelay = 0.001, budget = budget, sleepFn = { })
    try {
        policy1.execute<Unit> {
            attemptsP1++
            throw IllegalArgumentException("boom")
        }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attemptsP1 < 10, "a shared retry budget should cut retries short before maxAttempts is reached")

    var attemptsP2 = 0
    val policy2 = RetryPolicy(maxAttempts = 10, baseDelay = 0.001, budget = budget, sleepFn = { })
    try {
        policy2.execute<Unit> {
            attemptsP2++
            throw IllegalArgumentException("boom")
        }
    } catch (e: RetryExhaustedException) {
        // expected
    }
    expect(attemptsP2 <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all")
}
