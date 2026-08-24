package com.sysdrill.backend.session

import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions")
class SessionController(private val sessionService: SessionService) {

    @PostMapping
    fun start(@Valid @RequestBody request: StartSessionRequest): ResponseEntity<SessionResponse> {
        val session = sessionService.startSession(request.userId, request.scenarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): SessionResponse = toResponse(sessionService.getSession(id))

    @PostMapping("/{id}/submissions")
    fun submit(
        @PathVariable id: UUID,
        @RequestBody request: SubmitAnswerRequest,
    ): ResponseEntity<SubmissionResponse> {
        val submission = sessionService.submit(id, request.rawText, request.structuredJson, request.clientRequestId)
        return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.from(submission))
    }

    @PostMapping("/{id}/advance")
    fun advance(@PathVariable id: UUID): SessionResponse = toResponse(sessionService.advance(id))

    private fun toResponse(session: Session): SessionResponse =
        SessionResponse.from(session, sessionService.getCurrentStepPrompt(session))
}
