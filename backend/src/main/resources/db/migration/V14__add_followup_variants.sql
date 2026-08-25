-- PLAN.md step 12: gives each scenario's FOLLOWUP (꼬리설계) step multiple
-- authored variants instead of one fixed prompt. SessionService picks one
-- adaptively (targeting the user's most frequent weakness) or, absent a
-- clear weakness signal, deterministically from the session's seed.
--
-- Each variant's targetRiskKey matches a RuleEvaluator riskKey for that
-- scenario's domain (see backend RuleEvaluator.kt) — null for the
-- catch-all "no specific weakness" variant.

update scenario_steps
set content = $$
{
  "variants": [
    {
      "key": "traffic-spike",
      "targetRiskKey": null,
      "prompt": "이벤트 오픈 직전, 예상 트래픽이 20배로 상향 조정되었고 Redis 확장 예산에 제한이 생겼습니다. 설계를 다시 검토하세요."
    },
    {
      "key": "duplicate-issue-spike",
      "targetRiskKey": "MISSING_CONCURRENCY_CONTROL",
      "prompt": "마케팅팀이 SNS에 쿠폰 링크를 공유하면서, 같은 사용자가 여러 탭/기기로 동시에 여러 번 요청을 보내는 경우가 급증했습니다. 재고 경합 상황에서 설계가 여전히 안전한지 다시 검토하세요."
    },
    {
      "key": "bot-traffic",
      "targetRiskKey": "MISSING_RATE_LIMIT",
      "prompt": "쿠폰 정보가 유출되며 매크로/봇 트래픽이 유입되고 있습니다. 정상 사용자와 봇 트래픽을 구분하지 않고 처리하면 시스템이 버티지 못할 수 있습니다. 설계를 다시 검토하세요."
    }
  ]
}
$$
where id = 'a0000000-0000-0000-0000-000000000005';

update scenario_steps
set content = $$
{
  "variants": [
    {
      "key": "volume-spike",
      "targetRiskKey": "MISSING_IDEMPOTENT_CONSUMER",
      "prompt": "주문량이 10배로 증가했습니다. 결제 완료 알림처럼 일부 메시지는 중복 발송이 절대 허용되지 않습니다. 설계를 다시 검토하세요."
    },
    {
      "key": "malformed-events",
      "targetRiskKey": "MISSING_DLQ",
      "prompt": "일부 주문 이벤트에 잘못된 형식의 데이터가 섞여 들어오기 시작했습니다. 이 이벤트들이 계속 재시도되며 정상 이벤트 처리를 방해하지 않도록 설계를 다시 검토하세요."
    },
    {
      "key": "provider-degradation",
      "targetRiskKey": "MISSING_CIRCUIT_BREAKER",
      "prompt": "SMS provider 중 하나가 간헐적으로 느려지기 시작했습니다. 다른 provider(이메일/푸시)까지 영향을 받지 않도록 설계를 다시 검토하세요."
    }
  ]
}
$$
where id = 'c0000000-0000-0000-0000-000000000005';

update scenario_steps
set content = $$
{
  "variants": [
    {
      "key": "traffic-spike",
      "targetRiskKey": "MISSING_CACHE_POLICY_SEPARATION",
      "prompt": "트래픽이 20배로 증가했습니다. 가격 정보는 항상 최신 상태여야 하지만, 리뷰는 약간 오래된 데이터를 보여줘도 괜찮습니다. 설계를 다시 검토하세요."
    },
    {
      "key": "flash-sale-hot-item",
      "targetRiskKey": "MISSING_KEY_DISTRIBUTION",
      "prompt": "특정 상품 하나가 실시간 방송에 소개되며 해당 상품에만 트래픽이 극단적으로 몰리고 있습니다. 다른 상품 조회는 평시 수준입니다. 설계를 다시 검토하세요."
    },
    {
      "key": "read-heavy-growth",
      "targetRiskKey": "MISSING_READ_REPLICA",
      "prompt": "신규 사용자 유입으로 읽기 트래픽이 꾸준히 증가하는 추세입니다. 쓰기 트래픽은 거의 그대로입니다. 설계를 다시 검토하세요."
    }
  ]
}
$$
where id = 'd0000000-0000-0000-0000-000000000005';
