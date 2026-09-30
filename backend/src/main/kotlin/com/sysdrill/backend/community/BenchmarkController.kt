package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.1 — 리포트/포스트모템 화면이 쓰는 "나 vs 커뮤니티".
 *
 * 소유자만 조회할 수 있다([SessionAccessGuard]). 집계 자체는 익명이지만, 아무 세션
 * id 로나 열 수 있으면 조직 전용 시나리오의 참여자 수·점수 분포가 조직 밖으로
 * 새기 때문이다.
 */
@RestController
class BenchmarkController(
    private val benchmarkService: BenchmarkService,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    @GetMapping("/sessions/{sessionId}/benchmark")
    fun forSession(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): BenchmarkResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return benchmarkService.forSession(sessionId)
    }
}
