package com.sysdrill.backend.community

import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

/** 한 도메인에서의 최고 성적 — DrillScore 의 계산 단위이자 사용자에게 보여줄 근거. */
data class DomainBest(
    val domain: String,
    val title: String,
    val difficulty: String,
    val bestScore: Int,
    val weight: Double,
    /** bestScore × weight (반올림). */
    val points: Int,
)

data class DrillScore(
    val userId: UUID,
    val total: Int,
    val domainBests: List<DomainBest>,
)

/**
 * ADR-0042 — DrillScore.
 *
 * ```
 * DrillScore = Σ (공식 도메인 d 의 최고 세션 점수) × 난이도가중(d)
 * ```
 *
 * 반복 파밍 방지가 부가 규칙이 아니라 **이 수식 자체**에서 나온다:
 * 도메인별 최고점만 합산하므로 반복에 보상이 없고, 도메인 합이라 안 해 본
 * 도메인은 0 점이며(넓이가 자동 보상), 공식 시나리오만 집계하므로 자기가 만든
 * 쉬운 시나리오로 점수를 찍어낼 수 없다.
 *
 * 저장하지 않고 읽기 시점에 계산한다(ADR-0011). 사용자 수가 늘어 비싸지면
 * ADR-0011 이 열어 둔 길(명시적 캐시 + 입력 연동 무효화)을 따르고, 점수를
 * 세션 저장 경로에 끼워 넣지 않는다.
 */
@Service
class DrillScoreService(
    private val sessionRepository: SessionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
) {

    /**
     * 모든 사용자의 점수. [completedBefore] 를 주면 그 시점까지의 완료 세션만 세어
     * 과거 점수를 재구성한다 — "최근 30일 상승폭" 보드가 이걸 쓴다.
     */
    fun allUsers(completedBefore: Instant? = null): Map<UUID, DrillScore> {
        val official = scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null }
        if (official.isEmpty()) return emptyMap()

        val scenarioById = official.mapNotNull { s -> s.id?.let { it to s } }.toMap()
        val contentById = contentItemRepository.findAllById(official.map { it.contentId }).associateBy { it.id }
        val scenarioIdByVersionId = scenarioVersionRepository.findByScenarioIdIn(scenarioById.keys)
            .mapNotNull { v -> v.id?.let { it to v.scenarioId } }
            .toMap()

        // (userId, domain) -> 최고 세션 평균 점수
        val bestByUserDomain = mutableMapOf<Pair<UUID, String>, Int>()
        sessionRepository.completedSessionScores().forEach { row ->
            val average = row.getAverageScore() ?: return@forEach
            if (completedBefore != null) {
                val completedAt = row.getCompletedAt() ?: return@forEach
                if (!completedAt.isBefore(completedBefore)) return@forEach
            }
            val scenarioId = scenarioIdByVersionId[row.getScenarioVersionId()] ?: return@forEach
            val domain = scenarioById[scenarioId]?.domain ?: return@forEach
            val key = row.getUserId() to domain
            val score = average.roundToInt()
            if (score > (bestByUserDomain[key] ?: Int.MIN_VALUE)) bestByUserDomain[key] = score
        }

        // 도메인별 난이도와 제목 — 같은 도메인의 공식 시나리오는 하나라는 전제.
        val metaByDomain = official.associate { scenario ->
            val content = contentById[scenario.contentId]
            scenario.domain to (content?.title.orEmpty() to (content?.difficulty ?: DEFAULT_DIFFICULTY))
        }

        return bestByUserDomain.entries
            .groupBy({ it.key.first }, { it.key.second to it.value })
            .mapValues { (userId, domainScores) ->
                val bests = domainScores.mapNotNull { (domain, best) ->
                    val (title, difficulty) = metaByDomain[domain] ?: return@mapNotNull null
                    val weight = weightOf(difficulty)
                    DomainBest(
                        domain = domain,
                        title = title.ifBlank { domain },
                        difficulty = difficulty,
                        bestScore = best,
                        weight = weight,
                        points = (best * weight).roundToInt(),
                    )
                }.sortedByDescending { it.points }
                DrillScore(userId = userId, total = bests.sumOf { it.points }, domainBests = bests)
            }
    }

    fun forUser(userId: UUID): DrillScore =
        allUsers()[userId] ?: DrillScore(userId = userId, total = 0, domainBests = emptyList())

    companion object {
        private const val DEFAULT_DIFFICULTY = "MEDIUM"

        /**
         * 난이도 가중치. 쉬운 것만 반복하는 전략의 상한을 낮추는 장치이자, 점수를
         * 올리는 유일한 길을 "더 어려운 도메인으로 가는 것"으로 만드는 장치다.
         * 설정이 아니라 상수인 이유는 값이 바뀌면 모든 사용자의 점수가 한꺼번에
         * 바뀌기 때문 — 바꿀 때는 의도적으로 바꿔야 한다(ADR-0042).
         */
        private val WEIGHTS = mapOf("EASY" to 1.0, "MEDIUM" to 1.2, "HARD" to 1.5)

        fun weightOf(difficulty: String?): Double = WEIGHTS[difficulty?.uppercase()] ?: WEIGHTS.getValue("MEDIUM")
    }
}
