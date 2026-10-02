package com.sysdrill.backend.community

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * 인용된 공개 풀이로 가는 링크. 점수는 담지 않는다 — 풀이 상세가 이미 보여주고,
 * 여기서 한 번 더 계산하면 두 화면이 어긋날 여지만 생긴다.
 * ADR-0041 을 만족하지 못하는 viewer 에게는 [locked] 만 온다.
 */
data class QuotedWriteup(
    val sessionId: UUID,
    val locked: Boolean,
    val authorNickname: String?,
)

data class DiscussionMessage(
    val id: UUID,
    val authorUserId: UUID,
    val authorNickname: String,
    /** [spoilerLocked]이면 빈 문자열 — 본문을 내려보내고 화면에서 가리면 개발자 도구로 그대로 보인다. */
    val body: String,
    val createdAt: Instant?,
    val mine: Boolean,
    val quoted: QuotedWriteup?,
    /** 내가 이미 신고했는지 — 같은 글을 두 번 신고할 수 없다. */
    val reportedByMe: Boolean,
    /** PLAN.md Round E3 — 답글이면 부모 글 id. 부모가 숨겨진 답글은 최상위로 올린다(null). */
    val parentId: UUID? = null,
    val kind: String = DiscussionKind.QUESTION.name,
    val containsSpoiler: Boolean = false,
    /** 스포일러 글인데 viewer 가 시나리오를 완료하지 않았다 — [body]가 비어 있다. */
    val spoilerLocked: Boolean = false,
    /** PLAN.md Round E32 (C14). */
    val reactions: ReactionSummary? = null,
)

/**
 * PLAN.md Round E3 — 같은 시나리오의 이전 버전 스레드(읽기 전용).
 * 토론은 버전 단위라(ADR-0040) 새 버전이 나오면 옛 글이 화면에서 사라졌다.
 * 섞어 쓰지는 않되 읽을 수는 있게 한다(ADR-0048).
 */
data class PreviousVersionThread(
    val versionNo: Int,
    val messages: List<DiscussionMessage>,
)

/**
 * 시나리오 하나의 토론 스레드.
 *
 * 비어 있을 때 화면이 초라해지지 않도록 [completedCount]/[averageScore] 를 함께
 * 준다(§6.5 "빈 스레드 대응"). 이건 스포일러가 아니라 집계 신호다 — ADR-0041 도
 * 본문 대신 집계는 열어 둘 수 있다고 적어 두었다.
 */
data class DiscussionThread(
    val scenarioId: UUID,
    val scenarioVersionId: UUID,
    val scenarioTitle: String,
    val completedCount: Long,
    val averageScore: Int?,
    /** 내가 이 시나리오를 완료했는지 — 인용이 열리는지와 "먼저 풀어보기" 안내를 가른다. */
    val completedByMe: Boolean,
    val messages: List<DiscussionMessage>,
    val currentVersionNo: Int = 1,
    /** 최신 버전 먼저. 보이는 글이 하나도 없는 버전은 빠진다. */
    val previousVersions: List<PreviousVersionThread> = emptyList(),
)

data class PostDiscussionRequest(
    @field:NotBlank
    @field:Size(max = 4000)
    val body: String,
    /** 내가 볼 수 있는 공개 풀이만 인용할 수 있다. */
    val quotedSessionId: UUID? = null,
    /** PLAN.md Round E3 — 같은(최신) 버전의 최상위 글에만 답글을 달 수 있다. */
    val parentId: UUID? = null,
    val kind: DiscussionKind = DiscussionKind.QUESTION,
    val containsSpoiler: Boolean = false,
)

data class ReportDiscussionRequest(
    @field:Size(max = 500)
    val reason: String? = null,
)

/** 관리자 검토 목록 한 줄 — 신고 많은 순. */
data class ReportedDiscussion(
    val id: UUID,
    val scenarioVersionId: UUID,
    /** PLAN.md Round E3 — 관리자 화면에서 원래 스레드로 가는 링크용. */
    val scenarioId: UUID? = null,
    val scenarioTitle: String? = null,
    val authorNickname: String,
    val body: String,
    val reportCount: Long,
    val hidden: Boolean,
    val createdAt: Instant?,
)

data class HideDiscussionRequest(val hidden: Boolean)
