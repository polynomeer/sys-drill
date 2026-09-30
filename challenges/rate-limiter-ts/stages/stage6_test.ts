// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(2, 5.0);
  await rl.allow("k");
  await rl.allow("k");
  await rl.allow("k");
  const m = rl.metrics;
  if (m.allowed !== 2) throw new Error(`expected 2 allowed, got ${m.allowed}`);
  if (m.rejected !== 1) throw new Error(`expected 1 rejected, got ${m.rejected}`);
  if (Math.abs(m.rejectRate - 1 / 3) >= 0.01) {
    throw new Error(`expected rejectRate ~0.333, got ${m.rejectRate}`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
