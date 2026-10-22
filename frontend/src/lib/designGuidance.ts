// Domain titles and the incident-response retro prompts, shared across pages.
// The per-domain design checklists live in the design guides (DB) — see
// lib/designChecklists.ts.

export const DOMAIN_TITLES: Record<string, string> = {
  coupon: "선착순 쿠폰",
  notification: "알림 이벤트 처리",
  "product-browsing": "대규모 상품 조회",
  payment: "주문/결제",
  reservation: "예약 시스템",
  "batch-settlement": "배치/정산",
  autoscaling: "실시간 추천 API",
  deployment: "카나리 배포",
};

export const INCIDENT_GUIDANCE = [
  "가장 먼저 확인한 지표와 그 이유",
  "원인 가설과 근거",
  "선택한 조치와 그 이유",
  "조치의 예상 부작용",
  "조치 이후 지표가 어떻게 바뀌었는지",
  "재발 방지를 위한 구조 개선 아이디어",
];
