-- Seeds the sixth scenario from docs/ROADMAP.md Phase 2 ("배치/정산"),
-- docs/PRD.md §8 표 기준. Fixed UUIDs keep the seed reproducible across
-- environments/tests. FOLLOWUP content uses the {"variants": [...]} shape
-- introduced in V14 (PLAN.md step 12).

insert into content_items (id, type, title, difficulty, version) values
    ('b3000000-0000-0000-0000-000000000001', 'SCENARIO', '배치/정산', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'b3000000-0000-0000-0000-000000000002',
        'b3000000-0000-0000-0000-000000000001',
        'batch-settlement',
        '{"functional": ["대용량 레코드 정산", "실패 시 재처리"], "nonFunctional": {"totalRecords": 1000000, "duplicateSettlementAllowed": false, "consistencyMode": "재처리해도 중복 반영되지 않아야 함"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'b3000000-0000-0000-0000-000000000003',
        'b3000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "정산 대상 레코드가 100만 건으로 확정"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "정산 API 응답 지연 급증 → 처리 중이던 청크 실패 → 재처리 범위와 중복 반영 위험"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'b3000000-0000-0000-0000-000000000004',
        'b3000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "매일 새벽 100만 건의 거래 내역을 정산하는 배치 시스템을 설계하세요. 정산 도중 실패가 발생할 수 있으며, 실패 후 재처리 시에도 정산 결과가 중복 반영되면 안 됩니다."}'
    ),
    (
        'b3000000-0000-0000-0000-000000000005',
        'b3000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        $$
{
  "variants": [
    {
      "key": "confirmed-scale",
      "targetRiskKey": null,
      "prompt": "정산 대상 레코드가 100만 건으로 확정되었습니다. 이 규모에서도 설계가 여전히 안전한지 다시 검토하세요."
    },
    {
      "key": "mid-run-failure",
      "targetRiskKey": "MISSING_RESTARTABILITY",
      "prompt": "배치가 60% 정도 진행된 시점에 서버가 재시작되는 경우가 있습니다. 이 경우 처음부터 다시 실행해야 하는지, 아니면 중단된 지점부터 이어서 재개할 수 있는지 설계를 다시 검토하세요."
    },
    {
      "key": "reprocess-duplicate-risk",
      "targetRiskKey": "MISSING_RECONCILIATION",
      "prompt": "실패한 구간을 재처리할 때, 이미 정산이 반영된 레코드가 다시 처리되어 금액이 중복 반영될 수 있는지 설계를 다시 검토하세요."
    }
  ]
}
$$
    ),
    (
        'b3000000-0000-0000-0000-000000000006',
        'b3000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "정산 API 응답이 급격히 느려지며 처리 중이던 청크가 실패하기 시작했습니다. 재처리 범위가 계속 커지고 있고, 일부 레코드는 중복 반영될 위험도 있습니다. 대응하세요."}'
    );
