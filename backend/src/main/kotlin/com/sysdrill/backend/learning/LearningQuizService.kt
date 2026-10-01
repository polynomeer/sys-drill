package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.NotFoundException
import org.springframework.stereotype.Service
import kotlin.random.Random

data class QuizOption(
    val text: String,
    val correct: Boolean,
    /** Which concept this pattern belongs to — shown after answering, so a wrong pick still teaches something. */
    val fromRiskKey: String,
    val fromLabel: String,
)

data class ConceptQuiz(
    val riskKey: String,
    val question: String,
    val options: List<QuizOption>,
)

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.6 — a one-question self-check at the end of
 * a concept page, **built from existing concept data** instead of authored
 * questions: the right answer is one of this concept's own solution patterns,
 * the distractors are patterns of other concepts (same category first, so
 * they're plausible). Nothing is invented, and every option can say which
 * concept it really belongs to.
 *
 * It's a self-check, not an assessment — correctness ships with the options
 * and the page grades locally; nothing is recorded.
 */
@Service
class LearningQuizService(private val conceptRepository: LearningConceptRepository) {

    fun quiz(riskKey: String, random: Random = Random.Default): ConceptQuiz {
        val all = conceptRepository.findAll()
        val concept = all.firstOrNull { it.riskKey == riskKey } ?: throw NotFoundException("No learning concept for $riskKey")
        if (concept.patterns.isEmpty()) throw NotFoundException("Concept $riskKey has no patterns to quiz on")

        val own = concept.patterns.toSet()
        val others = all.filter { it.riskKey != riskKey }
        // Same category first (plausible distractors), then the rest; never a text this concept also lists.
        val candidates = (others.filter { it.category == concept.category }.shuffled(random) +
            others.filter { it.category != concept.category }.shuffled(random))
            // One pattern per concept, so the distractors point to different concepts.
            .mapNotNull { other -> other.patterns.filter { it !in own }.shuffled(random).firstOrNull()?.let { it to other } }
            .distinctBy { it.first }
            .take(DISTRACTORS)

        val answer = concept.patterns.random(random)
        val options = (candidates.map { (text, from) -> QuizOption(text, false, from.riskKey, from.label) } +
            QuizOption(answer, true, concept.riskKey, concept.label)).shuffled(random)
        return ConceptQuiz(
            riskKey = riskKey,
            question = "다음 중 '${concept.label}' 문제를 해결하는 패턴은 무엇인가요?",
            options = options,
        )
    }

    private companion object {
        const val DISTRACTORS = 2
    }
}
