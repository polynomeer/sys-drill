package com.sysdrill.backend.learning

import com.sysdrill.backend.auth.AuthenticatedUserId
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
class LearningController(
    private val learningQuizService: LearningQuizService,
    private val learningService: LearningService,
    private val learningPathService: LearningPathService,
    private val learningLabService: LearningLabService,
) {

    /** 역량 카테고리 6개와 그 아래 개념 요약. 내 약점이 많은 카테고리가 먼저 온다. */
    @GetMapping("/concepts")
    fun concepts(@AuthenticatedUserId userId: UUID): List<LearningCategory> =
        learningService.categories(userId)

    /** docs/LEARNING_COMMUNITY_PLAN.md §5.3 — 내 약점에서 파생한 학습 경로. 저장하지 않는다. */
    /** PLAN.md Round E18 (L7) — the knowledge map. */
    @GetMapping("/map")
    fun map(@AuthenticatedUserId userId: UUID): KnowledgeMap = learningService.map(userId)

    @GetMapping("/path")
    fun path(@AuthenticatedUserId userId: UUID): LearningPath = learningPathService.forUser(userId)

    /** docs/CODECRAFTERS_BENCHMARK.md §3.6 — self-check question generated from concept data (see [LearningQuizService]). */
    @GetMapping("/concepts/{riskKey}/quiz")
    fun quiz(@PathVariable riskKey: String): ConceptQuiz = learningQuizService.quiz(riskKey)

    /** docs/LEARNING_EXPANSION_PLAN.md L5·L6 (PLAN.md Round E9) — every lab. */
    @GetMapping("/labs")
    fun labs(): List<LabSummary> = learningLabService.list()

    /** L6 — a Capacity Lab problem, formulas withheld. */
    @GetMapping("/labs/{slug}/capacity")
    fun capacityLab(@PathVariable slug: String): CapacityLabView = learningLabService.capacity(slug)

    /** L6 — judge the learner's estimates on order of magnitude (same rule as the Drill's M2). Nothing is stored. */
    @PostMapping("/labs/{slug}/capacity/check")
    fun checkCapacity(@PathVariable slug: String, @RequestBody request: CapacityCheckRequest): CapacityCheckResult =
        learningLabService.checkCapacity(slug, request)

    /** L5 (ADR-0047) — an engine lab's knobs (with the incident's defaults), watched and predicted metrics. */
    @GetMapping("/labs/{slug}/engine")
    fun engineLab(@PathVariable slug: String): EngineLabView = learningLabService.engine(slug)

    /** L5 — run the domain's formula with these knob values, no session. Nothing is stored. */
    @PostMapping("/labs/{slug}/engine/run")
    fun runEngineLab(@PathVariable slug: String, @RequestBody request: EngineRunRequest) =
        learningLabService.run(slug, request)

    @GetMapping("/concepts/{riskKey}")
    fun concept(@PathVariable riskKey: String, @AuthenticatedUserId userId: UUID): LearningConceptDetail =
        learningService.detail(riskKey, userId)
}
