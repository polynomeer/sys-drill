// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(3, 10.0);
  const results: boolean[] = [];
  for (let i = 0; i < 5; i++) results.push(await rl.allow("user-a"));
  const allowed = results.filter((r) => r).length;
  if (allowed !== 3) throw new Error(`expected exactly 3 allowed within the window, got ${allowed}`);
  if ((await rl.allow("user-b")) !== true) {
    throw new Error("a different key should not be affected by user-a's budget");
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
