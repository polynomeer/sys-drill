-- Drill Map(docs/DRILLS_SIMULATION_VISION.md §6 우선순위 ④)의 첫 슬라이스 — 공식 7개
-- 시나리오가 전부 difficulty='MEDIUM'으로 동일해 실제 난이도 데이터가 없었다(2026-09-11
-- 보류 사유). 각 시나리오의 실제 메커닉(RuleEvaluator 체크 개수, real-infra 지원 여부,
-- 고정 FOLLOWUP vs 3-variant 적응형 FOLLOWUP, MVP 시기 vs 이후 추가 시기)을 근거로 실제
-- 난이도를 부여한다.
--
-- EASY   — coupon: 가장 먼저 만들어진 파일럿 시나리오, 단일 FOLLOWUP, real-infra 지원
--          (기준 시나리오 성격)
-- MEDIUM — notification/product-browsing: 여전히 MVP 시기 단일 FOLLOWUP이지만 RuleEvaluator
--          체크가 더 많음(notification=5개로 7개 도메인 중 최다)
-- HARD   — payment/reservation/batch-settlement/autoscaling: 전부 3-variant 적응형
--          FOLLOWUP으로 나중에 추가됐고, 결제/예약 정합성·배치 재처리 정합성·K8s 오토스케일링
--          등 더 까다로운 개념을 다룸

update content_items set difficulty = 'EASY' where id = 'a0000000-0000-0000-0000-000000000001'; -- 선착순 쿠폰

update content_items set difficulty = 'MEDIUM' where id in (
    'c0000000-0000-0000-0000-000000000001', -- 알림 이벤트 처리
    'd0000000-0000-0000-0000-000000000001'  -- 대규모 상품 조회
);

update content_items set difficulty = 'HARD' where id in (
    'b1000000-0000-0000-0000-000000000001', -- 주문/결제
    'b2000000-0000-0000-0000-000000000001', -- 예약 시스템
    'b3000000-0000-0000-0000-000000000001', -- 배치/정산
    'b4000000-0000-0000-0000-000000000001'  -- 실시간 추천 API
);
