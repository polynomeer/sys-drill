package com.sysdrill.backend.mission

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
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
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
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

    companion object {
        const val DEFENSE_QUESTION_COUNT = 2
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

    // ---- M2: scale estimation ----

    /**
     * Before the INITIAL submit: the fields to fill, no answers. After it: each estimate judged
     * against the scenario's value. Estimates travel in the INITIAL submission's
     * `structured_json` ({"estimates": {key: number}}) — they're fixed at submit time.
     */
    fun estimation(sessionId: UUID): EstimationResponse {
        val session = requireSession(sessionId)
        val fields = initialContent(session).estimation
        if (fields.isEmpty()) return EstimationResponse(available = false, open = false, fields = emptyList(), results = null)
        val initial = initialSubmission(session)
        if (initial == null) {
            return EstimationResponse(
                available = true,
                open = canAsk(session),
                fields = fields.map { EstimationFieldView(it.key, it.label, it.unit, it.hint) },
                results = null,
            )
        }
        return EstimationResponse(
            available = true,
            open = false,
            fields = fields.map { EstimationFieldView(it.key, it.label, it.unit, it.hint) },
            results = judgeEstimates(fields, estimatesOf(initial)),
        )
    }

    /** The evaluation prompt's M2 section for an INITIAL submission. */
    fun estimationPromptSection(session: Session, submission: Submission): String? {
        val fields = initialContent(session).estimation
        if (fields.isEmpty()) return null
        val results = judgeEstimates(fields, estimatesOf(submission))
        return buildString {
            appendLine("## 규모 추정 판정 (자릿수 기준 — 실제 값의 0.5~2배면 적중)")
            fields.zip(results).forEach { (field, r) ->
                val estimate = r.estimate?.let { "%,.0f".format(it) } ?: "(입력 안 함)"
                val verdict = when (r.direction) {
                    "ON_TARGET" -> "적중"
                    "UNDER" -> "과소 추정 (실제의 ${"%.0f".format((r.ratio ?: 0.0) * 100)}%)"
                    "OVER" -> "과대 추정 (실제의 ${"%.1f".format(r.ratio ?: 0.0)}배)"
                    else -> "추정하지 않음"
                }
                appendLine("- ${field.label}: 사용자 $estimate ${field.unit} / 실제 ${"%,.0f".format(field.answer)} ${field.unit} → $verdict")
            }
            appendLine("크게 빗나간 추정이 설계 선택(용량·샤딩·캐시 크기 등)에 어떤 영향을 줬는지 요구사항 해석력 항목에서 짚어 주세요. 정확한 숫자를 맞혔는지가 아니라 규모 감각을 봅니다.")
        }
    }

    // ---- M4: design defense ----

    /**
     * docs/DRILLS_EXPANSION_PLAN.md M4 (PLAN.md Round E10) — the INITIAL evaluation already
     * produced follow-up questions ("DB가 SPOF 아닌가요?"); until now they were only read.
     * The first [DEFENSE_QUESTION_COUNT] become the defense the learner answers alongside the
     * FOLLOWUP design (sent in its `structured_json.defense`). No new LLM call.
     */
    fun defense(sessionId: UUID): DefenseResponse {
        val session = requireSession(sessionId)
        val questions = initialSubmission(session)
            ?.let { evaluationRepository.findFirstBySubmissionIdAndIsActiveTrue(it.id!!) }
            ?.followupQuestions
            ?.let { runCatching { read(it, List::class.java).map { q -> q.toString() } }.getOrNull() }
            .orEmpty()
            .take(DEFENSE_QUESTION_COUNT)
        return DefenseResponse(
            available = questions.isNotEmpty(),
            // 면접형 타이머 모드에서는 답해야 제출할 수 있다(프론트에서 막는다). 서버는 비어 있어도 받고,
            // 평가 프롬프트에 "답하지 않음"으로 남긴다 — 시간 초과 자동 제출을 막으면 안 되기 때문.
            required = session.interviewMode,
            questions = questions,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun defensePromptSection(submission: Submission): String? {
        val entries = structuredOf(submission)["defense"] as? List<Map<String, Any?>> ?: return null
        if (entries.isEmpty()) return null
        return buildString {
            appendLine("## 설계 방어 (지난 단계 피드백의 꼬리질문에 대한 사용자 답변)")
            entries.forEach { e ->
                val answer = (e["answer"] as? String)?.trim().orEmpty()
                appendLine("- Q. ${e["question"]}")
                appendLine("  A. ${answer.ifBlank { "(답하지 않음)" }}")
            }
            appendLine("답변이 설계 근거를 구체적으로 대는지, 약점을 인정하고 대안을 제시하는지를 트레이드오프 설명·커뮤니케이션 항목에 반영하세요.")
        }
    }

    // ---- M11: incident status update ----

    /** docs/DRILLS_EXPANSION_PLAN.md M11 — the customer-facing status update drafted with the INCIDENT answer. */
    fun statusUpdatePromptSection(submission: Submission): String? {
        val draft = (structuredOf(submission)["statusUpdate"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return buildString {
            appendLine("## 고객 공지 초안 (장애 중 고객지원팀에 전달할 상태 업데이트)")
            appendLine(draft)
            appendLine()
            appendLine("커뮤니케이션 항목에서 이 공지를 평가하세요: 명료성(무엇이 영향받는지), 정확성(실제 지표·상황과 맞는지), 과장 여부(확인되지 않은 원인·복구 시점을 단정하는지), 행동 가능성(고객이 무엇을 하면 되는지).")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun structuredOf(submission: Submission): Map<String, Any?> =
        submission.structuredJson?.let { runCatching { read(it, Map::class.java) as Map<String, Any?> }.getOrNull() }.orEmpty()

    private fun judgeEstimates(fields: List<EstimationField>, estimates: Map<String, Double>): List<EstimateResult> =
        fields.map { EstimateJudge.judge(it.key, estimates[it.key], it.answer) }

    private fun initialSubmission(session: Session): Submission? =
        submissionRepository.findBySessionIdOrderByCreatedAtAsc(session.id!!).firstOrNull { it.phase == "INITIAL" }

    @Suppress("UNCHECKED_CAST")
    fun estimatesOf(submission: Submission): Map<String, Double> {
        val json = submission.structuredJson ?: return emptyMap()
        val parsed = runCatching { read(json, Map::class.java) as Map<String, Any?> }.getOrNull() ?: return emptyMap()
        val raw = parsed["estimates"] as? Map<String, Any?> ?: return emptyMap()
        return raw.mapNotNull { (k, v) -> (v as? Number)?.toDouble()?.let { k to it } }.toMap()
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

data class DefenseResponse(
    val available: Boolean,
    /** Interview-timer sessions must answer before submitting FOLLOWUP. */
    val required: Boolean,
    val questions: List<String>,
)

data class EstimationFieldView(val key: String, val label: String, val unit: String, val hint: String?)

data class EstimationResponse(
    val available: Boolean,
    /** True while the INITIAL design can still be submitted with estimates. */
    val open: Boolean,
    val fields: List<EstimationFieldView>,
    /** After the INITIAL submit, in [fields] order. */
    val results: List<EstimateResult>?,
)
