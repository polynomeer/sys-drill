-- Seeds the first MVP scenario from docs/PRD.md §8.1 ("선착순 쿠폰").
-- Fixed UUIDs keep the seed reproducible across environments/tests.

insert into content_items (id, type, title, difficulty, version) values
    ('a0000000-0000-0000-0000-000000000001', 'SCENARIO', '선착순 쿠폰', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'a0000000-0000-0000-0000-000000000002',
        'a0000000-0000-0000-0000-000000000001',
        'coupon',
        '{"functional": ["상품 조회", "쿠폰 발급"], "nonFunctional": {"targetUsers": 1000000, "totalCoupons": 10000, "duplicateIssueAllowed": false, "responseMode": "즉시 성공/실패"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'a0000000-0000-0000-0000-000000000003',
        'a0000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "이벤트 오픈 직전 예상 트래픽 20배 상향, Redis 확장 예산 제한"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "Redis latency 증가 → cache/lock 경로 지연 → DB write hotspot 및 timeout 증가"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'a0000000-0000-0000-0000-000000000004',
        'a0000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "100만 사용자를 대상으로 1만 장의 쿠폰을 선착순으로 발급하는 시스템을 설계하세요. 중복 발급은 불가하며, 사용자는 즉시 성공 또는 실패 응답을 받아야 합니다."}'
    ),
    (
        'a0000000-0000-0000-0000-000000000005',
        'a0000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        '{"prompt": "이벤트 오픈 직전, 예상 트래픽이 20배로 상향 조정되었고 Redis 확장 예산에 제한이 생겼습니다. 설계를 다시 검토하세요."}'
    ),
    (
        'a0000000-0000-0000-0000-000000000006',
        'a0000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "Redis latency가 급증하며 캐시/락 경로가 지연되고 있습니다. DB write hotspot과 timeout이 증가하는 중입니다. 대응하세요."}'
    );
