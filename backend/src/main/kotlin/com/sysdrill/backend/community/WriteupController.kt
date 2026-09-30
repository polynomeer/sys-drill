package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 / [ADR-0041] — 풀이 공유.
 *
 * 목록·상세 모두 로그인을 요구한다. 열람 자격이 "이 시나리오를 완료했는가"인
 * 이상, 호출자 신원 없이는 아예 판정할 수 없다.
 */
@RestController
class WriteupController(
    private val writeupService: WriteupService,
) {

    /** 내 세션의 공개 설정 — 리포트 화면의 공개 토글이 읽고 쓴다. */
    @GetMapping("/sessions/{sessionId}/visibility")
    fun getVisibility(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): SessionVisibilityResponse =
        writeupService.visibility(sessionId, userId)

    @PutMapping("/sessions/{sessionId}/visibility")
    fun setVisibility(
        @PathVariable sessionId: UUID,
        @RequestBody request: SessionVisibilityRequest,
        @AuthenticatedUserId userId: UUID,
    ): SessionVisibilityResponse = writeupService.setVisibility(sessionId, userId, request)

    /** 잠겨 있어도 200 — 이유는 [WriteupListResponse] 주석 참고. */
    @GetMapping("/scenarios/{scenarioId}/writeups")
    fun listForScenario(@PathVariable scenarioId: UUID, @AuthenticatedUserId userId: UUID): WriteupListResponse =
        writeupService.listForScenario(scenarioId, userId)

    @GetMapping("/writeups/{sessionId}")
    fun detail(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): WriteupDetail =
        writeupService.detail(sessionId, userId)
}
