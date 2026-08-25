-- Seeds the third MVP scenario from docs/PRD.md §8.3 ("대규모 상품 조회").
-- Fixed UUIDs keep the seed reproducible across environments/tests.

insert into content_items (id, type, title, difficulty, version) values
    ('d0000000-0000-0000-0000-000000000001', 'SCENARIO', '대규모 상품 조회', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'd0000000-0000-0000-0000-000000000002',
        'd0000000-0000-0000-0000-000000000001',
        'product-browsing',
        '{"functional": ["상품 상세 조회 (가격/재고/리뷰)"], "nonFunctional": {"baselineRps": 500, "priceFreshnessRequired": true, "reviewStaleAllowed": true}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'd0000000-0000-0000-0000-000000000003',
        'd0000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "트래픽 20배 증가. 가격은 최신성이 필수이고 리뷰는 다소 stale해도 무방함"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "특정 인기 상품(hot key)에 트래픽이 몰리며 cache miss 폭증 → DB read latency 급증"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'd0000000-0000-0000-0000-000000000004',
        'd0000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "상품 상세 페이지에서 가격·재고·리뷰를 함께 보여주는 조회 시스템을 설계하세요. 초당 500건 수준의 평시 트래픽을 가정합니다."}'
    ),
    (
        'd0000000-0000-0000-0000-000000000005',
        'd0000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        '{"prompt": "트래픽이 20배로 증가했습니다. 가격 정보는 항상 최신 상태여야 하지만, 리뷰는 약간 오래된 데이터를 보여줘도 괜찮습니다. 설계를 다시 검토하세요."}'
    ),
    (
        'd0000000-0000-0000-0000-000000000006',
        'd0000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "특정 인기 상품(hot key)에 요청이 집중되며 캐시 miss가 폭증하고 있습니다. 동시에 발생한 miss들이 각자 DB를 두드리며 DB read latency가 급증하는 중입니다. 대응하세요."}'
    );
