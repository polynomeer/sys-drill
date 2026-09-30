package com.sysdrill.backend.community

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.postmortem.mttdMttr
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.SimulationService
import com.sysdrill.backend.submission.SubmissionRepository
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.1 (슬라이스 1) — 세션 리포트/포스트모템 옆에
 * 붙는 "나 vs 커뮤니티".
 *
 * **새 테이블이 없다.** 점수는 `CertificationService` 가 쓰는 것과 같은
 * (제출 → 활성 평가 → 세션 평균) 경로로, MTTD/MTTR 은 포스트모템과 같은
 * [mttdMttr] 로 계산한다 — 같은 화면에 "내 MTTR 1분 55초"와 "커뮤니티 중앙값"이
 * 나란히 놓이므로 두 숫자가 다른 정의로 계산되면 안 된다.
 *
 * 세션 단위로 진입하는 이유(`/sessions/{id}/benchmark`): 비교는 내 결과 옆에서만
 * 의미가 있고, 소유자 검사([com.sysdrill.backend.session.SessionAccessGuard])를
 * 그대로 재사용할 수 있어 조직 전용 시나리오의 집계가 외부로 새지 않는다.
 * 시나리오 단위 공개 엔드포인트는 마켓플레이스 슬라이스에서 별도로 다룬다.
 */
@Service
class BenchmarkService(
    private val sessionRepository: SessionRepository,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    private val simulationService: SimulationService,
    @Value("\${sysdrill.community.benchmark.min-sample-size:5}") private val minSampleSize: Int,
) {

    fun forSession(sessionId: UUID): BenchmarkResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }

        // 같은 시나리오 *버전* 의 완료 세션만. 버전이 다르면 인시던트나 루브릭이
        // 다를 수 있어 애초에 같은 문제를 푼 것이 아니다.
        val peers = sessionRepository.findByScenarioVersionIdAndStatus(
            session.scenarioVersionId,
            SessionStatus.COMPLETED,
        )
        val peerIds = peers.mapNotNull { it.id }

        val scoreBySessionId = averageScoreBySessionId(peerIds)
        val mttdBySessionId = mutableMapOf<UUID, Long>()
        val mttrBySessionId = mutableMapOf<UUID, Long>()
        peerIds.forEach { id ->
            val (mttd, mttr) = mttdMttr(simulationService.getTimeline(id))
            mttd?.let { mttdBySessionId[id] = it }
            mttr?.let { mttrBySessionId[id] = it }
        }

        return BenchmarkResponse(
            scenarioVersionId = session.scenarioVersionId,
            sampleSize = peerIds.size,
            minSampleSize = minSampleSize,
            score = metric(scoreBySessionId[sessionId], scoreBySessionId.values, higherIsBetter = true),
            mttdSeconds = metric(mttdBySessionId[sessionId], mttdBySessionId.values, higherIsBetter = false),
            mttrSeconds = metric(mttrBySessionId[sessionId], mttrBySessionId.values, higherIsBetter = false),
        )
    }

    /** `CertificationService.averageScore` 와 같은 계산을 여러 세션에 대해 한 번에. */
    private fun averageScoreBySessionId(sessionIds: List<UUID>): Map<UUID, Long> {
        if (sessionIds.isEmpty()) return emptyMap()
        val submissions = submissionRepository.findBySessionIdIn(sessionIds)
        val scoreBySubmissionId = evaluationRepository
            .findBySubmissionIdInAndIsActiveTrue(submissions.mapNotNull { it.id })
            .associate { it.submissionId to it.totalScore }
        return submissions.groupBy { it.sessionId }.mapNotNull { (sessionId, sessionSubmissions) ->
            val scores = sessionSubmissions.mapNotNull { scoreBySubmissionId[it.id] }
            if (scores.isEmpty()) null else sessionId to (scores.sum() / scores.size).toLong()
        }.toMap()
    }

    /**
     * 표본이 [minSampleSize] 에 못 미치면 분포와 순위를 **둘 다** 감춘다 — 내 값만
     * 돌려주고 비교는 하지 않는다.
     */
    private fun metric(mine: Long?, all: Collection<Long>, higherIsBetter: Boolean): BenchmarkMetric {
        if (all.size < minSampleSize) {
            return BenchmarkMetric(mine = mine, distribution = null, topPercent = null, higherIsBetter = higherIsBetter)
        }
        val sorted = all.sorted()
        // p50/p90 은 지표와 무관하게 **오름차순 백분위**다. 낮을수록 좋은 MTTR 에서
        // p90 을 "상위 10%"(=빠른 쪽)로 뒤집어 계산하면 p90 이라는 이름과 값이
        // 어긋나 읽는 사람을 속인다. 방향 해석은 [BenchmarkMetric.higherIsBetter] 로
        // 프론트에 넘기고, 여기서는 표준 정의를 지킨다.
        val distribution = MetricDistribution(p50 = percentile(sorted, 50), p90 = percentile(sorted, 90))
        val topPercent = mine?.let {
            val better = if (higherIsBetter) sorted.count { v -> v > it } else sorted.count { v -> v < it }
            (better * 100.0 / sorted.size).roundToInt()
        }
        return BenchmarkMetric(mine = mine, distribution = distribution, topPercent = topPercent, higherIsBetter = higherIsBetter)
    }

    companion object {
        /** 최근접 순위법. 보간하지 않으므로 결과는 항상 실제 표본 중 하나다. */
        internal fun percentile(sorted: List<Long>, p: Int): Long {
            require(sorted.isNotEmpty()) { "percentile of an empty sample" }
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
