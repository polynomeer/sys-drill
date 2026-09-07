package com.sysdrill.backend.certification

import com.sysdrill.backend.auth.AuthenticatedUserId
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Phase 5 — "SysDrill Certified Incident Responder" (docs/adr/0032). GET /certifications/{userId} is a public verification page (no auth) — anyone with the link can check someone's certification status. */
@RestController
@RequestMapping("/certifications")
class CertificationController(
    private val certificationService: CertificationService,
) {

    @GetMapping("/me")
    fun me(@AuthenticatedUserId userId: UUID): CertificationStatusResponse = certificationService.status(userId)

    @GetMapping("/{userId}")
    fun get(@PathVariable userId: UUID): CertificationStatusResponse = certificationService.status(userId)
}
