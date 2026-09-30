package com.sysdrill.backend.community

import java.time.Instant
import java.util.UUID

/** 목록 한 줄. 본문은 담지 않는다 — 잠겨 있을 때 이 타입 자체가 내려가지 않는다. */
data class WriteupSummary(
    val sessionId: UUID,
    /** 익명 공개면 null. 화면은 이 값이 없을 때 "익명"으로 표시한다. */
    val authorNickname: String?,
    val anonymous: Boolean,
    val averageScore: Int?,
    val completedAt: Instant?,
    val sharedAt: Instant?,
    val mine: Boolean,
)

data class WriteupPhase(
    val phase: String,
    /** 그 단계에 제출한 설계 답안 원문. */
    val answer: String?,
    val score: Int?,
    val topRisks: List<String>,
)

data class WriteupDetail(
    val sessionId: UUID,
    val scenarioId: UUID,
    val scenarioTitle: String,
    val domain: String,
    val authorNickname: String?,
    val anonymous: Boolean,
    val averageScore: Int?,
    val completedAt: Instant?,
    val mine: Boolean,
    val phases: List<WriteupPhase>,
    /** 포스트모템을 작성하지 않은 세션이면 전부 비어 있다(§6.2 "부분 공개 없음"의 예외가 아니라, 원래 없는 것). */
    val rootCause: String?,
    val preventionItems: List<String>,
    val mttdSeconds: Long?,
    val mttrSeconds: Long?,
)

/**
 * ADR-0041 — 목록은 **잠겨 있어도 200 으로** 내려간다. 미완료자에게 "먼저 직접
 * 풀어보세요"를 보여주는 것은 오류 처리가 아니라 이 화면의 정상 상태이기 때문이다.
 *
 * [count] 는 잠겨 있을 때도 알려준다 — 몇 편이 기다리는지는 스포일러가 아니고,
 * 오히려 ADR-0041 이 노린 "직접 풀어볼 동기" 그 자체다.
 */
data class WriteupListResponse(
    val scenarioId: UUID,
    val scenarioTitle: String,
    val locked: Boolean,
    val count: Int,
    val writeups: List<WriteupSummary>,
)

data class SessionVisibilityRequest(
    val visibility: String,
    val anonymous: Boolean = false,
)

data class SessionVisibilityResponse(
    val sessionId: UUID,
    val visibility: String,
    val anonymous: Boolean,
    val sharedAt: Instant?,
    /** 공개 토글 옆에 "이 시나리오의 다른 풀이 보기"를 놓기 위해 함께 준다. */
    val scenarioId: UUID?,
    val scenarioTitle: String?,
    /** 완료 전에는 공개 자체가 불가능하므로(409), 화면이 미리 안내할 수 있게 알려준다. */
    val completed: Boolean,
)
