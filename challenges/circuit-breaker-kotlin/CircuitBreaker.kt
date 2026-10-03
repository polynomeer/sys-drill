/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Kotlin)
 *
 * Implement `CircuitBreaker` below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

enum class State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException(message: String) : RuntimeException(message)

/**
 * Wraps calls to a possibly-failing function (e.g. an external API) and
 * stops calling it once it's clearly broken, instead of letting every
 * caller wait out its own timeout.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val recoveryTimeout: Double = 5.0, // seconds
) {
    init {
        // TODO(stage 1): store config and start CLOSED.
        TODO("not implemented")
    }

    val state: State
        // TODO(stage 1): CLOSED | OPEN | HALF_OPEN.
        // TODO(stage 3): once OPEN and recoveryTimeout has elapsed since the
        // trip, reading state should report HALF_OPEN (a real trial call
        // hasn't necessarily happened yet — this is a state *transition*,
        // not just a label).
        get() = TODO("not implemented")

    fun <T> call(fn: () -> T): T {
        // TODO(stage 1): while CLOSED, call fn() and return its result.
        // TODO(stage 2): count consecutive failures; once failureThreshold is
        // reached, trip to OPEN. While OPEN, throw CircuitOpenException immediately
        // WITHOUT calling fn — that's the whole point (fail fast).
        // TODO(stage 3): once state has moved to HALF_OPEN (see `state`),
        // the next call() is a *trial*: run fn for real, and if it
        // succeeds, recover to CLOSED (reset the failure count too).
        // TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        // and restart the recoveryTimeout countdown from now.
        TODO("not implemented")
    }
}
