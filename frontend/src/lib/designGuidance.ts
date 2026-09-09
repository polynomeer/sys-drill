// Shared by design/[sessionId]/page.tsx (in-session guidance) and
// app/learning/page.tsx (static reference content) — one source of truth so
// the two never drift apart.

export const DOMAIN_TITLES: Record<string, string> = {
  coupon: "선착순 쿠폰",
  notification: "알림 이벤트 처리",
  "product-browsing": "대규모 상품 조회",
  payment: "주문/결제",
  reservation: "예약 시스템",
  "batch-settlement": "배치/정산",
  autoscaling: "실시간 추천 API",
};

export const DESIGN_GUIDANCE_BY_DOMAIN: Record<string, string[]> = {
  coupon: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "고수준 아키텍처와 요청 흐름",
    "저장소 선택과 읽기/쓰기 패턴",
    "동시성·멱등성 처리 (중복 발급 방지)",
    "캐시/락 전략과 실패 시 대응",
    "Rate limit 등 트래픽 보호 전략",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  notification: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "비동기 처리 경계 (이벤트 발행 vs 알림 전송)",
    "idempotent consumer — 재시도로 인한 중복 발송 방지",
    "retry/backoff 전략",
    "DLQ(dead letter queue) — poison message 격리",
    "provider별 circuit breaker",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  "product-browsing": [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "고수준 아키텍처와 요청 흐름",
    "데이터별 캐시 정책 분리 (가격 vs 리뷰)",
    "hot key 분산 전략",
    "single-flight/lock — 동시 cache miss 중복 요청 방지",
    "read replica를 통한 읽기 확장",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  payment: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "트랜잭션 경계 — DB 트랜잭션과 외부 PG 호출을 어떻게 분리했는지 (outbox/saga)",
    "결제 멱등성 — 재시도 시 이중 결제 방지",
    "PG 장애 시 retry/backoff 전략과 partial failure 대응",
    "결제 상태와 주문 상태의 일관성 보장 방법",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  reservation: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "동시 예약 요청에 대한 락 전략 (세분화 수준 포함)",
    "재고 정합성 — 예약 가능 수량 확인과 확정의 원자성",
    "예약 홀드(hold) 타임아웃과 자동 해제",
    "중복 예약(overbooking) 방지 방법",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  "batch-settlement": [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "청킹(chunking) — 대용량 레코드를 어떤 단위로 나누어 처리하는지",
    "재시작성(restartability) — 중간 실패 시 처음부터 재실행 vs 체크포인트 재개",
    "정산 정합성(reconciliation) — 재처리 시 중복 반영 방지",
    "장시간 실행(long transaction)에 대한 대응",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  autoscaling: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "고수준 아키텍처와 요청 흐름",
    "Pod 오토스케일링(HPA) 전략",
    "리소스 request/limit 설정 기준",
    "무중단 배포(readiness probe/PodDisruptionBudget) 전략",
    "트래픽 급증 대응 전략",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
};

export const INCIDENT_GUIDANCE = [
  "가장 먼저 확인한 지표와 그 이유",
  "원인 가설과 근거",
  "선택한 조치와 그 이유",
  "조치의 예상 부작용",
  "조치 이후 지표가 어떻게 바뀌었는지",
  "재발 방지를 위한 구조 개선 아이디어",
];
