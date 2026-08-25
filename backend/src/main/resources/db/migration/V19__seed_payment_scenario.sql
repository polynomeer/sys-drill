-- Seeds the fourth scenario from docs/ROADMAP.md Phase 2 ("주문/결제"),
-- docs/PRD.md §8 표 기준. Fixed UUIDs keep the seed reproducible across
-- environments/tests. FOLLOWUP content uses the {"variants": [...]} shape
-- introduced in V14 (PLAN.md step 12) — same adaptive/seed-based selection
-- as the three MVP scenarios.

insert into content_items (id, type, title, difficulty, version) values
    ('b1000000-0000-0000-0000-000000000001', 'SCENARIO', '주문/결제', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'b1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000001',
        'payment',
        '{"functional": ["주문 생성", "결제 처리"], "nonFunctional": {"baselineOrderRps": 30, "duplicateChargeAllowed": false, "consistencyMode": "최종 일관성 허용, 결제 금액은 반드시 정확해야 함"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'b1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "PG 응답 지연 발생, 재시도 시 이중 결제 위험"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "PG 타임아웃 급증 → outbox 재시도 폭증 → 주문 처리 지연 전이"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'b1000000-0000-0000-0000-000000000004',
        'b1000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "사용자가 상품을 주문하고 결제를 진행하는 시스템을 설계하세요. 주문 생성과 외부 결제대행사(PG) 연동이 필요하며, 결제 실패 시에도 주문과 결제 상태가 항상 일관되어야 합니다."}'
    ),
    (
        'b1000000-0000-0000-0000-000000000005',
        'b1000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        $$
{
  "variants": [
    {
      "key": "pg-latency-spike",
      "targetRiskKey": null,
      "prompt": "결제대행사(PG) 응답이 간헐적으로 3초 이상 걸리기 시작했습니다. 이 지연이 주문 처리 전체에 어떤 영향을 미치는지 설계를 다시 검토하세요."
    },
    {
      "key": "lost-response",
      "targetRiskKey": "MISSING_PAYMENT_IDEMPOTENCY",
      "prompt": "네트워크 문제로 결제 응답이 유실되는 경우가 발생하고 있습니다. 클라이언트가 같은 결제 요청을 재전송할 때 이중 결제가 일어나지 않는지 설계를 다시 검토하세요."
    },
    {
      "key": "no-shared-transaction",
      "targetRiskKey": "MISSING_TRANSACTION_BOUNDARY",
      "prompt": "주문 생성 DB 트랜잭션과 PG 호출을 하나의 트랜잭션으로 묶을 수 없다는 제약이 새로 추가되었습니다(PG는 외부 HTTP 호출이라 롤백이 불가능합니다). 설계를 다시 검토하세요."
    }
  ]
}
$$
    ),
    (
        'b1000000-0000-0000-0000-000000000006',
        'b1000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "결제대행사(PG) 응답이 급격히 느려지며 타임아웃이 급증하고 있습니다. 재시도가 쌓이며 outbox 처리 지연이 계속 길어지고, 그 여파가 주문 처리 자체에도 번지고 있습니다. 대응하세요."}'
    );
