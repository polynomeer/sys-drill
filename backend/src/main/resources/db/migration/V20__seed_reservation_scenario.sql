-- Seeds the fifth scenario from docs/ROADMAP.md Phase 2 ("예약 시스템"),
-- docs/PRD.md §8 표 기준. Fixed UUIDs keep the seed reproducible across
-- environments/tests. FOLLOWUP content uses the {"variants": [...]} shape
-- introduced in V14 (PLAN.md step 12).

insert into content_items (id, type, title, difficulty, version) values
    ('b2000000-0000-0000-0000-000000000001', 'SCENARIO', '예약 시스템', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'b2000000-0000-0000-0000-000000000002',
        'b2000000-0000-0000-0000-000000000001',
        'reservation',
        '{"functional": ["좌석 조회", "좌석 예약"], "nonFunctional": {"baselineReservationRps": 20, "duplicateReservationAllowed": false, "consistencyMode": "강한 일관성 필요 (중복 예약 불가)"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'b2000000-0000-0000-0000-000000000003',
        'b2000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "예약 오픈 직후 동시 접속자 15배 급증"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "인기 좌석에 락 경합 급증 → 락 대기 시간 증가 → 타임아웃 및 중복 예약 위험"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'b2000000-0000-0000-0000-000000000004',
        'b2000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "인기 공연의 좌석을 예약하는 시스템을 설계하세요. 같은 좌석을 여러 사용자가 동시에 예약 시도할 수 있으며, 중복 예약은 절대 허용되지 않습니다."}'
    ),
    (
        'b2000000-0000-0000-0000-000000000005',
        'b2000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        $$
{
  "variants": [
    {
      "key": "concurrent-surge",
      "targetRiskKey": null,
      "prompt": "예약 오픈 직후 동시 접속자가 15배로 급증했습니다. 이 상황에서 설계가 여전히 안전한지 다시 검토하세요."
    },
    {
      "key": "abandoned-holds",
      "targetRiskKey": "MISSING_RESERVATION_TIMEOUT",
      "prompt": "일부 사용자가 좌석을 선택만 하고 결제를 완료하지 않은 채 이탈하는 경우가 많습니다. 이런 미완료 예약이 좌석을 계속 점유하지 않는지 설계를 다시 검토하세요."
    },
    {
      "key": "split-check-and-reserve",
      "targetRiskKey": "MISSING_INVENTORY_CONSISTENCY",
      "prompt": "예약 가능 수량 확인 API와 실제 예약 확정 API가 분리되어 있다는 제약이 추가되었습니다. 그 사이에 같은 좌석이 중복 예약될 수 있는지 설계를 다시 검토하세요."
    }
  ]
}
$$
    ),
    (
        'b2000000-0000-0000-0000-000000000006',
        'b2000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "인기 좌석에 예약 시도가 몰리며 락 경합이 급격히 심해지고 있습니다. 락 대기 시간이 계속 길어지고, 일부 요청은 타임아웃되며 중복 예약 위험도 커지는 중입니다. 대응하세요."}'
    );
