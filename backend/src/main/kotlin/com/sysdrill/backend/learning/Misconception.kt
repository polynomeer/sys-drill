package com.sysdrill.backend.learning

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.simulation.SimulationActionType as A
import com.sysdrill.backend.simulation.SimulationService
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** One misconception rule: a first incident action that looks right but isn't, seen often enough. */
private data class MisconceptionRule(
    val key: String,
    /** Null = across every domain. */
    val domain: String?,
    val actions: Set<A>,
    val belief: String,
    val correction: String,
)

data class MisconceptionCard(
    val key: String,
    val belief: String,
    val correction: String,
    val evidence: String,
    val labSlug: String?,
    val failureDomain: String?,
)

/**
 * docs/LEARNING_EXPANSION_PLAN.md L10 (PLAN.md Round E29) — misconceptions read from what I did,
 * not from what I wrote: the same plausible-but-wrong first incident action in [THRESHOLD]+
 * completed sessions. Rules are code (config as data, reviewed); nothing is stored.
 */
@Service
class MisconceptionService(
    private val conceptMasteryService: ConceptMasteryService,
    private val simulationService: SimulationService,
    private val labRepository: LearningLabRepository,
) {
    fun forUser(userId: UUID): List<MisconceptionCard> {
        // Completed sessions with their first incident action (the timeline already drops sandbox actions).
        val firsts = conceptMasteryService.history(userId).mapNotNull { run ->
            val first = simulationService.getTimeline(run.session.id!!).drop(1).firstOrNull()?.actionType ?: return@mapNotNull null
            runCatching { A.valueOf(first) }.getOrNull()?.let { run.domain to it }
        }
        val labByDomain = labRepository.findAllByOrderByDisplayOrderAsc().filter { it.kind == "ENGINE" && it.domain != null }.associate { it.domain!! to it.slug }
        return RULES.mapNotNull { rule ->
            val inScope = firsts.filter { rule.domain == null || it.first == rule.domain }
            val hits = inScope.filter { it.second in rule.actions }
            if (hits.size < THRESHOLD) return@mapNotNull null
            val actionsUsed = hits.map { it.second }.distinct().joinToString(", ") { ACTION_NAMES[it] ?: it.name }
            MisconceptionCard(
                key = rule.key,
                belief = rule.belief,
                correction = rule.correction,
                evidence = "${if (rule.domain == null) "완료한 인시던트" else "이 도메인 인시던트"} ${inScope.size}개 중 ${hits.size}번 첫 조치로 $actionsUsed",
                labSlug = (rule.domain ?: hits.groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key)?.let { labByDomain[it] },
                failureDomain = rule.domain ?: hits.first().first,
            )
        }
    }

    companion object {
        const val THRESHOLD = 3

        private val ACTION_NAMES = mapOf(
            A.INCREASE_DB_POOL to "DB Pool 증가", A.ADD_CONSUMERS to "컨슈머 증설", A.ADD_READ_REPLICA to "Read Replica 추가",
            A.ADD_DISPATCHER_WORKERS to "디스패처 증설", A.SHORTEN_HOLD_TIMEOUT to "홀드 타임아웃 단축",
            A.REDUCE_CHUNK_SIZE to "청크 크기 축소", A.SCALE_OUT_REPLICAS to "Pod 증설", A.CONTINUE_ROLLOUT to "배포 계속",
        )

        /** The per-domain ones are the L8 failure patterns' first "bad fix"; the last spans domains. */
        private val RULES = listOf(
            MisconceptionRule("pool-fixes-it", "coupon", setOf(A.INCREASE_DB_POOL), "DB 커넥션 풀을 늘리면 해결된다",
                "풀은 대기열일 뿐입니다. DB 자체의 쓰기 한계를 넘으면 풀이 클수록 경합만 커지고, 캐시 쪽 증상은 그대로 남습니다."),
            MisconceptionRule("more-consumers", "notification", setOf(A.ADD_CONSUMERS), "컨슈머를 늘리면 적체가 풀린다",
                "provider가 느린 동안 컨슈머 하나의 처리량은 그대로입니다. 늘린 만큼 느린 provider에 동시 호출만 늘고, 재시도가 유입을 부풀리는 한 적체는 줄지 않습니다."),
            MisconceptionRule("replica-for-stampede", "product-browsing", setOf(A.ADD_READ_REPLICA), "읽기가 몰리면 Replica를 붙이면 된다",
                "원인이 적중률 하락과 중복 조회라면 Replica도 같은 중복 쿼리를 받습니다. 동시 miss를 합치는 것이 먼저입니다."),
            MisconceptionRule("more-dispatchers", "payment", setOf(A.ADD_DISPATCHER_WORKERS), "적체가 쌓이면 처리기를 늘리면 된다",
                "외부 PG가 느릴 때 디스패처를 늘리면 PG 동시 호출이 늘어 지연이 더 길어지고, 멱등성 키가 없으면 재시도가 이중 결제를 만듭니다."),
            MisconceptionRule("shorter-holds", "reservation", setOf(A.SHORTEN_HOLD_TIMEOUT), "홀드를 짧게 하면 좌석 경합이 풀린다",
                "점유는 빨리 풀리지만 락 경합은 그대로이고, 결제 중인 정상 사용자의 홀드가 풀리는 새 문제가 생깁니다."),
            MisconceptionRule("smaller-chunks", "batch-settlement", setOf(A.REDUCE_CHUNK_SIZE), "청크를 작게 쪼개면 재처리가 해결된다",
                "재처리 범위는 줄지만 처음부터 다시 도는 구조와 중복 반영은 그대로입니다. 재개 지점과 멱등한 반영이 먼저입니다."),
            MisconceptionRule("more-pods", "autoscaling", setOf(A.SCALE_OUT_REPLICAS), "Pod를 늘리면 용량 문제가 해결된다",
                "새 Pod도 같은 리소스 제한으로 재시작하고 롤아웃에 휩쓸립니다. 수평 확장은 안정성 문제를 대신 풀어주지 않습니다."),
            MisconceptionRule("push-rollout", "deployment", setOf(A.CONTINUE_ROLLOUT), "배포를 빨리 끝내면 문제도 끝난다",
                "결함 있는 버전이라면 비율을 올릴수록 실패가 비례해 늘어납니다. 원인이 방금 나간 변경일 때 첫 수는 멈추거나 되돌리는 것입니다."),
            MisconceptionRule("capacity-first", null,
                setOf(A.INCREASE_DB_POOL, A.ADD_CONSUMERS, A.ADD_READ_REPLICA, A.ADD_DISPATCHER_WORKERS, A.SCALE_OUT_REPLICAS),
                "장애가 나면 일단 용량부터 늘린다",
                "용량 증설은 원인을 모를 때 가장 비싸고 가장 자주 빗나가는 첫 수입니다. 지표에서 병목이 어디인지(풀·캐시·외부·락) 먼저 확인하세요."),
        )
    }
}

@RestController
class MisconceptionController(private val service: MisconceptionService) {
    /** PLAN.md Round E29 (L10). */
    @GetMapping("/learning/misconceptions")
    fun mine(@AuthenticatedUserId userId: UUID): List<MisconceptionCard> = service.forUser(userId)
}
