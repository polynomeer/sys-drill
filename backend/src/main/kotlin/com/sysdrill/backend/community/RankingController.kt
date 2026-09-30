package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.BadRequestException
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class RankingVisibilityRequest(val optOut: Boolean)

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.6 — Drill Score 와 랭킹.
 *
 * 로그인한 사용자만 본다. 보드에 닉네임이 노출되는 이상 익명 공개 URL로는
 * 열지 않는다(§7) — 공개 프로필(`/certifications/{userId}`)은 본인이 직접
 * 공유한 것이지만 랭킹은 내가 올린 적 없는 곳에 내 이름이 실리는 것이다.
 */
@RestController
class RankingController(
    private val rankingService: RankingService,
) {

    // board 는 String 으로 받는다 — Spring 의 기본 enum 변환은 대소문자를 가려서
    // `?board=overall` 이 400 이 되는데, 쿼리 파라미터에 그 구분은 의미가 없다.
    @GetMapping("/community/rankings")
    fun board(
        @RequestParam(defaultValue = "overall") board: String,
        @RequestParam(required = false) domain: String?,
        @AuthenticatedUserId userId: UUID,
    ): RankingBoardResponse {
        val selected = RankingBoard.entries.firstOrNull { it.name.equals(board, ignoreCase = true) }
            ?: throw BadRequestException("Unknown ranking board: $board")
        return rankingService.board(selected, domain, userId)
    }

    @GetMapping("/community/rankings/me")
    fun me(@AuthenticatedUserId userId: UUID): MyRankingResponse = rankingService.me(userId)

    @PutMapping("/community/rankings/visibility")
    fun setVisibility(
        @RequestBody request: RankingVisibilityRequest,
        @AuthenticatedUserId userId: UUID,
    ): MyRankingResponse {
        rankingService.setOptOut(userId, request.optOut)
        return rankingService.me(userId)
    }
}
