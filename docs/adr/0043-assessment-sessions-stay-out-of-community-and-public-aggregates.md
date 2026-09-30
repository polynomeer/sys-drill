---
status: accepted
---

# 채용 평가 세션은 커뮤니티·공개 집계에서 제외한다

조직 채용 평가([ADR-0033](0033-assessment-reuses-invitation-shape-but-preview-stays-public-and-status-is-derived.md))는 공개 시나리오 위에서 도는 **평범한 세션**이고, `organization_assessments.result_session_id`로만 구분된다. 그래서 이후에 생긴 커뮤니티 기능(풀이 공유 [ADR-0041](0041-shared-writeups-are-visible-only-to-users-who-completed-that-scenario.md), Drill Score·랭킹 [ADR-0042](0042-drill-score-and-ranking-reward-breadth-and-difficulty-not-repetition.md), 벤치마크, 시나리오 통계)과 공개 인증([ADR-0032](0032-certification-is-live-computed-and-scoped-to-official-domains.md))이 평가 세션을 일반 훈련과 똑같이 셌고, 풀이 공유는 평가 답안을 **공개 콘텐츠로 만들 수 있었다**.

**평가 세션은 공개 콘텐츠가 될 수 없고(공개 전환 409, 목록·상세·토론 인용에서도 제외), Drill Score·랭킹·인증·벤치마크 모집단·시나리오 통계·최근 완료자·활동 타임라인 어디에도 반영하지 않는다.** 판별은 `AssessmentSessions` 한 곳에 모으고, 집계 JPQL은 DB에서 `not exists`로 거른다. 같은 변경에서 조직·비공개 시나리오 세션의 풀이 공개도 막았다(LEARNING_COMMUNITY_PLAN §7이 이미 요구했지만 구현에 빠져 있었다).

**이유**: 평가는 조직이 후보자를 판단하려고 만든 결과물이다 — 답안과 점수의 1차 소비자는 조직이고, 시간 제한·이해관계라는 다른 조건에서 나온 점수다. 후보자 본인의 기록으로 세면 (1) 채용 과정의 답안이 공개될 수 있고, (2) 평가 직후 공개 인증·랭킹이 바뀌는 식으로 응시 사실이 간접적으로 드러나며, (3) 조건이 다른 점수가 벤치마크 분포를 흐린다. 대안이었던 "본인 기록이니 센다"는 후보자에게 유리하지만 위 세 가지를 막을 방법이 없다.

**예외 — 풀이 열람 자격은 그대로 인정한다**: ADR-0041의 "그 시나리오를 완료했는가"에는 평가 세션도 포함된다. 남의 풀이를 **읽는** 권한일 뿐 아무것도 노출하지 않고, 실제로 그 문제와 씨름한 것은 사실이기 때문이다.

**되돌리기 비용**: 제외를 풀면 기존 후보자들의 점수·인증·랭킹이 일제히 바뀐다. 조건부(예: 후보자가 명시적으로 동의한 평가만 포함)로 바꾸려면 동의 기록 컬럼과 조직 측 설정이 필요하다.
