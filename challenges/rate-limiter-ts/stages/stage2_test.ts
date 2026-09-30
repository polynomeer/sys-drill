// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
import { RateLimiter } from "./rate_limiter.ts";

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main(): Promise<void> {
  const rl = new RateLimiter(2, 0.5);
  if ((await rl.allow("k")) !== true) throw new Error("expected the first request to be allowed");
  if ((await rl.allow("k")) !== true) throw new Error("expected the second request to be allowed");
  if ((await rl.allow("k")) !== false) throw new Error("third request within the window should be rejected");
  await sleep(700);
  if ((await rl.allow("k")) !== true) throw new Error("after the window elapses, capacity should replenish");
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
