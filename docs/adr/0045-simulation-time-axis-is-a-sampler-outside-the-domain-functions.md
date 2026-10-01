---
status: accepted
---

# 시뮬레이션 시간축은 도메인 함수 밖의 샘플러가 파생한다

지금까지 규칙 기반 엔진에는 시계가 없었다 — `SystemState`는 액션을 적용할 때만 바뀌고, 차트는 프론트가 3초 폴링값을 40개 쌓아 그렸다. 그래서 알림의 "N분 지속", 에러 버짓, 적체가 빠지는 시간, 탐지 지연처럼 시간이 들어간 개념을 표현할 수 없었다([OBSERVABILITY_UI_PLAN.md](../OBSERVABILITY_UI_PLAN.md) O0-a).

**7개 도메인 함수(`RuleBasedSimulationEngine`)는 그대로 두고, 엔진 밖의 순수 함수 `TelemetrySampler`가 `(기준 trait, 인시던트 시작·종료 시각, 시각이 붙은 액션 이력, 샘플 시각)`으로 시계열을 계산한다.** 시간 효과는 두 가지뿐이다 — 인시던트 시작 후 90초 램프업(같은 trait의 인시던트 전/후 정상 상태를 필드별 선형 보간)과, 적체가 "초당 초과분"인 도메인(notification · payment · reservation)의 `queueLag` 적분. 시계열은 저장하지 않고 요청마다 계산한다([ADR-0011](0011-derived-values-are-never-persisted.md)). 실제 인프라 세션은 저장된 스냅샷을 계단형으로 돌려준다([ADR-0016](0016-incident-replay-snapshots-only-for-real-infra.md)).

**이유**: 대안은 (1) 각 도메인 함수가 `elapsed`를 받아 스스로 시간 반응을 정의하거나, (2) 서버에 틱 워커를 두고 상태를 주기적으로 저장하는 것이었다. (1)은 도메인마다 시간 수식이 생겨 `SimulationEngineTest`의 손계산 단언 관행([ADR-0010](0010-simulation-engine-per-domain-functions.md))이 시간 차원으로 폭발하고, 7개 메커니즘의 차이(ADR-0012)와 무관한 "시간이 흐르는 방식"까지 도메인마다 달라진다. (2)는 결정론을 잃어 리플레이·포크([ADR-0046](0046-forks-are-ephemeral-redis-state-not-sessions.md))가 같은 결과를 재현하지 못하고 세션마다 워커 비용이 생긴다. 샘플러는 기존 수식과 테스트를 한 줄도 바꾸지 않으면서 결정론을 지킨다.

**대가**: 시간 반응이 일반적인 두 효과로 제한된다 — "TTL이 짧으면 몇 초 뒤 캐시가 비어 DB가 무너진다" 같은 도메인 고유의 시간 동역학은 표현하지 못한다. 그런 동역학이 필요해지면 그 도메인만 샘플러에 훅을 추가하는 방향으로 넓히고, 도메인 함수 시그니처는 건드리지 않는다.
