-- Seeds the seventh scenario (PLAN.md step 29, "규칙 기반 신규 도메인" —
-- 실제 Kubernetes 클러스터 없이 순수 수식 기반 SimulationEngine으로 구현).
-- Fixed UUIDs keep the seed reproducible across environments/tests. FOLLOWUP
-- content uses the {"variants": [...]} shape introduced in V14 (PLAN.md step 12).

insert into content_items (id, type, title, difficulty, version) values
    ('b4000000-0000-0000-0000-000000000001', 'SCENARIO', '실시간 추천 API', 'MEDIUM', 1);

insert into scenarios (id, content_id, domain, base_requirements, scoring_profile) values
    (
        'b4000000-0000-0000-0000-000000000002',
        'b4000000-0000-0000-0000-000000000001',
        'autoscaling',
        '{"functional": ["메인 화면 실시간 추천 노출", "Kubernetes 기반 운영"], "nonFunctional": {"trafficVariance": "시간대별로 크게 변동", "deploymentModel": "무중단 롤링 배포"}}',
        '{"rubricRef": "docs/PRD.md#10-평가-루브릭-100점"}'
    );

insert into scenario_versions (id, scenario_id, version_no, status, followup_rules, incident_rules) values
    (
        'b4000000-0000-0000-0000-000000000003',
        'b4000000-0000-0000-0000-000000000002',
        1,
        'PUBLISHED',
        '{"trigger": "초기 설계 제출 완료", "change": "프로모션마다 트래픽이 반복적으로 튀는 것이 확인됨"}',
        '{"trigger": "꼬리설계 제출 완료", "event": "트래픽 10배 폭증과 롤링 배포가 겹침 → 일부 Pod가 OOM kill로 재시작 반복"}'
    );

insert into scenario_steps (id, scenario_version_id, step_order, step_type, trigger_condition, content) values
    (
        'b4000000-0000-0000-0000-000000000004',
        'b4000000-0000-0000-0000-000000000003',
        1,
        'INITIAL',
        null,
        '{"prompt": "메인 화면에 노출되는 실시간 추천 API를 설계하세요. Kubernetes 위에서 여러 Pod로 운영되며, 트래픽이 시간대별로 크게 달라집니다."}'
    ),
    (
        'b4000000-0000-0000-0000-000000000005',
        'b4000000-0000-0000-0000-000000000003',
        2,
        'FOLLOWUP',
        '{"afterStepOrder": 1}',
        $$
{
  "variants": [
    {
      "key": "confirmed-scale",
      "targetRiskKey": null,
      "prompt": "이 서비스는 프로모션마다 반복적으로 트래픽이 튈 것으로 예상됩니다. 이 규모에서도 설계가 여전히 안전한지 다시 검토하세요."
    },
    {
      "key": "resource-limit-incident",
      "targetRiskKey": "MISSING_RESOURCE_LIMITS",
      "prompt": "Pod 하나가 메모리를 과도하게 사용해 노드 전체에 영향을 준 적이 있습니다. 개별 Pod의 자원 사용량을 어떻게 제한하는지 설계를 다시 검토하세요."
    },
    {
      "key": "rollout-safety-incident",
      "targetRiskKey": "MISSING_ROLLOUT_SAFETY",
      "prompt": "배포 도중 일시적으로 API 응답이 느려진 적이 있습니다. 무중단 배포를 위해 무엇을 준비해야 하는지 설계를 다시 검토하세요."
    }
  ]
}
$$
    ),
    (
        'b4000000-0000-0000-0000-000000000006',
        'b4000000-0000-0000-0000-000000000003',
        3,
        'INCIDENT',
        '{"afterStepOrder": 2}',
        '{"prompt": "실시간 추천 API 트래픽이 10배로 폭증했습니다. 하필 새 버전 롤링 배포가 진행 중이며, 일부 Pod는 메모리 제한 설정 오류로 재시작을 반복하고 있습니다. 대응하세요."}'
    );
