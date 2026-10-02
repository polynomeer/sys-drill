package com.sysdrill.backend.mission

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.simulation.InvestigationService
import com.sysdrill.backend.simulation.SimulationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ReadinessItem(val key: String, val label: String, val checked: Boolean, /** Derived from what was actually done (alerts, SLO) — not a toggle. */ val auto: Boolean)

data class ReadinessResponse(
    val items: List<ReadinessItem>,
    /** The learner went through the check (or the session predates it). */
    val confirmed: Boolean,
    /** The incident has started — readiness is what it was at "deploy" and can't change. */
    val locked: Boolean,
)

data class ConfirmReadinessRequest(val structuredLogging: Boolean, val tracing: Boolean)

/**
 * docs/OBSERVABILITY_UI_PLAN.md O7 (PLAN.md Round E26) — the pre-incident readiness check and the
 * observability-quality facts for the evaluation. Alert rules and SLO are checked from what the
 * learner actually set (O5/M3); structured logging and tracing are switches whose absence is felt
 * during the incident: no trace data, logs without fields. Nothing here scores — the facts go to
 * the Observability rubric item as a rule-based pre-check, like the rest of ARCHITECTURE §8.2.
 */
@Service
class ReadinessService(
    private val sessionRepository: SessionRepository,
    private val missionService: MissionService,
    private val simulationService: SimulationService,
    private val investigationService: InvestigationService,
) {
    fun get(sessionId: UUID): ReadinessResponse {
        val session = requireSession(sessionId)
        val state = missionService.state(session)
        val readiness = state.readiness ?: Readiness()
        return ReadinessResponse(
            items = listOf(
                ReadinessItem("alerts", "알림 규칙 설정", state.alertRules.isNotEmpty(), auto = true),
                ReadinessItem("slo", "SLO 정의", state.slo != null, auto = true),
                ReadinessItem("structuredLogging", "구조화 로그 (서비스·trace id 필드)", readiness.structuredLogging, auto = false),
                ReadinessItem("tracing", "분산 트레이싱 활성화", readiness.tracing, auto = false),
            ),
            confirmed = state.readiness?.confirmedAt != null,
            locked = incidentStarted(sessionId),
        )
    }

    @Transactional
    fun confirm(sessionId: UUID, request: ConfirmReadinessRequest): ReadinessResponse {
        val session = requireSession(sessionId)
        if (incidentStarted(sessionId)) throw ConflictException("인시던트가 이미 시작되었습니다 — 배포 시점의 준비 상태는 바꿀 수 없습니다")
        missionService.save(session, missionService.state(session).copy(readiness = Readiness(request.structuredLogging, request.tracing, Instant.now())))
        return get(sessionId)
    }

    /** Effective switches — legacy sessions (no readiness recorded) behave as before: all on. */
    fun readiness(session: Session): Readiness = missionService.state(session).readiness ?: Readiness()

    /** The INCIDENT evaluation's Observability pre-check — only what rules can back up. */
    fun observabilityPromptSection(session: Session): String? {
        val sessionId = session.id!!
        val series = simulationService.getSeries(sessionId)
        val start = series.incidentStartedAt ?: return null
        val state = missionService.state(session)
        val alerts = series.observability?.alerts.orEmpty()
        val firstAlert = alerts.firstOrNull { !it.falseAlarm }
        val looked = investigationService.list(sessionId).filter { !it.createdAt!!.isBefore(start) }
        val panels = looked.filter { it.kind == "OPEN_PANEL" }.mapNotNull { it.target }.distinct()
        val readiness = state.readiness
        return buildString {
            appendLine("## 관측 품질 사전 점검 (규칙 판정)")
            appendLine("- 알림 규칙 ${state.alertRules.size}개, SLO ${if (state.slo != null) "직접 정의" else "정의하지 않음(기본값 사용)"}")
            appendLine("- 탐지: " + (firstAlert?.let { "인시던트 ${java.time.Duration.between(start, it.firedAt).seconds}초 뒤 첫 알림" } ?: "인시던트 중 발화한 알림 없음"))
            appendLine("- 정상 구간에서 발화한 과민 알림 ${alerts.count { it.falseAlarm }}회")
            if (readiness?.confirmedAt != null) {
                appendLine("- 배포 시 구조화 로그 ${onOff(readiness.structuredLogging)}, 트레이싱 ${onOff(readiness.tracing)}")
            }
            appendLine("- 인시던트 중 열어본 화면: " + (panels.ifEmpty { listOf("없음") }.joinToString()) +
                " / 로그 검색 ${looked.count { it.kind == "QUERY_LOGS" }}회 · 트레이스 열람 ${looked.count { it.kind == "OPEN_TRACE" }}회 · 서비스 맵 노드 확인 ${looked.count { it.kind == "INSPECT_NODE" }}회")
            appendLine("관측 가능성 항목은 이 사실을 근거로 판단하세요. 회고에 쓴 원인 추정이 실제로 열어본 근거(지표·로그·트레이스)와 이어지는지도 함께 보세요.")
        }
    }

    private fun onOff(b: Boolean) = if (b) "켬" else "끔"

    private fun incidentStarted(sessionId: UUID): Boolean = simulationService.getTimeline(sessionId).isNotEmpty()

    private fun requireSession(sessionId: UUID): Session =
        sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }
}
