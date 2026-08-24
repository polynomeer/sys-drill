package com.sysdrill.backend.evaluation

import com.sysdrill.backend.submission.Submission
import org.springframework.stereotype.Component

data class StubEvaluationResult(
    val rubricVersion: String,
    val totalScore: Int?,
    val weaknesses: String,
)

/**
 * Placeholder for the real Rule+AI evaluation pipeline (docs/ARCHITECTURE.md
 * §7), which lands in PLAN.md step 5. This only proves the async pipeline
 * (queue -> worker -> Evaluation row -> session transition) works end to end.
 *
 * Deliberately fails when a submission's text contains [FORCE_FAILURE_MARKER]
 * so EvaluationWorker's retry/dead-letter path can be exercised in tests
 * without relying on flaky/random failure injection.
 */
@Component
class StubRuleEvaluator {

    fun evaluate(submission: Submission): StubEvaluationResult {
        check(submission.rawText?.contains(FORCE_FAILURE_MARKER) != true) {
            "Stub evaluator forced failure for testing (submission ${submission.id})"
        }
        return StubEvaluationResult(
            rubricVersion = "stub-v0",
            totalScore = null,
            weaknesses = """["실제 평가 엔진은 5단계에서 추가될 예정입니다 (현재는 스텁 결과)"]""",
        )
    }

    companion object {
        const val FORCE_FAILURE_MARKER = "FORCE_EVAL_FAILURE"
    }
}
