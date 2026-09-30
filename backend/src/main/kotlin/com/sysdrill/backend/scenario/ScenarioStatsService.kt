package com.sysdrill.backend.scenario

import com.sysdrill.backend.session.SessionRepository
import java.util.UUID
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

/** 한 시나리오의 "몇 명이 끝냈고 평균 몇 점인가". 표본이 없으면 둘 다 0/null. */
data class ScenarioStats(val completedCount: Long, val averageScore: Int?)

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 시나리오 목록의 **난이도 신호**.
 *
 * 별점을 두지 않는 이유가 여기 있다: 표본이 적을 때 별점은 왜곡이 크고,
 * "실제로 푼 사람들의 평균 점수"가 훨씬 객관적인 난이도 지표다.
 *
 * 저장하지 않고 읽기 시점에 집계한다(ADR-0011). 한 시나리오의 **모든 버전**을
 * 합산한다 — 벤치마크(§6.1)가 버전을 고정하는 것과 반대인데, 목적이 다르기
 * 때문이다: 벤치마크는 같은 문제를 푼 사람끼리 비교해야 하고, 여기서는
 * "이 시나리오가 얼마나 어렵고 인기 있는가"를 보여주면 된다.
 */
@Service
class ScenarioStatsService(
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val sessionRepository: SessionRepository,
) {

    fun byScenarioId(scenarioIds: Collection<UUID>): Map<UUID, ScenarioStats> {
        if (scenarioIds.isEmpty()) return emptyMap()

        val scenarioIdByVersionId = scenarioVersionRepository.findByScenarioIdIn(scenarioIds)
            .mapNotNull { version -> version.id?.let { it to version.scenarioId } }
            .toMap()
        if (scenarioIdByVersionId.isEmpty()) return emptyMap()

        val perScenario = mutableMapOf<UUID, MutableList<Pair<Long, Double?>>>()
        sessionRepository.statsByScenarioVersionIds(scenarioIdByVersionId.keys).forEach { row ->
            val scenarioId = scenarioIdByVersionId[row.getScenarioVersionId()] ?: return@forEach
            perScenario.getOrPut(scenarioId) { mutableListOf() }
                .add(row.getCompletedCount() to row.getAverageScore())
        }

        return perScenario.mapValues { (_, rows) ->
            val total = rows.sumOf { it.first }
            // 버전별 평균을 완료 수로 가중해 합친다 — 단순 평균을 내면 표본 1개짜리
            // 옛 버전이 최신 버전과 같은 무게를 갖는다.
            val weighted = rows.mapNotNull { (count, avg) -> avg?.let { count to it } }
            val average = if (total > 0 && weighted.isNotEmpty()) {
                weighted.sumOf { (count, avg) -> count * avg } / weighted.sumOf { it.first }
            } else null
            ScenarioStats(completedCount = total, averageScore = average?.roundToInt())
        }
    }
}
