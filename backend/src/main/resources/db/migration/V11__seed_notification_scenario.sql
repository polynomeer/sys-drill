-- Seeds the second MVP scenario from docs/PRD.md §8.2 ("알림 이벤트 처리").
-- Fixed UUIDs keep the seed reproducible across environments/tests.

insert into content_items (id, type, title, difficulty, version) values
    ('c0000000-0000-0000-0000-000000000001', 'SCENARIO', '알림 이벤트 처리', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'c0000000-0000-0000-0000-000000000002',
        'c0000000-0000-0000-0000-000000000001',
        'notification',
        '{"functional": ["주문 이벤트 수신", "이메일/푸시/SMS 발송"], "nonFunctional": {"eventsPerSecond": 50, "duplicateDeliveryAllowed": false, "deliveryMode": "비동기"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'c0000000-0000-0000-0000-000000000003',
        'c0000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "주문량 10배 증가, 일부 메시지(결제 완료 등)는 중복 발송 불가"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "provider(SMS/이메일) timeout 발생 → 재시도 폭증 → Kafka consumer lag 증가"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'c0000000-0000-0000-0000-000000000004',
        'c0000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "주문·결제·배송 이벤트가 발생할 때마다 이메일/푸시/SMS로 사용자에게 알림을 전달하는 시스템을 설계하세요. 이벤트 발행과 알림 전송은 응답 시간이 달라도 되지만, 알림은 최종적으로 반드시 전달되어야 합니다."}'
    ),
    (
        'c0000000-0000-0000-0000-000000000005',
        'c0000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        '{"prompt": "주문량이 10배로 증가했습니다. 결제 완료 알림처럼 일부 메시지는 중복 발송이 절대 허용되지 않습니다. 설계를 다시 검토하세요."}'
    ),
    (
        'c0000000-0000-0000-0000-000000000006',
        'c0000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "SMS/이메일 provider 응답이 급격히 느려지고 있습니다. 컨슈머가 provider 호출에 묶여 처리량이 떨어지고, 재시도가 몰리며 큐 backlog(consumer lag)가 계속 쌓이는 중입니다. 대응하세요."}'
    );
