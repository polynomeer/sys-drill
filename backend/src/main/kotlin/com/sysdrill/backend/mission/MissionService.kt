package com.sysdrill.backend.mission

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * PLAN.md Round E8 — reading/writing `sessions.mission_state` and the mission add-ons
 * authored on a scenario's steps (docs/DRILLS_EXPANSION_PLAN.md §5-1).
 *
 * Content is optional end to end: a scenario without `clarifications` (every v1
 * official scenario until ADR-0048's v2) simply reports `available = false`, and the
 * evaluation prompt gets no extra section.
 */
@Service
class MissionService(
    private val sessionRepository: SessionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val mapper: ObjectMapper,
) {
    // Step content and mission_state carry keys these classes don't model (prompt, variants,
    // later rounds' fields) — ignore them instead of failing.
    private fun <T> read(json: String, type: Class<T>): T =
        mapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json)

    fun state(session: Session): MissionState =
        runCatching { read(session.missionState, MissionState::class.java) }.getOrDefault(MissionState())

    /** The JSON to store when pinning the tail-design variant (the caller saves the session). */
    fun withFollowupVariant(session: Session, key: String): String =
        mapper.writeValueAsString(state(session).copy(followupVariantKey = key))

    fun save(session: Session, state: MissionState) {
        session.missionState = mapper.writeValueAsString(state)
        sessionRepository.save(session)
    }

    fun initialContent(session: Session): InitialMissionContent {
        val step = scenarioStepRepository.findByScenarioVersionIdOrderByStepOrder(session.scenarioVersionId)
            .firstOrNull { it.stepType == "INITIAL" } ?: return InitialMissionContent()
        return parseInitial(step)
    }

    fun parseInitial(step: ScenarioStep): InitialMissionContent =
        step.content?.let { runCatching { read(it, InitialMissionContent::class.java) }.getOrNull() }
            ?: InitialMissionContent()

    // ---- M1: clarifying questions ----

    fun clarifications(sessionId: UUID): ClarificationsResponse {
        val session = requireSession(sessionId)
        val questions = initialContent(session).clarifications
        val asked = state(session).askedClarifications
        return ClarificationsResponse.of(questions, asked, canAsk = canAsk(session))
    }

    /**
     * Reveals one answer and records that it was asked. Only while the INITIAL design is
     * being written — the evaluation reads the asked set at submit time, so asking after
     * submitting would rewrite history.
     */
    @Transactional
    fun ask(sessionId: UUID, questionId: String): ClarificationsResponse {
        val session = requireSession(sessionId)
        val questions = initialContent(session).clarifications
        if (questions.none { it.id == questionId }) throw NotFoundException("Clarification not found: $questionId")
        if (!canAsk(session)) throw ConflictException("요구사항 질문은 초기 설계를 제출하기 전까지만 할 수 있습니다")
        val current = state(session)
        if (questionId !in current.askedClarifications) {
            save(session, current.copy(askedClarifications = current.askedClarifications + questionId))
        }
        return ClarificationsResponse.of(questions, state(session).askedClarifications, canAsk = true)
    }

    private fun canAsk(session: Session): Boolean =
        session.currentPhase == "INITIAL" && session.status == SessionStatus.IN_PROGRESS

    /** The evaluation prompt's M1 section for an INITIAL submission, or null when the scenario has no questions. */
    fun clarificationPromptSection(session: Session): String? {
        val questions = initialContent(session).clarifications
        if (questions.isEmpty()) return null
        val asked = state(session).askedClarifications.toSet()
        val askedQuestions = questions.filter { it.id in asked }
        val missedCritical = questions.filter { it.critical && it.id !in asked }
        return buildString {
            appendLine("## 요구사항 확인 (사용자가 설계 전에 질문으로 확인한 것)")
            appendLine("이 시나리오의 문제 설명은 일부러 불완전합니다. 사용자는 아래 질문으로 요구사항을 확인할 수 있었습니다.")
            if (askedQuestions.isEmpty()) {
                appendLine("- (아무 질문도 하지 않았습니다)")
            } else {
                askedQuestions.forEach { appendLine("- Q. ${it.question} → A. ${it.answer}") }
            }
            appendLine()
            appendLine("## 확인하지 않은 핵심 요구사항 (사용자는 이 답을 모른 채 설계했습니다)")
            if (missedCritical.isEmpty()) {
                appendLine("- 없음 — 핵심 질문을 모두 확인했습니다")
            } else {
                missedCritical.forEach { appendLine("- Q. ${it.question} → 실제 요구사항: ${it.answer}") }
            }
            appendLine("요구사항 해석력 항목은 확인한 정보를 설계에 반영했는지와, 확인하지 않은 핵심 요구사항을 설계가 우연히라도 만족하는지로 판단하세요. 무관한 질문을 한 것은 감점하지 마세요.")
        }
    }

    private fun requireSession(sessionId: UUID): Session =
        sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }
}

data class ClarificationQuestion(
    val id: String,
    val question: String,
    val asked: Boolean,
    /** Only once asked — never sent ahead of time. */
    val answer: String?,
    /** Only once [ClarificationsResponse.canAsk] is over (after INITIAL), so the report can say which were critical. */
    val critical: Boolean?,
)

data class ClarificationsResponse(
    /** False when the scenario has no clarifying questions — the UI shows nothing. */
    val available: Boolean,
    val canAsk: Boolean,
    val questions: List<ClarificationQuestion>,
    /** Report summary, filled once asking is over. */
    val criticalAsked: Int?,
    val criticalTotal: Int?,
) {
    companion object {
        fun of(questions: List<Clarification>, asked: List<String>, canAsk: Boolean): ClarificationsResponse {
            val askedSet = asked.toSet()
            // Asked ones in the order they were asked, then the rest in authored order.
            val ordered = asked.mapNotNull { id -> questions.firstOrNull { it.id == id } } + questions.filter { it.id !in askedSet }
            return ClarificationsResponse(
                available = questions.isNotEmpty(),
                canAsk = canAsk,
                questions = ordered.map {
                    ClarificationQuestion(
                        id = it.id,
                        question = it.question,
                        asked = it.id in askedSet,
                        answer = it.answer.takeIf { _ -> it.id in askedSet || !canAsk },
                        critical = it.critical.takeIf { !canAsk },
                    )
                },
                criticalAsked = if (canAsk) null else questions.count { it.critical && it.id in askedSet },
                criticalTotal = if (canAsk) null else questions.count { it.critical },
            )
        }
    }
}
