package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.auth.PlatformAccessGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ADR-0040 — 시나리오별 토론.
 *
 * 전송은 폴링이다(ADR-0026 연장). 화면이 [thread] 를 주기적으로 다시 읽으므로
 * 이 응답은 스레드 **전체**를 담는다 — 증분 API 는 WebSocket 을 들일 때 함께
 * 생각할 문제이고, 시나리오 하나짜리 스레드에는 아직 필요하지 않다.
 */
@RestController
class DiscussionController(
    private val discussionService: DiscussionService,
) {

    @GetMapping("/scenarios/{scenarioId}/discussion")
    fun thread(@PathVariable scenarioId: UUID, @AuthenticatedUserId userId: UUID): DiscussionThread =
        discussionService.thread(scenarioId, userId)

    @PostMapping("/scenarios/{scenarioId}/discussion")
    fun post(
        @PathVariable scenarioId: UUID,
        @Valid @RequestBody request: PostDiscussionRequest,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<DiscussionMessage> =
        ResponseEntity.status(HttpStatus.CREATED).body(discussionService.post(scenarioId, userId, request))

    @PostMapping("/discussions/{discussionId}/reports")
    fun report(
        @PathVariable discussionId: UUID,
        @Valid @RequestBody request: ReportDiscussionRequest,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<Void> {
        discussionService.report(discussionId, userId, request)
        // 202 가 아니라 204 — 신고는 큐에 넣는 게 아니라 그 자리에서 기록된다.
        // 프런트의 apiFetch 도 "본문 없음"을 204 로만 인정한다(202 면 JSON 파싱에서 터진다).
        return ResponseEntity.noContent().build()
    }
}

/**
 * §6.5 모더레이션 — 신고 → `PLATFORM_ADMIN` 검토 → 숨김.
 * [com.sysdrill.backend.admin.AdminDashboardController] 와 같은 방식으로 역할을 검사한다.
 */
@RestController
@RequestMapping("/admin/discussions")
class AdminDiscussionController(
    private val discussionService: DiscussionService,
    private val accessGuard: PlatformAccessGuard,
) {

    @GetMapping("/reported")
    fun reported(@AuthenticatedUserId userId: UUID): List<ReportedDiscussion> {
        accessGuard.requirePlatformAdmin(userId)
        return discussionService.reported()
    }

    @PutMapping("/{discussionId}/hidden")
    fun setHidden(
        @PathVariable discussionId: UUID,
        @RequestBody request: HideDiscussionRequest,
        @AuthenticatedUserId userId: UUID,
    ): ReportedDiscussion {
        accessGuard.requirePlatformAdmin(userId)
        return discussionService.setHidden(discussionId, userId, request.hidden)
    }
}
