-- docs/LEARNING_EXPANSION_PLAN.md L5 후속 — 8번째 도메인(deployment, ADR-0049)의 엔진 랩.
-- 랩은 한 시점의 상태라 손잡이는 시작 비율·진행 횟수·롤백. 시간에 따른 확산은 Drill에서.
insert into learning_labs (slug, kind, risk_key, domain, title, summary, spec, display_order) values
('engine-canary-rollout', 'ENGINE', 'MISSING_CANARY_ANALYSIS', 'deployment', '카나리는 어디까지 — 결함 있는 배포',
 '새 버전에 결함이 있을 때, 카나리 비율을 한 단계 올리면 실패는 얼마나 늘까요? 롤백하면 어디로 돌아갈까요?',
 '{
   "knobs": [
     {"trait": "canaryStartPercent", "label": "카나리 시작 비율 (%)", "type": "number", "min": 1, "max": 100, "step": 1},
     {"trait": "rolloutPromotions", "label": "진행 단계 (시작 이후 몇 번 늘렸나)", "type": "number", "min": 0, "max": 5, "step": 1},
     {"trait": "rolledBack", "label": "롤백", "type": "boolean"}
   ],
   "watch": ["errorRate", "availability", "p95LatencyMs"],
   "predict": ["errorRate"]
 }', 18);
