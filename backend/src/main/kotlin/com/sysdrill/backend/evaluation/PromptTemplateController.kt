package com.sysdrill.backend.evaluation

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.auth.PlatformAccessGuard
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CreatePromptTemplateRequest(
    @field:NotBlank val purpose: String,
    @field:NotBlank val templateBody: String,
)

data class PromptTemplateResponse(
    val id: UUID,
    val purpose: String,
    val version: Int,
    val active: Boolean,
    val createdAt: Instant?,
) {
    companion object {
        fun from(t: PromptTemplate) = PromptTemplateResponse(t.id!!, t.purpose, t.version, t.active, t.createdAt)
    }
}

/** PLAN.md step 35 — platform-admin-only (see [PlatformAccessGuard]); previously entirely unauthenticated. */
@RestController
@RequestMapping("/admin/prompt-templates")
class PromptTemplateController(
    private val service: PromptTemplateService,
    private val platformAccessGuard: PlatformAccessGuard,
) {

    @PostMapping
    fun create(
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: CreatePromptTemplateRequest,
    ): ResponseEntity<PromptTemplateResponse> {
        platformAccessGuard.requirePlatformAdmin(userId)
        val created = service.create(request.purpose, request.templateBody)
        return ResponseEntity.status(HttpStatus.CREATED).body(PromptTemplateResponse.from(created))
    }

    @GetMapping
    fun list(@AuthenticatedUserId userId: UUID, @RequestParam purpose: String): List<PromptTemplateResponse> {
        platformAccessGuard.requirePlatformAdmin(userId)
        return service.listVersions(purpose).map(PromptTemplateResponse::from)
    }

    @PostMapping("/{id}/activate")
    fun activate(@AuthenticatedUserId userId: UUID, @PathVariable id: UUID): PromptTemplateResponse {
        platformAccessGuard.requirePlatformAdmin(userId)
        return PromptTemplateResponse.from(service.activate(id))
    }
}
