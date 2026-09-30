package com.sysdrill.backend.community

import com.sysdrill.backend.identity.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

/** ADR-0042 — 점수 구간별 티어. 절대 순위보다 이걸 앞세운다. */
enum class DrillTier(val label: String, val minScore: Int) {
    TRAINEE("훈련생", 0),
    OPERATOR("운영자", 100),
    RESPONDER("대응자", 300),
    ARCHITECT("설계자", 500),
    PRINCIPAL("수석", 700);

    companion object {
        fun of(score: Int): DrillTier = entries.last { score >= it.minScore }
    }
}

enum class RankingBoard { OVERALL, DOMAIN, RECENT }

data class RankingEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val tier: String,
    val tierLabel: String,
    /** 이 항목이 요청자 본인인지 — 보드에서 자기 위치를 찾기 위해. */
    val isMe: Boolean,
)

data class RankingBoardResponse(
    val board: RankingBoard,
    /** DOMAIN 보드에서만 채워진다. */
    val domain: String?,
    val entries: List<RankingEntry>,
    /** 이 보드에 집계된 총 인원(노출 거부 제외). */
    val participantCount: Int,
)

data class MyRankingResponse(
    val score: Int,
    val tier: String,
    val tierLabel: String,
    /** "상위 N%" — 참여자 중 나보다 점수가 높은 비율. 참여자가 없으면 null. */
    val topPercent: Int?,
    val rank: Int?,
    val participantCount: Int,
    /** 다음 티어까지 남은 점수. 최고 티어면 null. */
    val pointsToNextTier: Int?,
    val nextTierLabel: String?,
    /** ADR-0042 — 계산 근거를 항상 함께 준다. 설명할 수 없는 점수는 신뢰받지 못한다. */
    val breakdown: List<DomainBest>,
    val optedOut: Boolean,
)

/**
 * ADR-0042 — 랭킹 보드.
 *
 * **티어와 백분위를 앞세우고 절대 순위는 부차적으로** 둔다. "3등"은 소수에게만
 * 의미가 있지만 "상위 30% · 대응자"는 모두에게 다음 목표를 준다.
 *
 * 누적 점수만 보면 초기 사용자가 영구히 유리하므로 [RankingBoard.RECENT]
 * (최근 30일 상승폭) 보드를 함께 둔다.
 */
@Service
class RankingService(
    private val drillScoreService: DrillScoreService,
    private val userRepository: UserRepository,
) {

    fun board(board: RankingBoard, domain: String?, viewerId: UUID, limit: Int = 20): RankingBoardResponse {
        val scores = drillScoreService.allUsers()
        val participants = visibleUsers(scores.keys)

        val ranked: List<Pair<UUID, Int>> = when (board) {
            RankingBoard.OVERALL -> participants.mapNotNull { id -> scores[id]?.let { id to it.total } }
            RankingBoard.DOMAIN -> participants.mapNotNull { id ->
                scores[id]?.domainBests?.firstOrNull { it.domain == domain }?.let { id to it.bestScore }
            }
            RankingBoard.RECENT -> {
                val cutoff = Instant.now().minus(RECENT_WINDOW)
                val past = drillScoreService.allUsers(completedBefore = cutoff)
                participants.mapNotNull { id ->
                    val gain = (scores[id]?.total ?: 0) - (past[id]?.total ?: 0)
                    if (gain > 0) id to gain else null
                }
            }
        }.sortedWith(compareByDescending<Pair<UUID, Int>> { it.second }.thenBy { it.first.toString() })

        val nicknames = userRepository.findAllById(ranked.map { it.first }).associate { it.id to it.nickname }
        val entries = ranked.take(limit).mapIndexed { index, (userId, score) ->
            RankingEntry(
                rank = index + 1,
                nickname = nicknames[userId] ?: "알 수 없음",
                score = score,
                tier = DrillTier.of(score).name,
                tierLabel = DrillTier.of(score).label,
                isMe = userId == viewerId,
            )
        }
        return RankingBoardResponse(
            board = board,
            domain = domain.takeIf { board == RankingBoard.DOMAIN },
            entries = entries,
            participantCount = ranked.size,
        )
    }

    fun me(userId: UUID): MyRankingResponse {
        val scores = drillScoreService.allUsers()
        val mine = scores[userId] ?: DrillScore(userId, 0, emptyList())
        val optedOut = userRepository.findById(userId).map { it.rankingOptOut }.orElse(false)

        val participants = visibleUsers(scores.keys)
        val totals = participants.mapNotNull { scores[it]?.total }.sortedDescending()
        val tier = DrillTier.of(mine.total)
        val next = DrillTier.entries.firstOrNull { it.minScore > mine.total }

        // 노출을 거부했어도 내 점수·티어는 본다 — 감추는 것은 남에게 보이는 쪽뿐이다.
        val rank = if (optedOut || totals.isEmpty()) null else totals.indexOf(mine.total).takeIf { it >= 0 }?.plus(1)
        val topPercent = if (optedOut || totals.isEmpty()) null else {
            (totals.count { it > mine.total } * 100.0 / totals.size).roundToInt()
        }

        return MyRankingResponse(
            score = mine.total,
            tier = tier.name,
            tierLabel = tier.label,
            topPercent = topPercent,
            rank = rank,
            participantCount = totals.size,
            pointsToNextTier = next?.let { it.minScore - mine.total },
            nextTierLabel = next?.label,
            breakdown = mine.domainBests,
            optedOut = optedOut,
        )
    }

    fun setOptOut(userId: UUID, optOut: Boolean) {
        userRepository.findById(userId).ifPresent { user ->
            user.rankingOptOut = optOut
            userRepository.save(user)
        }
    }

    /** 노출을 거부하지 않은 사용자만 보드에 들어간다(§7 프라이버시). */
    private fun visibleUsers(candidates: Collection<UUID>): List<UUID> =
        userRepository.findAllById(candidates).filterNot { it.rankingOptOut }.mapNotNull { it.id }

    companion object {
        private val RECENT_WINDOW: Duration = Duration.ofDays(30)
    }
}
