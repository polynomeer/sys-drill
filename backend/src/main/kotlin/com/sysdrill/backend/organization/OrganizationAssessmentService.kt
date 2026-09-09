package com.sysdrill.backend.organization

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.mail.EmailSender
import com.sysdrill.backend.reporting.ReportRepository
import com.sysdrill.backend.reporting.ReportResponse
import com.sysdrill.backend.reporting.ReportResponses
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionResponse
import com.sysdrill.backend.session.SessionService
import com.sysdrill.backend.session.SessionStatus
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Phase 5 — 채용/역량 평가 상품화 (docs/adr/0033). OrganizationInvitation's
 * email-bound token, scoped to one scenario instead of org membership.
 * Session start is unchanged — [SessionService.startSession] is called
 * verbatim, and the report is the same [com.sysdrill.backend.reporting.Report]
 * every other session produces.
 */
@Service
class OrganizationAssessmentService(
    private val assessmentRepository: OrganizationAssessmentRepository,
    private val organizationRepository: OrganizationRepository,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val reportRepository: ReportRepository,
    private val accessGuard: OrganizationAccessGuard,
    private val objectMapper: ObjectMapper,
    private val emailSender: EmailSender,
    @Value("\${sysdrill.organization.invitation-ttl-days}") private val ttlDays: Long,
    @Value("\${sysdrill.frontend-origin}") private val frontendOrigin: String,
) {

    @Transactional
    fun create(orgId: UUID, adminUserId: UUID, candidateEmail: String, scenarioId: UUID): AssessmentResponse {
        accessGuard.requireAdmin(orgId, adminUserId)
        val scenario = scenarioRepository.findById(scenarioId).orElseThrow { NotFoundException("Scenario not found: $scenarioId") }
        if (scenario.organizationId != null && scenario.organizationId != orgId) {
            throw NotFoundException("Scenario not found: $scenarioId")
        }

        val assessment = assessmentRepository.save(
            OrganizationAssessment(
                organizationId = orgId,
                scenarioId = scenarioId,
                candidateEmail = candidateEmail.lowercase(),
                token = UUID.randomUUID().toString(),
                invitedBy = adminUserId,
                expiresAt = Instant.now().plusSeconds(ttlDays * 24 * 3600),
            )
        )

        val orgName = organizationRepository.findById(orgId).map { it.name }.orElse("SysDrill")
        emailSender.send(
            to = assessment.candidateEmail,
            subject = "\"$orgName\" 역량 평가 초대",
            body = "\"$orgName\"에서 역량 평가에 초대했습니다.\n\n아래 링크에서 확인하세요:\n$frontendOrigin/organizations/assessments/${assessment.token}",
        )
        return toResponse(assessment)
    }

    fun listForOrganization(orgId: UUID, adminUserId: UUID): List<AssessmentResponse> {
        accessGuard.requireAdmin(orgId, adminUserId)
        return assessmentRepository.findByOrganizationId(orgId).map(::toResponse)
    }

    fun preview(token: String): AssessmentPreviewResponse {
        val assessment = assessmentRepository.findByToken(token) ?: throw NotFoundException("Assessment not found: $token")
        val organization = organizationRepository.findById(assessment.organizationId).orElseThrow { NotFoundException("Organization not found") }
        val scenario = scenarioRepository.findById(assessment.scenarioId).orElseThrow { NotFoundException("Scenario not found") }
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return AssessmentPreviewResponse(
            organizationName = organization.name,
            scenarioTitle = content?.title ?: scenario.domain,
            scenarioDomain = scenario.domain,
            candidateEmail = assessment.candidateEmail,
            expired = assessment.expiresAt.isBefore(Instant.now()),
            alreadyStarted = assessment.resultSessionId != null,
        )
    }

    @Transactional
    fun start(token: String, candidateUserId: UUID): SessionResponse {
        val candidate = userRepository.findById(candidateUserId).orElseThrow { NotFoundException("User not found: $candidateUserId") }
        val assessment = accessGuard.requireAssessmentRecipient(token, candidate.email)
        if (assessment.resultSessionId != null) throw ConflictException("Assessment $token has already been started")
        if (assessment.expiresAt.isBefore(Instant.now())) throw ConflictException("Assessment $token has expired")

        val session = sessionService.startSession(candidateUserId, assessment.scenarioId)
        assessment.resultSessionId = session.id
        assessmentRepository.save(assessment)
        return toSessionResponse(session, candidateUserId)
    }

    fun getReport(orgId: UUID, adminUserId: UUID, assessmentId: UUID): ReportResponse {
        accessGuard.requireAdmin(orgId, adminUserId)
        val assessment = assessmentRepository.findById(assessmentId).orElseThrow { NotFoundException("Assessment not found: $assessmentId") }
        if (assessment.organizationId != orgId) throw NotFoundException("Assessment not found: $assessmentId")
        val sessionId = assessment.resultSessionId ?: throw NotFoundException("Assessment $assessmentId has not been started yet")
        val report = reportRepository.findFirstBySessionIdOrderByVersionDesc(sessionId)
            ?: throw NotFoundException("No report for assessment $assessmentId yet — the candidate may not have finished")
        return ReportResponses.toResponse(report, objectMapper)
    }

    private fun toResponse(assessment: OrganizationAssessment): AssessmentResponse {
        val scenario = scenarioRepository.findById(assessment.scenarioId).orElse(null)
        val title = scenario?.let { contentItemRepository.findById(it.contentId).orElse(null)?.title ?: it.domain } ?: "알 수 없음"
        val session = assessment.resultSessionId?.let { sessionRepository.findById(it).orElse(null) }
        val status = when {
            session == null -> AssessmentStatus.NOT_STARTED
            session.status == SessionStatus.COMPLETED -> AssessmentStatus.COMPLETED
            else -> AssessmentStatus.IN_PROGRESS
        }
        return AssessmentResponse(
            id = assessment.id!!,
            organizationId = assessment.organizationId,
            scenarioId = assessment.scenarioId,
            scenarioTitle = title,
            candidateEmail = assessment.candidateEmail,
            token = assessment.token,
            status = status,
            resultSessionId = assessment.resultSessionId,
            expiresAt = assessment.expiresAt,
            createdAt = assessment.createdAt,
        )
    }

    private fun toSessionResponse(session: Session, callerId: UUID): SessionResponse = SessionResponse.from(
        session,
        sessionService.getCurrentStepPrompt(session),
        sessionService.getScenarioDomain(session),
        sessionService.getPhaseDeadline(session),
        callerId,
    )
}
