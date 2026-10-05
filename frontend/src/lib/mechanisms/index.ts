/**
 * docs/LEARNING_DEEPENING_PLAN.md L17 (ADR-0055) — mechanism widgets: in-browser simulations grounded
 * in a Build challenge's stage values. One pilot so far; more follow once it proves useful.
 */
export interface Mechanism {
  slug: string;
  title: string;
  summary: string;
  /** The concept page that links here. */
  riskKey: string;
  /** The Build challenge family whose stages the scenes replay. */
  challenge: string;
}

export const MECHANISMS: Mechanism[] = [
  {
    slug: "rate-limiter",
    title: "Rate Limiter 메커니즘",
    summary: "고정 윈도우·슬라이딩 로그·토큰 버킷이 같은 요청을 어떻게 다루는지, Build 과제 6단계의 장면을 그대로 재생합니다.",
    riskKey: "MISSING_RATE_LIMIT",
    challenge: "rate-limiter",
  },
];

export function mechanismsFor(riskKey: string): Mechanism[] {
  return MECHANISMS.filter((m) => m.riskKey === riskKey);
}
