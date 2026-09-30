package com.sysdrill.backend.learning

import com.sysdrill.backend.auth.AuthenticatedUserId
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.6 — 개념 라이브러리 조회.
 *
 * `/scenarios` 와 달리 **인증을 요구한다.** 개념 본문 자체는 공개해도 되는
 * 참고 자료지만, 이 슬라이스가 만드는 가치는 "내가 몇 번 놓친 개념인가" 배지와
 * 약점 순 정렬이라 사용자 없이는 반쪽짜리 화면이 된다. 비로그인 공개가 필요해지면
 * (마케팅·SEO 용도) 그때 별도 경로로 여는 편이 낫다.
 */
@RestController
@RequestMapping("/learning")
class LearningController(private val learningService: LearningService) {

    /** 역량 카테고리 6개와 그 아래 개념 요약. 내 약점이 많은 카테고리가 먼저 온다. */
    @GetMapping("/concepts")
    fun concepts(@AuthenticatedUserId userId: UUID): List<LearningCategory> =
        learningService.categories(userId)

    @GetMapping("/concepts/{riskKey}")
    fun concept(@PathVariable riskKey: String, @AuthenticatedUserId userId: UUID): LearningConceptDetail =
        learningService.detail(riskKey, userId)
}
