package com.sysdrill.backend.postmortem

import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions/{sessionId}/postmortem")
class PostmortemController(private val postmortemService: PostmortemService) {

    @GetMapping
    fun get(@PathVariable sessionId: UUID): PostmortemResponse = postmortemService.get(sessionId)

    @PutMapping
    fun save(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SavePostmortemRequest,
    ): PostmortemResponse = postmortemService.save(sessionId, request)
}
