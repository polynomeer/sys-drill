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
    /**
     * **이 지표의** 표본 수. [BenchmarkResponse.sampleSize] 와 다를 수 있다 —
     * 완료 세션 15개 중 실제로 인시던트까지 간 세션이 3개뿐이면 점수 표본은 15,
     * MTTR 표본은 3이다. 이 값 없이 전체 표본 수만 보여주면 "15명이 풀었다"고
     * 해놓고 "표본 부족"이라 말하는 앞뒤 안 맞는 화면이 된다.
     */
    val sampleSize: Int,
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
