---
status: accepted
---

# Learning 랩은 시뮬레이션 엔진을 세션 없이 호출하고 정답표를 저장하지 않는다

Learning에 값을 바꿔 현상을 관찰하는 인터랙티브 랩(Experiment · Break · Predict)을 추가한다([LEARNING_EXPANSION_PLAN.md](../LEARNING_EXPANSION_PLAN.md) L5).

**랩은 서버의 `RuleBasedSimulationEngine`을 세션 없이 직접 호출한다(`POST /learning/labs/{slug}/run`).** 랩 콘텐츠(`learning_labs`)는 "어느 trait을 어느 범위의 슬라이더로 줄지"만 정하고, 결과는 엔진이 낸다. 예측 문제("TTL을 줄이면 DB 부하는?")의 정답도 저장하지 않고 엔진 실행 결과의 변화량 부호로 판정한다. 엔진이 표현하지 못하는 현상은 랩으로 만들지 않으며, 랩을 위해 엔진 수식을 바꾸지 않는다 — 그래서 초안의 "Connection Pool은 클수록 좋은가" 랩은 뺐다(엔진에서 쓰기 용량이 pool 크기에 정비례).

**이유**: 대안은 프론트엔드 전용 경량 시뮬레이터였다 — 서버 왕복이 없고 랩마다 자유로운 수식을 쓸 수 있어 더 빠르고 표현력이 크다. 하지만 그러면 Learning에서 본 현상과 Drill 인시던트에서 겪는 현상이 서로 다른 수식에서 나오고, 어느 한쪽을 고칠 때 다른 쪽이 조용히 어긋난다. 1차 Learning 기획이 강점으로 꼽은 "학습 화면과 피드백 화면이 같은 어휘"([ADR-0039](0039-learning-concepts-live-in-the-database-not-frontend-constants.md))를 수식 수준까지 연장한 선택이다. 정답표를 저장하지 않는 것도 같은 이유 — 엔진이 바뀌면 손으로 적은 정답은 조용히 틀린다.

**대가**: 랩의 표현력이 7개 도메인 수식에 묶인다(Consistent Hashing, Raft 같은 시각 위젯은 이 경로로 만들 수 없다). 엔진이 바뀌면 랩의 관찰 결과도 함께 바뀌므로, 랩 설명 문구가 특정 수치를 단정하지 않게 써야 한다.
