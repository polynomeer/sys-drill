package com.sysdrill.backend.community

import java.util.UUID

/**
 * 하나의 지표에 대한 "나 vs 커뮤니티".
 *
 * [distribution] 과 [topPercent] 는 표본이 [BenchmarkResponse.minSampleSize] 에
 * 못 미치면 **둘 다 null** 이다 — 3명이 푼 시나리오에서 "상위 33%"는 정보가
 * 아니라 노이즈다(docs/LEARNING_COMMUNITY_PLAN.md §6.1).
 */
data class BenchmarkMetric(
    /** 이 세션의 값. 인시던트에 도달하지 않은 세션의 MTTD/MTTR 처럼 해당 없으면 null. */
    val mine: Long?,
    val distribution: MetricDistribution?,
    /**
     * "상위 N%" — 나보다 잘한 표본의 비율(반올림). 지표마다 '잘함'의 방향이 달라
     * ([higherIsBetter]) 서버가 계산해 내려준다. [mine] 이 null 이면 null.
     */
    val topPercent: Int?,
    /** 점수는 높을수록, MTTD/MTTR 은 낮을수록 좋다 — 프론트가 화살표 방향을 정하는 데 쓴다. */
    val higherIsBetter: Boolean,
)

/** 최근접 순위법(nearest-rank) 백분위. 보간하지 않으므로 값은 항상 실제 표본 중 하나다. */
data class MetricDistribution(val p50: Long, val p90: Long)

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.1 — 같은 `scenarioVersionId` 를 완료한
 * 세션들에 대한 집계. 저장하지 않고 읽기 시점에 계산한다(ADR-0011).
 */
data class BenchmarkResponse(
    val scenarioVersionId: UUID,
    /** 집계에 쓰인 완료 세션 수 (내 세션 포함). */
    val sampleSize: Int,
    /** 이 값 미만이면 분포를 감춘다. */
    val minSampleSize: Int,
    val score: BenchmarkMetric,
    val mttdSeconds: BenchmarkMetric,
    val mttrSeconds: BenchmarkMetric,
)
