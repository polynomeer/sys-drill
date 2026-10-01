---
status: accepted
---

# 포크(Counterfactual · Fork My Run)는 세션이 아니라 Redis 임시 상태다

리플레이의 임의 시점에서 "여기서 다르게 해보기"(내 세션 — [DRILLS_EXPANSION_PLAN.md](../DRILLS_EXPANSION_PLAN.md) M6)와 "여기서 내가 해보기"(남의 공개 풀이 — [COMMUNITY_EXPANSION_PLAN.md](../COMMUNITY_EXPANSION_PLAN.md) C9)를 제공한다.

**포크는 `sessions`·`applied_actions` 행을 만들지 않는다.** Redis `fork:{id}`(1시간 TTL)에 도메인, 원본의 기준 trait, 분기 시점까지의 액션 접두부, 포크 이후 액션만 저장하고, 상태는 규칙 기반 엔진으로 매번 재계산한다. 실제 인프라 세션은 포크할 수 없다(스냅샷 기반이라 재계산 불가 — [ADR-0016](0016-incident-replay-snapshots-only-for-real-infra.md)). 같은 결정의 일부로, 이미 있던 "완료 후 샌드박스" 액션이 원래 타임라인에 표시 없이 쌓이던 문제를 `parameters.sandbox=true` 표식과 MTTR·벤치마크·시계열 제외로 고친다.

**이유**: 대안은 포크마다 새 세션(또는 세션 복제)을 만드는 것이었다. 그러면 포크가 세션 상태 머신·리포트·평가 큐·Drill Score·인증·벤치마크·최근 완료자·알림의 모든 집계에 "이건 진짜 훈련이 아님"이라는 예외를 하나씩 추가해야 한다 — 채용 평가 세션 하나를 빼는 데 9개 기능을 손댄 선례([ADR-0043](0043-assessment-sessions-stay-out-of-community-and-public-aggregates.md))가 그 비용을 보여준다. 기존 샌드박스처럼 같은 세션에 이어 쌓는 방식은 원래 기록을 오염시킨다(실제로 MTTR이 오염되고 있었다). 포크는 "비교해 보고 버리는" 용도라 영속할 이유가 없다.

**대가**: 포크 결과는 1시간 뒤 사라지고 공유·재방문할 수 없다. 공유가 필요하면 결과 수치를 토론 글에 첨부하는 것까지만 지원한다. 포크를 저장해 달라는 수요가 확인되면 세션이 아닌 별도 테이블(포크 결과 요약)로 가야 하며, 그때도 이 ADR의 "포크는 훈련 기록이 아니다"는 유지한다.
